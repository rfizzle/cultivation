package com.rfizzle.cultivation.soil;

import com.rfizzle.cultivation.soil.SoilOverlaySyncPolicy.Action;
import com.rfizzle.cultivation.soil.SoilOverlaySyncPolicy.OverlayRules;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoilOverlaySyncPolicyTest {
    /** Overlays on and soil enabled — the state where quads are on screen. */
    private static final OverlayRules DISPLAYING = new OverlayRules(true, true, 25.0, true);

    @Test
    void unchangedRulesOweNothing() {
        assertEquals(Action.NONE, SoilOverlaySyncPolicy.decide(DISPLAYING, DISPLAYING));
        assertEquals(Action.NONE, SoilOverlaySyncPolicy.decide(
                new OverlayRules(false, true, 25.0, true),
                new OverlayRules(false, true, 25.0, true)));
    }

    @Test
    void turningTheDisplayPreferenceOnRefetchesLoadedChunks() {
        // The gap the issue names: chunks that loaded while the toggle was off were
        // never requested, so only a re-pull can fill them without a chunk reload.
        OverlayRules before = new OverlayRules(false, true, 25.0, true);
        assertEquals(Action.REFETCH, SoilOverlaySyncPolicy.decide(before, DISPLAYING));
    }

    @Test
    void turningTheDisplayPreferenceOffOnlyClears() {
        OverlayRules after = new OverlayRules(false, true, 25.0, true);
        assertEquals(Action.CLEAR, SoilOverlaySyncPolicy.decide(DISPLAYING, after));
    }

    @Test
    void disablingSoilClearsEvenWhileThePreferenceStaysOn() {
        // SPEC §1: enableSoilFertility=false means no overlays. The preference being
        // on must not keep a stale cache alive on screen.
        OverlayRules after = new OverlayRules(true, false, 25.0, true);
        assertEquals(Action.CLEAR, SoilOverlaySyncPolicy.decide(DISPLAYING, after));
    }

    @Test
    void reEnablingSoilRefetches() {
        OverlayRules before = new OverlayRules(true, false, 25.0, true);
        assertEquals(Action.REFETCH, SoilOverlaySyncPolicy.decide(before, DISPLAYING));
    }

    @Test
    void bandCutoffChangeRefetchesBecauseCachedFlagsAreNowWrong() {
        // tiredThreshold feeds SoilOverlayFlags.computeFlags, so moving it rewrites
        // the band of positions already cached — clearing alone would leave them blank.
        OverlayRules after = new OverlayRules(true, true, 40.0, true);
        assertEquals(Action.REFETCH, SoilOverlaySyncPolicy.decide(DISPLAYING, after));
    }

    @Test
    void groundTrackingChangeRefetches() {
        OverlayRules after = new OverlayRules(true, true, 25.0, false);
        assertEquals(Action.REFETCH, SoilOverlaySyncPolicy.decide(DISPLAYING, after));
    }

    @Test
    void serverRuleChangesWhileHiddenOnlyClear() {
        // No point re-pulling what nothing will draw; the next toggle-on refetches.
        OverlayRules before = new OverlayRules(false, true, 25.0, true);
        OverlayRules after = new OverlayRules(false, true, 40.0, false);
        assertEquals(Action.CLEAR, SoilOverlaySyncPolicy.decide(before, after));
    }

    @Test
    void outwardWalkVisitsTheCentreFirst() {
        // The regression this guards: a corner-first raster walk re-pulled the player's
        // own chunks last, so a paced sweep left the ground the player was looking at
        // unchanged for seconds while it fetched chunks the renderer culls anyway.
        List<long[]> visited = new ArrayList<>();
        SoilOverlaySyncPolicy.forEachChunkOutward(10, -4, 3, (x, z) -> visited.add(new long[] {x, z}));

        assertArrayEquals(new long[] {10, -4}, visited.get(0), "the centre chunk must go out first");
    }

    @Test
    void outwardWalkNeverStepsBackTowardTheCentre() {
        List<Integer> distances = new ArrayList<>();
        SoilOverlaySyncPolicy.forEachChunkOutward(0, 0, 4,
                (x, z) -> distances.add(Math.max(Math.abs(x), Math.abs(z))));

        for (int i = 1; i < distances.size(); i++) {
            assertTrue(distances.get(i) >= distances.get(i - 1),
                    "distance from centre must never decrease as the sweep proceeds");
        }
    }

    @Test
    void outwardWalkCoversEverySquareExactlyOnce() {
        int radius = 5;
        Set<Long> seen = new HashSet<>();
        SoilOverlaySyncPolicy.forEachChunkOutward(-2, 7, radius,
                (x, z) -> assertTrue(seen.add(((long) x << 32) ^ (z & 0xFFFFFFFFL)),
                        "chunk " + x + "," + z + " visited twice"));

        int expected = (2 * radius + 1) * (2 * radius + 1);
        assertEquals(expected, seen.size(), "must cover the full square, no gaps");
    }

    @Test
    void outwardWalkOfZeroRadiusIsJustTheCentre() {
        List<long[]> visited = new ArrayList<>();
        SoilOverlaySyncPolicy.forEachChunkOutward(3, 3, 0, (x, z) -> visited.add(new long[] {x, z}));

        assertEquals(1, visited.size());
        assertArrayEquals(new long[] {3, 3}, visited.get(0));
    }

    @Test
    void displayingRequiresBothThePreferenceAndSoil() {
        assertTrue(DISPLAYING.displaying());
        assertFalse(new OverlayRules(false, true, 25.0, true).displaying());
        assertFalse(new OverlayRules(true, false, 25.0, true).displaying());
        assertFalse(new OverlayRules(false, false, 25.0, true).displaying());
    }
}
