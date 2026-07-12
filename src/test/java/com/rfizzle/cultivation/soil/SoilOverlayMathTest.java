package com.rfizzle.cultivation.soil;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoilOverlayMathTest {
    @Test
    void withinRenderDistanceIsInclusiveAtTheEdge() {
        assertTrue(SoilOverlayMath.withinRenderDistanceSq(24.0, 0.0, 0.0, 24.0));
        assertTrue(SoilOverlayMath.withinRenderDistanceSq(3.0, 4.0, 0.0, 5.0));
        assertFalse(SoilOverlayMath.withinRenderDistanceSq(24.1, 0.0, 0.0, 24.0));
    }

    @Test
    void chunkDistanceIsChebyshevAndSymmetric() {
        assertEquals(0L, SoilOverlayMath.chunkChebyshevDistance(3, -4, 3, -4));
        assertEquals(5L, SoilOverlayMath.chunkChebyshevDistance(0, 0, 5, 2));
        assertEquals(5L, SoilOverlayMath.chunkChebyshevDistance(5, 2, 0, 0));
    }

    @Test
    void chunkDistanceDoesNotOverflowOnCraftedCoordinates() {
        // A client sending playerChunkX + Integer.MIN_VALUE would make an int
        // subtraction wrap and Math.abs stay negative — the long path must not.
        long distance = SoilOverlayMath.chunkChebyshevDistance(0, 0, Integer.MIN_VALUE, 0);
        assertTrue(distance > 0, "crafted min-value coordinate must not defeat the range gate");
        assertTrue(distance > 66, "must exceed any real view distance (max 32 + slack)");
    }

    @Test
    void brightnessFloorAndCeilingClamp() {
        assertEquals(SoilOverlayMath.MIN_BRIGHTNESS, SoilOverlayMath.brightnessFactor(0), 1e-6);
        assertEquals(SoilOverlayMath.MIN_BRIGHTNESS, SoilOverlayMath.brightnessFactor(-5), 1e-6);
        assertEquals(1.0F, SoilOverlayMath.brightnessFactor(15), 1e-6);
        assertEquals(1.0F, SoilOverlayMath.brightnessFactor(99), 1e-6);
    }

    @Test
    void brightnessIsMonotonicBetweenFloorAndCeiling() {
        float prev = SoilOverlayMath.brightnessFactor(0);
        for (int light = 1; light <= 15; light++) {
            float next = SoilOverlayMath.brightnessFactor(light);
            assertTrue(next > prev, "brightness must rise with light level");
            prev = next;
        }
    }
}
