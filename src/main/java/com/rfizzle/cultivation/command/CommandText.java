package com.rfizzle.cultivation.command;

import com.rfizzle.cultivation.soil.SoilBand;

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
}
