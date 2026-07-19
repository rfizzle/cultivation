package com.rfizzle.cultivation.client;

import com.rfizzle.cultivation.Cultivation;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.config.SyncedConfig;
import com.rfizzle.cultivation.network.SoilOverlayRequestC2SPayload;
import com.rfizzle.cultivation.soil.SoilOverlaySyncPolicy;
import com.rfizzle.cultivation.soil.SoilOverlaySyncPolicy.Action;
import com.rfizzle.cultivation.soil.SoilOverlaySyncPolicy.OverlayRules;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Keeps the client's soil overlay cache honest when the rules behind it move
 * mid-session ({@code design/SPEC.md} §1).
 *
 * <p>Overlays are pulled per chunk on chunk load, so a rule change while chunks
 * stay loaded leaves the cache stale — blank where it should draw when the player
 * turns {@code showSoilOverlays} on, or drawing bands the server no longer
 * produces after {@code /cultivation reload}. Rather than hook each mutation site
 * (the config screen's save runnable, the config-sync receiver, an integrated
 * server's reload), this watches the resulting state: a four-field snapshot
 * compared once per client tick, which no path can slip past. The comparison is
 * the whole standing cost; {@link SoilOverlaySyncPolicy} decides what a change owes.
 *
 * <p>A re-pull is paced at {@link #REQUESTS_PER_TICK} chunks per tick rather than
 * fired at once: {@code SoilOverlayNetworking}'s token bucket refills at 256/s, so
 * an unpaced sweep of a large view distance would silently drop requests and leave
 * the cache half-filled — the bug this class exists to prevent.
 *
 * <p>Everything here runs on the client main thread, so it shares
 * {@link ClientSoilOverlayData}'s confinement and needs no synchronization
 * ({@code mc-shared-state}). The snapshot and queue reset on disconnect so one
 * server's rules never carry into the next ({@code mc-tick-work}).
 */
public final class SoilOverlayResync {
    /**
     * Comfortably under the server's 256/s bucket refill (8 × 20 ticks = 160/s).
     * The headroom is deliberate: {@code ClientChunkEvents.CHUNK_LOAD} sends its own
     * unpaced request per chunk, so a sweep that ran right up to the refill rate
     * would start losing requests to the rate limiter whenever chunks are streaming
     * in at the same time — leaving exactly the blank patches this class prevents.
     */
    private static final int REQUESTS_PER_TICK = 8;

    private static OverlayRules lastRules;
    private static ResourceKey<Level> lastDimension;
    private static final LongArrayFIFOQueue PENDING = new LongArrayFIFOQueue();

    private SoilOverlayResync() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SoilOverlayResync::onClientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private static void reset() {
        lastRules = null;
        lastDimension = null;
        PENDING.clear();
    }

    private static void onClientTick(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null || client.player == null) {
            return;
        }
        // Wait for the server's rules before establishing a baseline: diffing against
        // the local file's values would fire a spurious full re-pull on join, on top
        // of the chunk-load burst that is already filling the cache.
        CultivationConfig serverRules = SyncedConfig.serverConfig();
        if (serverRules == null) {
            return;
        }
        try {
            // A portal swaps the ClientLevel without disconnecting, so a sweep in
            // flight is left holding the old dimension's chunk coordinates.
            ResourceKey<Level> dimension = level.dimension();
            if (!dimension.equals(lastDimension)) {
                lastDimension = dimension;
                PENDING.clear();
            }
            OverlayRules current = new OverlayRules(
                    CultivationConfig.get().showSoilOverlays,
                    serverRules.enableSoilFertility,
                    serverRules.tiredThreshold,
                    serverRules.enableNonFarmlandSoil);
            if (lastRules == null) {
                lastRules = current;
            } else if (!lastRules.equals(current)) {
                OverlayRules previous = lastRules;
                lastRules = current;
                apply(SoilOverlaySyncPolicy.decide(previous, current), client, level);
            }
            drain(level);
        } catch (Exception e) {
            Cultivation.LOGGER.error("Soil overlay resync failed", e);
            reset();
        }
    }

    private static void apply(Action action, Minecraft client, ClientLevel level) {
        if (action == Action.NONE) {
            return;
        }
        // Drop any in-flight sweep first — it was queued under the old rules.
        PENDING.clear();
        if (action == Action.CLEAR) {
            ClientSoilOverlayData.clear();
            return;
        }
        // REFETCH deliberately leaves the cache standing: acceptChunk replaces a
        // chunk wholesale (and drops it on an empty response), so each response
        // corrects its own chunk as it lands. Clearing up front would blank the
        // whole field for the length of a paced sweep instead.
        enqueueLoadedChunks(client, level);
    }

    /**
     * Queues every currently loaded chunk around the player, nearest first. Bounded
     * by the render distance, and naturally capped below it by whatever the server
     * actually sent.
     */
    private static void enqueueLoadedChunks(Minecraft client, ClientLevel level) {
        ChunkPos center = client.player.chunkPosition();
        SoilOverlaySyncPolicy.forEachChunkOutward(
                center.x, center.z, client.options.getEffectiveRenderDistance(),
                (x, z) -> {
                    if (level.getChunkSource().hasChunk(x, z)) {
                        PENDING.enqueue(ChunkPos.asLong(x, z));
                    }
                });
    }

    private static void drain(ClientLevel level) {
        for (int sent = 0; sent < REQUESTS_PER_TICK && !PENDING.isEmpty(); sent++) {
            long chunkPos = PENDING.dequeueLong();
            int x = ChunkPos.getX(chunkPos);
            int z = ChunkPos.getZ(chunkPos);
            if (!level.getChunkSource().hasChunk(x, z)) {
                continue; // unloaded while queued; a reload fires CHUNK_LOAD's own pull
            }
            ClientPlayNetworking.send(new SoilOverlayRequestC2SPayload(x, z));
        }
    }
}
