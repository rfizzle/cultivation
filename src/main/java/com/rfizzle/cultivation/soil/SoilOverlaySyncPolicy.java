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
        /** Drop the cache and re-pull every loaded chunk under the new rules. */
        CLEAR_AND_REFETCH
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
        return after.displaying() ? Action.CLEAR_AND_REFETCH : Action.CLEAR;
    }
}
