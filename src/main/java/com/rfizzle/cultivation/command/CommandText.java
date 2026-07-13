package com.rfizzle.cultivation.command;

import com.rfizzle.cultivation.soil.SoilBand;
import com.rfizzle.cultivation.soil.SoilMath;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Plumbing-free formatting for the {@code /cultivation} command output — the
 * pure bits {@code mc-commands} wants split out from the Brigadier wiring so
 * they unit-test at Tier 1 without a runtime. No Minecraft types beyond the
 * MC-free {@link SoilBand} enum, so the whole class loads on the plain JUnit
 * classpath.
 */
public final class CommandText {
    private CommandText() {
    }

    /** Whole-percent fertility for display; matches the SPEC §9 example ({@code 62%}). */
    public static int percent(float fertility) {
        return Math.round(fertility);
    }

    /** The band's display lang key, keyed by the lower-cased enum name. */
    public static String bandKey(SoilBand band) {
        return "command.cultivation.soil.band." + band.name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * The last {@code n} entries of {@code history}, oldest first — the diet
     * history is stored up to {@code MAX_HISTORY} deep, so "last three foods" is
     * a tail slice, not the whole list. A shorter list returns unchanged.
     */
    public static <T> List<T> lastFoods(List<T> history, int n) {
        if (n <= 0) {
            return List.of();
        }
        int size = history.size();
        if (size <= n) {
            return List.copyOf(history);
        }
        return List.copyOf(history.subList(size - n, size));
    }

    /**
     * The distinct values of {@code values}, keeping first-encounter order — the
     * field survey's crop line reports each crop the plot remembers once, in the
     * spatial order its blocks were walked.
     */
    public static <T> List<T> distinct(List<T> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    /**
     * One surveyed soil block's state, as the aggregate needs it — the MC-free
     * slice of {@code SoilData} so {@link #summarize} unit-tests at Tier 1.
     * Untracked (pristine) columns feed a {@code fertility} of {@link SoilMath#MAX_FERTILITY}
     * and zero bonuses.
     */
    public record FieldBlock(float fertility, int enrichedChance, int fertilizerRemaining) {
    }

    /**
     * The aggregate of a field survey: how many soil blocks it covered (farmland,
     * or a second-wave crop's ground), the mean fertility as a whole percent and
     * its band, and the count of blocks that are exhausted, enriched, or hold a
     * Fertilizer dose.
     */
    public record FieldSummary(int soil, int avgPercent, SoilBand band,
                               int exhausted, int enriched, int fertilized) {
    }

    /**
     * Reduces a plot's surveyed blocks to a {@link FieldSummary}. The band follows
     * the mean fertility through the same {@link SoilMath#band} the single-block
     * report uses; exhausted is counted by that band so the rule stays single-sourced.
     * An empty survey yields all zeros (the command never calls it empty — the
     * looked-at center is always tracked soil).
     */
    public static FieldSummary summarize(List<FieldBlock> blocks, double tiredThreshold) {
        int soil = blocks.size();
        if (soil == 0) {
            return new FieldSummary(0, 0, SoilBand.EXHAUSTED, 0, 0, 0);
        }
        float sum = 0.0F;
        int exhausted = 0;
        int enriched = 0;
        int fertilized = 0;
        for (FieldBlock block : blocks) {
            sum += block.fertility();
            if (SoilMath.band(block.fertility(), tiredThreshold) == SoilBand.EXHAUSTED) {
                exhausted++;
            }
            if (block.enrichedChance() > 0) {
                enriched++;
            }
            if (block.fertilizerRemaining() > 0) {
                fertilized++;
            }
        }
        float mean = sum / soil;
        return new FieldSummary(soil, percent(mean), SoilMath.band(mean, tiredThreshold),
                exhausted, enriched, fertilized);
    }
}
