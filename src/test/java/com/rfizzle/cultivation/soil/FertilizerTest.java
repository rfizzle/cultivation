package com.rfizzle.cultivation.soil;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure dose bookkeeping for SPEC §6 — the decisions that ride the world-mutating shell. */
class FertilizerTest {
    @Test
    void emptyDoseAcceptsAFill() {
        assertTrue(Fertilizer.canApplyDose(0, 15), "an unfertilized block must accept a dose");
    }

    @Test
    void partialDoseTopsUp() {
        assertTrue(Fertilizer.canApplyDose(1, 15), "a nearly-spent dose must top up");
        assertTrue(Fertilizer.canApplyDose(14, 15), "one short of full must still top up");
    }

    @Test
    void fullDoseFails() {
        assertFalse(Fertilizer.canApplyDose(15, 15), "an already-full dose must fail without consuming");
    }

    @Test
    void overFullDoseFails() {
        // A dose left over from a higher configured value must not re-accept at a lower one.
        assertFalse(Fertilizer.canApplyDose(20, 15), "a dose above the configured size must not re-accept");
    }

    @Test
    void bonusRidesAnyLiveDose() {
        assertTrue(Fertilizer.grantsHarvestBonus(1), "a block with a live dose pays the bonus");
        assertTrue(Fertilizer.grantsHarvestBonus(15), "a full dose pays the bonus");
    }

    @Test
    void bonusStopsAtZero() {
        assertFalse(Fertilizer.grantsHarvestBonus(0), "a spent dose pays nothing");
    }
}
