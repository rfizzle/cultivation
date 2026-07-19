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
import net.minecraft.world.level.ChunkPos;

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
     * Just under the server's 256/s bucket refill (12 × 20 ticks = 240/s), so a
     * resync of any size completes without a single request being rate-limited.
     */
    private static final int REQUESTS_PER_TICK = 12;

    private static OverlayRules lastRules;
    private static final LongArrayFIFOQueue PENDING = new LongArrayFIFOQueue();

    private SoilOverlayResync() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SoilOverlayResync::onClientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private static void reset() {
        lastRules = null;
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
        ClientSoilOverlayData.clear();
        if (action == Action.CLEAR_AND_REFETCH) {
            enqueueLoadedChunks(client, level);
        }
    }

    /**
     * Queues every currently loaded chunk around the player. Bounded by the render
     * distance, and naturally capped below it by whatever the server actually sent.
     */
    private static void enqueueLoadedChunks(Minecraft client, ClientLevel level) {
        ChunkPos center = client.player.chunkPosition();
        int radius = client.options.getEffectiveRenderDistance();
        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                if (level.getChunkSource().hasChunk(x, z)) {
                    PENDING.enqueue(ChunkPos.asLong(x, z));
                }
            }
        }
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
