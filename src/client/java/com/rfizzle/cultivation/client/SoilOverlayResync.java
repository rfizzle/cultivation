package com.rfizzle.cultivation.client;

import com.rfizzle.cultivation.Cultivation;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.config.SyncedConfig;
import com.rfizzle.cultivation.network.SoilOverlayRequestC2SPayload;
import com.rfizzle.cultivation.soil.OverlayRequestPacer;
import com.rfizzle.cultivation.soil.SoilOverlaySyncPolicy;
import com.rfizzle.cultivation.soil.SoilOverlaySyncPolicy.Action;
import com.rfizzle.cultivation.soil.SoilOverlaySyncPolicy.OverlayRules;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Owns the client's soil-overlay request traffic and keeps the overlay cache
 * honest ({@code design/SPEC.md} §1). Two jobs, one paced queue:
 *
 * <ul>
 *   <li><b>Chunk-load pull.</b> {@code CultivationClient}'s
 *       {@code ClientChunkEvents.CHUNK_LOAD} routes each chunk here via
 *       {@link #onChunkLoaded}; a join at a large view distance would otherwise
 *       fire thousands of requests at once and overflow the server's rate
 *       limiter, silently dropping half.
 *   <li><b>Mid-session re-pull.</b> A rule behind the overlays can move while
 *       chunks stay loaded — {@code showSoilOverlays} through the config screen,
 *       the server rules through {@code /cultivation reload} — leaving the cache
 *       blank where it should draw or drawing bands the server no longer
 *       produces. Rather than hook each mutation site, this watches the
 *       resulting state: a four-field snapshot compared once per client tick,
 *       which no path can slip past. {@link SoilOverlaySyncPolicy} decides what
 *       a change owes.
 * </ul>
 *
 * <p>Both sources feed one {@link OverlayRequestPacer}, drained at
 * {@link #REQUESTS_PER_TICK} chunks per tick. Sharing the queue is what keeps
 * their <em>combined</em> send rate under {@code SoilOverlayNetworking}'s token
 * bucket, so neither a join burst nor a rule-change sweep can overflow it.
 *
 * <p>Everything here runs on the client main thread, so it shares
 * {@link ClientSoilOverlayData}'s confinement and needs no synchronization
 * ({@code mc-shared-state}). The snapshot and queue reset on disconnect so one
 * server's rules never carry into the next ({@code mc-tick-work}).
 */
public final class SoilOverlayResync {
    /**
     * Comfortably under the server's 256/s bucket refill (8 × 20 ticks = 160/s).
     * This is the single budget both the chunk-load pull and the rule-change
     * sweep share, so the rate limiter never sees more than this from the mod no
     * matter how many chunks stream in at once. The margin below the refill
     * absorbs tick and clock jitter across a burst rather than running the bucket
     * at its ragged edge.
     */
    private static final int REQUESTS_PER_TICK = 8;

    private static OverlayRules lastRules;
    private static ResourceKey<Level> lastDimension;
    private static final OverlayRequestPacer PACER = new OverlayRequestPacer(REQUESTS_PER_TICK);

    private SoilOverlayResync() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SoilOverlayResync::onClientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    /**
     * Queues a chunk's overlay pull as it loads. Called from
     * {@code CultivationClient}'s {@code CHUNK_LOAD} handler in place of an inline
     * send, so the join burst is paced through the shared queue rather than fired
     * at the rate limiter all at once. Client main thread only.
     */
    public static void onChunkLoaded(int chunkX, int chunkZ) {
        PACER.enqueue(ChunkPos.asLong(chunkX, chunkZ));
    }

    private static void reset() {
        lastRules = null;
        lastDimension = null;
        PACER.clear();
    }

    private static void onClientTick(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null || client.player == null) {
            return;
        }
        try {
            // A portal swaps the ClientLevel without disconnecting, so requests queued
            // for the old dimension must be dropped. The first observation only records
            // the dimension: the chunk-load burst already sitting in the queue is ours
            // to send, not a stale cross-dimension sweep.
            ResourceKey<Level> dimension = level.dimension();
            if (lastDimension == null) {
                lastDimension = dimension;
            } else if (!dimension.equals(lastDimension)) {
                lastDimension = dimension;
                PACER.clear();
            }
            // The rule-change baseline needs the server's rules; until they arrive —
            // and on a server without the mod, never — skip the diff but keep draining,
            // so the chunk-load queue can't grow unbounded. Waiting for the rules also
            // avoids diffing against the local file's values and firing a spurious full
            // re-pull on join, on top of the chunk-load burst already filling the cache.
            CultivationConfig serverRules = SyncedConfig.serverConfig();
            if (serverRules != null) {
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
        // Drop any in-flight requests first — they were queued under the old rules.
        PACER.clear();
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
                        PACER.enqueue(ChunkPos.asLong(x, z));
                    }
                });
    }

    private static void drain(ClientLevel level) {
        if (PACER.isEmpty()) {
            return; // nothing to send this tick — skip building the drain callbacks
        }
        PACER.drain(
                chunkPos -> level.getChunkSource().hasChunk(
                        ChunkPos.getX(chunkPos), ChunkPos.getZ(chunkPos)),
                chunkPos -> ClientPlayNetworking.send(new SoilOverlayRequestC2SPayload(
                        ChunkPos.getX(chunkPos), ChunkPos.getZ(chunkPos))));
    }
}
