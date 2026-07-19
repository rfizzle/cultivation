package com.rfizzle.cultivation.soil;

/**
 * Decides what the client owes its soil overlay cache when the rules behind that
 * cache change mid-session ({@code design/SPEC.md} §1).
 *
 * <p>The cache is a projection of two independent inputs: the player's local
 * {@code showSoilOverlays} preference, and the server-authoritative rules that
 * decide which positions deviate at all ({@code enableSoilFertility}, plus the
 * {@code tiredThreshold} and {@code enableNonFarmlandSoil} inputs to {@link
 * SoilOverlayFlags#computeFlags}). Either can move while chunks stay loaded — the
 * preference through the config screen, the server rules through
 * {@code /cultivation reload} — and neither re-runs the per-chunk pull that
 * populated the cache on chunk load. Left alone, the cache goes stale: blank where
 * it should draw, or drawing bands the current rules no longer produce.
 *
 * <p>Pure by construction so the transition matrix tests at Tier 1; the client
 * shell owns the snapshotting, the network sends, and the cache itself.
 */
public final class SoilOverlaySyncPolicy {
    private SoilOverlaySyncPolicy() {
    }

    /**
     * Everything that decides whether the client's cached overlay set is still
     * the right one. Deliberately a record: equality is the staleness test.
     *
     * @param showOverlays     the player's local display preference
     * @param soilEnabled      server-side {@code enableSoilFertility}
     * @param tiredThreshold   server-side band cutoff — moves which positions deviate
     * @param nonFarmlandSoil  server-side {@code enableNonFarmlandSoil} — moves which grounds are tracked
     */
    public record OverlayRules(boolean showOverlays, boolean soilEnabled,
            double tiredThreshold, boolean nonFarmlandSoil) {

        /** Whether overlays should be on screen at all under these rules. */
        public boolean displaying() {
            return showOverlays && soilEnabled;
        }
    }

    /** What the client must do to its cache for a given rule transition. */
    public enum Action {
        /** Rules are unchanged — the cache still describes the world. */
        NONE,
        /** Drop the cache; nothing should draw, so nothing needs re-fetching. */
        CLEAR,
        /**
         * Re-pull every loaded chunk under the new rules, letting each response
         * replace its chunk in place. Deliberately not a clear-then-refill: the
         * sweep is paced over several ticks, and dropping the cache up front would
         * blank ground the player is looking at until its chunk's turn came round.
         */
        REFETCH
    }

    /**
     * The action owed for a move from {@code before} to {@code after}.
     *
     * <p>Any change while overlays should be displaying re-pulls, because a band
     * cutoff or ground-tracking change rewrites the flags of positions already
     * cached — not just which chunks are populated. A change that leaves overlays
     * hidden only clears: the pull would be answered with nothing (the server
     * short-circuits while soil is disabled) or discarded unread.
     */
    public static Action decide(OverlayRules before, OverlayRules after) {
        if (before.equals(after)) {
            return Action.NONE;
        }
        return after.displaying() ? Action.REFETCH : Action.CLEAR;
    }

    /**
     * Visits every chunk within {@code radius} of a centre, nearest first — ring by
     * ring outward in Chebyshev distance.
     *
     * <p>Order is the point. A refetch is paced over many ticks, and the renderer
     * culls to {@code soilOverlayRenderDistance} (blocks, not chunks), so only the
     * handful of chunks around the player can visibly change. A raster walk from a
     * corner spends its first several seconds on chunks whose responses the renderer
     * discards; walking outward puts what the player is looking at in the first tick
     * of the sweep.
     */
    public static void forEachChunkOutward(int centerX, int centerZ, int radius, ChunkVisitor visitor) {
        for (int ring = 0; ring <= radius; ring++) {
            for (int x = centerX - ring; x <= centerX + ring; x++) {
                for (int z = centerZ - ring; z <= centerZ + ring; z++) {
                    // Emit only this ring's perimeter; the interior went out on earlier rings.
                    if (Math.max(Math.abs(x - centerX), Math.abs(z - centerZ)) == ring) {
                        visitor.accept(x, z);
                    }
                }
            }
        }
    }

    @FunctionalInterface
    public interface ChunkVisitor {
        void accept(int chunkX, int chunkZ);
    }
}
