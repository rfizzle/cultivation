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

    @Test
    void distinctKeepsFirstEncounterOrderAndDedupes() {
        assertEquals(List.of("wheat", "carrots", "potatoes"),
                CommandText.distinct(List.of("wheat", "carrots", "wheat", "potatoes", "carrots")));
        assertEquals(List.of(), CommandText.distinct(List.of()));
        assertEquals(List.of("wheat"), CommandText.distinct(List.of("wheat", "wheat")));
    }

    @Test
    void summarizeAveragesFertilityAndBandsOffTheMean() {
        // 100 + 50 -> mean 75 -> Rich (>= 75); tiredThreshold 25.
        CommandText.FieldSummary summary = CommandText.summarize(List.of(
                new CommandText.FieldBlock(100.0F, 0, 0),
                new CommandText.FieldBlock(50.0F, 0, 0)), 25.0);
        assertEquals(2, summary.soil());
        assertEquals(75, summary.avgPercent());
        assertEquals(SoilBand.RICH, summary.band());
    }

    @Test
    void summarizeCountsExhaustedEnrichedAndFertilizedBlocks() {
        CommandText.FieldSummary summary = CommandText.summarize(List.of(
                new CommandText.FieldBlock(0.0F, 0, 0),      // exhausted
                new CommandText.FieldBlock(0.0F, 15, 0),     // exhausted + enriched
                new CommandText.FieldBlock(60.0F, 10, 5),    // enriched + fertilized
                new CommandText.FieldBlock(60.0F, 0, 0)), 25.0);
        assertEquals(4, summary.soil());
        assertEquals(2, summary.exhausted());
        assertEquals(2, summary.enriched());
        assertEquals(1, summary.fertilized());
    }

    @Test
    void summarizeTreatsPristineBlocksAsFullFertility() {
        CommandText.FieldSummary summary = CommandText.summarize(List.of(
                new CommandText.FieldBlock(100.0F, 0, 0),
                new CommandText.FieldBlock(100.0F, 0, 0)), 25.0);
        assertEquals(100, summary.avgPercent());
        assertEquals(SoilBand.RICH, summary.band());
        assertEquals(0, summary.exhausted());
    }

    @Test
    void summarizeHandlesAnEmptySurvey() {
        CommandText.FieldSummary summary = CommandText.summarize(List.of(), 25.0);
        assertEquals(0, summary.soil());
        assertEquals(0, summary.avgPercent());
        assertEquals(0, summary.exhausted());
    }
}
