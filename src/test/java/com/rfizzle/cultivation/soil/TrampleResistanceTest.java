package com.rfizzle.cultivation.soil;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrampleResistanceTest {
    @Test
    void enrichedBlockResistsAPlayerTrample() {
        assertTrue(TrampleResistance.resistsTrample(true, true, 10));
        assertTrue(TrampleResistance.resistsTrample(true, true, 15));
        assertTrue(TrampleResistance.resistsTrample(true, true, 1));
    }

    @Test
    void unEnrichedBlockNeverResists() {
        assertFalse(TrampleResistance.resistsTrample(true, true, 0));
    }

    @Test
    void mobsNeverResist() {
        // World danger stays Tribulation's ground — only a player's own feet are covered.
        assertFalse(TrampleResistance.resistsTrample(true, false, 15));
        assertFalse(TrampleResistance.resistsTrample(true, false, 0));
    }

    @Test
    void disabledToggleNeverResists() {
        assertFalse(TrampleResistance.resistsTrample(false, true, 15));
        assertFalse(TrampleResistance.resistsTrample(false, false, 15));
    }
}
