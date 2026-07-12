package com.rfizzle.cultivation.command;

import com.rfizzle.cultivation.soil.SoilBand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tier-1 coverage for the plumbing-free {@code /cultivation} formatting helpers:
 * percent rounding, band lang keys, and the last-three-foods tail slice.
 */
class CommandTextTest {

    @Test
    void percentRoundsToNearestWholeNumber() {
        assertEquals(62, CommandText.percent(61.6F));
        assertEquals(62, CommandText.percent(62.4F));
        assertEquals(0, CommandText.percent(0.4F));
        assertEquals(1, CommandText.percent(0.5F));
        assertEquals(100, CommandText.percent(100.0F));
    }

    @Test
    void bandKeyMapsEveryBandToItsLowercaseName() {
        assertEquals("command.cultivation.soil.band.rich", CommandText.bandKey(SoilBand.RICH));
        assertEquals("command.cultivation.soil.band.fair", CommandText.bandKey(SoilBand.FAIR));
        assertEquals("command.cultivation.soil.band.tired", CommandText.bandKey(SoilBand.TIRED));
        assertEquals("command.cultivation.soil.band.exhausted", CommandText.bandKey(SoilBand.EXHAUSTED));
    }

    @Test
    void lastFoodsReturnsTheTailInOrder() {
        assertEquals(List.of("c", "d", "e"), CommandText.lastFoods(List.of("a", "b", "c", "d", "e"), 3));
    }

    @Test
    void lastFoodsReturnsWholeListWhenShorter() {
        assertEquals(List.of("a", "b"), CommandText.lastFoods(List.of("a", "b"), 3));
        assertEquals(List.of("a", "b", "c"), CommandText.lastFoods(List.of("a", "b", "c"), 3));
    }

    @Test
    void lastFoodsHandlesEmptyAndNonPositiveCount() {
        assertEquals(List.of(), CommandText.lastFoods(List.of(), 3));
        assertEquals(List.of(), CommandText.lastFoods(List.of("a", "b"), 0));
    }
}
