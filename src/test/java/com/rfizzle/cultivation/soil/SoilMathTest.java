package com.rfizzle.cultivation.soil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoilMathTest {
    private static final double HARVEST_DRAIN = 3.0;
    private static final double ROTATION_MULTIPLIER = 0.5;
    private static final double TIRED_THRESHOLD = 25.0;

    @Test
    void sameCropDrainsFullAmount() {
        assertEquals(3.0F, SoilMath.drainAmount(true, HARVEST_DRAIN, ROTATION_MULTIPLIER), 1e-6);
    }

    @Test
    void rotatedCropDrainsHalf() {
        assertEquals(1.5F, SoilMath.drainAmount(false, HARVEST_DRAIN, ROTATION_MULTIPLIER), 1e-6);
    }

    @Test
    void fertilityClampsAtZeroAndMax() {
        assertEquals(0.0F, SoilMath.clampFertility(-5.0F));
        assertEquals(100.0F, SoilMath.clampFertility(250.0F));
        assertEquals(42.5F, SoilMath.clampFertility(42.5F));
    }

    @Test
    void nanFertilityHealsToPristine() {
        assertEquals(SoilMath.MAX_FERTILITY, SoilMath.clampFertility(Float.NaN));
    }

    @ParameterizedTest
    @CsvSource({
            "100.0, RICH",
            "75.0,  RICH",
            "74.99, FAIR",
            "25.0,  FAIR",   // exactly the Tired threshold is Fair (SPEC §1 testing strategy)
            "24.99, TIRED",
            "0.01,  TIRED",
            "0.0,   EXHAUSTED",
    })
    void bandBoundaries(float fertility, SoilBand expected) {
        assertEquals(expected, SoilMath.band(fertility, TIRED_THRESHOLD));
    }

    @Test
    void growthMultiplierPerBand() {
        assertEquals(1.0F, SoilMath.growthMultiplier(SoilBand.RICH, 0.75, 0.5));
        assertEquals(1.0F, SoilMath.growthMultiplier(SoilBand.FAIR, 0.75, 0.5));
        assertEquals(0.75F, SoilMath.growthMultiplier(SoilBand.TIRED, 0.75, 0.5));
        assertEquals(0.5F, SoilMath.growthMultiplier(SoilBand.EXHAUSTED, 0.75, 0.5));
    }

    @Test
    void growthMultiplierIsFlooredAwayFromZero() {
        // A configured 0 must mean "grows very slowly", never a zero that vanilla's
        // 25.0F / speed division turns into a negative nextInt bound and a crash.
        assertEquals(SoilMath.MIN_GROWTH_MULTIPLIER, SoilMath.growthMultiplier(SoilBand.TIRED, 0.0, 0.0));
        assertEquals(SoilMath.MIN_GROWTH_MULTIPLIER, SoilMath.growthMultiplier(SoilBand.EXHAUSTED, 0.0, 0.0));
        assertEquals(SoilMath.MIN_GROWTH_MULTIPLIER, SoilMath.growthMultiplier(SoilBand.EXHAUSTED, 1.0e-9, 1.0e-9));
        assertTrue(SoilMath.growthMultiplier(SoilBand.EXHAUSTED, 0.5, 0.5) > SoilMath.MIN_GROWTH_MULTIPLIER);
    }

    @Test
    void scaledGrowthBoundWidensAsSoilTires() {
        // Healthy soil (1.0) leaves the vanilla roll bit-identical.
        assertEquals(10, SoilMath.scaledGrowthBound(10, 1.0F));
        assertEquals(5, SoilMath.scaledGrowthBound(5, 1.0F));
        // Tired (0.75) and Exhausted (0.5) widen the bound, so growth rolls succeed rarer.
        assertEquals(13, SoilMath.scaledGrowthBound(10, 0.75F));
        assertEquals(20, SoilMath.scaledGrowthBound(10, 0.5F));
        // Never below 1, and a non-positive multiplier halts growth without a bad bound.
        assertEquals(1, SoilMath.scaledGrowthBound(1, 5.0F));
        assertEquals(Integer.MAX_VALUE, SoilMath.scaledGrowthBound(10, 0.0F));
    }

    @Test
    void lazyRecoveryMatchesLivePathExpectation() {
        // Live path expectation: randomTickSpeed/4096 random ticks per block per game
        // tick, each worth `perRandomTick` fertility. One in-game day at defaults:
        // 2.0 * 24000 * 3 / 4096 = 35.15625 — SPEC §1's "~35 fertility/day".
        assertEquals(35.15625F, SoilMath.lazyRecovery(24000, 2.0, 3), 1e-4);
    }

    @Test
    void lazyRecoveryScalesWithRandomTickSpeed() {
        assertEquals(2.0F * SoilMath.lazyRecovery(1000, 2.0, 3), SoilMath.lazyRecovery(1000, 2.0, 6), 1e-5);
    }

    @Test
    void lazyRecoveryIsZeroForNonPositiveInputs() {
        assertEquals(0.0F, SoilMath.lazyRecovery(0, 2.0, 3));
        assertEquals(0.0F, SoilMath.lazyRecovery(-100, 2.0, 3));
        assertEquals(0.0F, SoilMath.lazyRecovery(1000, 2.0, 0));
    }

    @Test
    void monocultureSustainsThirtyThreeFullHarvestsFromFull() {
        // SPEC §1: at defaults a monoculture block sustains 33 harvests full → exhausted.
        float fertility = SoilMath.MAX_FERTILITY;
        int fullHarvests = 0;
        while (fertility > 0.0F) {
            fertility = SoilMath.clampFertility(fertility - SoilMath.drainAmount(true, HARVEST_DRAIN, ROTATION_MULTIPLIER));
            if (fertility > 0.0F) {
                fullHarvests++;
            }
        }
        assertEquals(33, fullHarvests);
    }
}
