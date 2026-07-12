package com.rfizzle.cultivation.soil;

import com.rfizzle.cultivation.attachment.SoilData;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoilOverlayFlagsTest {
    private static final double TIRED_THRESHOLD = 25.0;

    private static SoilData soil(float fertility, int enriched, int dose) {
        return new SoilData(fertility, Optional.empty(), enriched, dose, 0L, false);
    }

    private static byte flags(float fertility, int enriched, int dose) {
        return SoilOverlayFlags.computeFlags(soil(fertility, enriched, dose), TIRED_THRESHOLD);
    }

    @Test
    void bandBitsMatchEachBand() {
        assertEquals(SoilBand.RICH, SoilOverlayFlags.band(flags(100f, 0, 0)));
        assertEquals(SoilBand.FAIR, SoilOverlayFlags.band(flags(50f, 0, 0)));
        assertEquals(SoilBand.TIRED, SoilOverlayFlags.band(flags(10f, 0, 0)));
        assertEquals(SoilBand.EXHAUSTED, SoilOverlayFlags.band(flags(0f, 0, 0)));
    }

    @Test
    void richAndFairUninvestedAreNotDeviating() {
        assertFalse(SoilOverlayFlags.isDeviating(flags(100f, 0, 0)));
        assertFalse(SoilOverlayFlags.isDeviating(flags(75f, 0, 0)));
        assertFalse(SoilOverlayFlags.isDeviating(flags(50f, 0, 0)));
        assertFalse(SoilOverlayFlags.isDeviating(flags(25f, 0, 0)));
    }

    @Test
    void tiredAndExhaustedAreDeviating() {
        assertTrue(SoilOverlayFlags.isDeviating(flags(24.99f, 0, 0)));
        assertTrue(SoilOverlayFlags.isDeviating(flags(0f, 0, 0)));
    }

    @Test
    void investmentBitsSetAndDeviateEvenOnHealthySoil() {
        byte dosed = flags(100f, 0, 4);
        assertTrue(SoilOverlayFlags.hasDose(dosed));
        assertFalse(SoilOverlayFlags.isEnriched(dosed));
        assertTrue(SoilOverlayFlags.isDeviating(dosed));

        byte enriched = flags(100f, 10, 0);
        assertTrue(SoilOverlayFlags.isEnriched(enriched));
        assertFalse(SoilOverlayFlags.hasDose(enriched));
        assertTrue(SoilOverlayFlags.isDeviating(enriched));
    }

    @Test
    void cracksAndFlecksComposeInOneByte() {
        byte both = flags(10f, 10, 4);
        assertEquals(SoilBand.TIRED, SoilOverlayFlags.band(both));
        assertTrue(SoilOverlayFlags.hasDose(both));
        assertTrue(SoilOverlayFlags.isEnriched(both));
    }
}
