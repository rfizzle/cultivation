package com.rfizzle.cultivation.client;

import com.rfizzle.cultivation.network.SoilBandsS2CPayload;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The client's soil overlay cache: per chunk, the deviating positions and their
 * {@link com.rfizzle.cultivation.soil.SoilOverlayFlags flag bytes}, fed by the
 * pull response and single-position deltas ({@code design/SPEC.md} §1).
 *
 * <p>Every entry point runs on the client main thread — the network receivers hop
 * through {@code client.execute}, and chunk load/unload and the render pass are
 * already on it — so a plain map needs no synchronization ({@code mc-shared-state}:
 * confinement, not locking). The cache is cleared on disconnect and pruned on
 * chunk unload so it never grows unbounded across reconnects.
 */
public final class ClientSoilOverlayData {
    private static final Map<Long, Map<Integer, Byte>> CHUNKS = new HashMap<>();

    private ClientSoilOverlayData() {
    }

    /** Receives a full chunk response, replacing any prior cache for that chunk. */
    public static void acceptChunk(SoilBandsS2CPayload payload) {
        if (payload.entries().isEmpty()) {
            CHUNKS.remove(payload.chunkPos());
            return;
        }
        Map<Integer, Byte> positions = new HashMap<>(payload.entries().size());
        for (SoilBandsS2CPayload.Entry entry : payload.entries()) {
            positions.put(entry.packedPos(), entry.flags());
        }
        CHUNKS.put(payload.chunkPos(), positions);
    }

    /** Applies a single-position change; {@code present == false} drops the position. */
    public static void acceptDelta(long chunkPos, int packedPos, boolean present, byte flags) {
        if (present) {
            CHUNKS.computeIfAbsent(chunkPos, key -> new HashMap<>()).put(packedPos, flags);
            return;
        }
        Map<Integer, Byte> positions = CHUNKS.get(chunkPos);
        if (positions != null) {
            positions.remove(packedPos);
            if (positions.isEmpty()) {
                CHUNKS.remove(chunkPos);
            }
        }
    }

    /** Drops a chunk's overlays when it unloads client-side. */
    public static void removeChunk(long chunkPos) {
        CHUNKS.remove(chunkPos);
    }

    public static void clear() {
        CHUNKS.clear();
    }

    public static boolean isEmpty() {
        return CHUNKS.isEmpty();
    }

    /** Visits every cached position as {@code (chunkPos, packedPos, flags)}. */
    public static void forEach(OverlayConsumer consumer) {
        for (Map.Entry<Long, Map<Integer, Byte>> chunk : CHUNKS.entrySet()) {
            long chunkPos = chunk.getKey();
            for (Map.Entry<Integer, Byte> position : chunk.getValue().entrySet()) {
                consumer.accept(chunkPos, position.getKey(), position.getValue());
            }
        }
    }

    @FunctionalInterface
    public interface OverlayConsumer {
        void accept(long chunkPos, int packedPos, byte flags);
    }
}
