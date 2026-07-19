package com.rfizzle.cultivation.soil;

import com.rfizzle.cultivation.soil.SoilOverlaySyncPolicy.Action;
import com.rfizzle.cultivation.soil.SoilOverlaySyncPolicy.OverlayRules;
import org.junit.jupiter.api.Test;

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
        assertEquals(Action.CLEAR_AND_REFETCH, SoilOverlaySyncPolicy.decide(before, DISPLAYING));
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
        assertEquals(Action.CLEAR_AND_REFETCH, SoilOverlaySyncPolicy.decide(before, DISPLAYING));
    }

    @Test
    void bandCutoffChangeRefetchesBecauseCachedFlagsAreNowWrong() {
        // tiredThreshold feeds SoilOverlayFlags.computeFlags, so moving it rewrites
        // the band of positions already cached — clearing alone would leave them blank.
        OverlayRules after = new OverlayRules(true, true, 40.0, true);
        assertEquals(Action.CLEAR_AND_REFETCH, SoilOverlaySyncPolicy.decide(DISPLAYING, after));
    }

    @Test
    void groundTrackingChangeRefetches() {
        OverlayRules after = new OverlayRules(true, true, 25.0, false);
        assertEquals(Action.CLEAR_AND_REFETCH, SoilOverlaySyncPolicy.decide(DISPLAYING, after));
    }

    @Test
    void serverRuleChangesWhileHiddenOnlyClear() {
        // No point re-pulling what nothing will draw; the next toggle-on refetches.
        OverlayRules before = new OverlayRules(false, true, 25.0, true);
        OverlayRules after = new OverlayRules(false, true, 40.0, false);
        assertEquals(Action.CLEAR, SoilOverlaySyncPolicy.decide(before, after));
    }

    @Test
    void displayingRequiresBothThePreferenceAndSoil() {
        assertTrue(DISPLAYING.displaying());
        assertFalse(new OverlayRules(false, true, 25.0, true).displaying());
        assertFalse(new OverlayRules(true, false, 25.0, true).displaying());
        assertFalse(new OverlayRules(false, false, 25.0, true).displaying());
    }
}
