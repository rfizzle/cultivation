package com.rfizzle.cultivation.network;

import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.soil.SoilOverlayMath;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers the soil-overlay payloads and the client's per-chunk request handler
 * ({@code design/SPEC.md} §1). The response is a pull: the client asks for one
 * chunk on load, the server answers with its deviating positions. Deltas are
 * pushed separately from {@link SoilOverlayServer}.
 *
 * <p>The C2S handler is the mod's only untrusted inbound surface, so it validates
 * server-side ({@code mc-networking}): soil must be enabled, and the requested
 * chunk must sit within the player's view distance. A per-player token bucket
 * absorbs the burst of requests a join or teleport fires (one per loaded chunk)
 * while capping sustained spam — each response is only a cheap in-memory scan, so
 * the bucket is generous.
 */
public final class SoilOverlayNetworking {
    /** Enough to serve a full view-distance reload burst in one refill window. */
    private static final double BUCKET_CAPACITY = 2048.0;
    private static final double REFILL_PER_SECOND = 256.0;

    private static final Map<UUID, Bucket> BUCKETS = new ConcurrentHashMap<>();

    private SoilOverlayNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(SoilBandsS2CPayload.TYPE, SoilBandsS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SoilBandDeltaS2CPayload.TYPE, SoilBandDeltaS2CPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SoilOverlayRequestC2SPayload.TYPE, SoilOverlayRequestC2SPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SoilOverlayRequestC2SPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    context.player().server.execute(() -> handleRequest(player, payload));
                });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                BUCKETS.remove(handler.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BUCKETS.clear());
    }

    private static void handleRequest(ServerPlayer player, SoilOverlayRequestC2SPayload payload) {
        if (!CultivationConfig.get().enableSoilFertility) {
            return;
        }
        ServerLevel level = player.serverLevel();
        ChunkPos requested = new ChunkPos(payload.chunkX(), payload.chunkZ());
        ChunkPos playerChunk = player.chunkPosition();
        int maxChunks = level.getServer().getPlayerList().getViewDistance() + 2;
        if (SoilOverlayMath.chunkChebyshevDistance(
                playerChunk.x, playerChunk.z, requested.x, requested.z) > maxChunks) {
            return; // out of the player's view — never a legitimate request
        }
        if (!consumeToken(player.getUUID())) {
            return; // rate limited
        }
        List<SoilBandsS2CPayload.Entry> entries = SoilOverlayServer.collectChunkEntries(level, requested);
        ServerPlayNetworking.send(player, new SoilBandsS2CPayload(requested.toLong(), entries));
    }

    private static boolean consumeToken(UUID player) {
        long now = System.currentTimeMillis();
        Bucket bucket = BUCKETS.computeIfAbsent(player, id -> new Bucket(BUCKET_CAPACITY, now));
        synchronized (bucket) {
            double refill = (now - bucket.lastMillis) / 1000.0 * REFILL_PER_SECOND;
            bucket.tokens = Math.min(BUCKET_CAPACITY, bucket.tokens + refill);
            bucket.lastMillis = now;
            if (bucket.tokens < 1.0) {
                return false;
            }
            bucket.tokens -= 1.0;
            return true;
        }
    }

    private static final class Bucket {
        private double tokens;
        private long lastMillis;

        private Bucket(double tokens, long lastMillis) {
            this.tokens = tokens;
            this.lastMillis = lastMillis;
        }
    }
}
