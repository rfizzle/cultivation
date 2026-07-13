package com.rfizzle.cultivation.soil;

/**
 * Pure fertility math for {@code design/SPEC.md} §1 — drain, band
 * classification, growth multipliers, and the lazy-recovery formula. No
 * Minecraft types so the whole contract unit-tests without a runtime.
 */
public final class SoilMath {
    public static final float MAX_FERTILITY = 100.0F;

    /** Rich starts at 75 regardless of the configurable Tired threshold (SPEC §1 band table). */
    public static final float RICH_THRESHOLD = 75.0F;

    /**
     * Floor for the growth multiplier. Vanilla growth rolls
     * {@code nextInt((int) (25.0F / speed) + 1)}: a multiplier of 0 would make
     * the division overflow into a negative {@code nextInt} bound and crash the
     * server tick, so a configured 0 means "grows ~2500x slower", never "divide
     * by zero".
     */
    public static final float MIN_GROWTH_MULTIPLIER = 0.01F;

    /** Vanilla rolls each random tick per block with probability randomTickSpeed / 4096 per game tick. */
    private static final double RANDOM_TICK_DENOMINATOR = 4096.0;

    private SoilMath() {
    }

    /** Clamps into [0, 100]; NaN (a tampered save) heals to pristine. */
    public static float clampFertility(float fertility) {
        if (Float.isNaN(fertility)) {
            return MAX_FERTILITY;
        }
        return Math.clamp(fertility, 0.0F, MAX_FERTILITY);
    }

    /** Fertility lost by one mature harvest; a first-ever harvest counts as rotated. */
    public static float drainAmount(boolean sameCrop, double harvestDrain, double rotationDrainMultiplier) {
        return (float) (sameCrop ? harvestDrain : harvestDrain * rotationDrainMultiplier);
    }

    /**
     * Fertility accrued over {@code elapsedTicks} of crop-free, non-farmland time —
     * the expected value of the live path over the same span, rain-blind by design
     * (weather history is never replayed).
     */
    public static float lazyRecovery(long elapsedTicks, double perRandomTick, int randomTickSpeed) {
        if (elapsedTicks <= 0 || randomTickSpeed <= 0) {
            return 0.0F;
        }
        return (float) (perRandomTick * elapsedTicks * randomTickSpeed / RANDOM_TICK_DENOMINATOR);
    }

    public static SoilBand band(float fertility, double tiredThreshold) {
        if (fertility <= 0.0F) {
            return SoilBand.EXHAUSTED;
        }
        if (fertility < tiredThreshold) {
            return SoilBand.TIRED;
        }
        if (fertility < RICH_THRESHOLD) {
            return SoilBand.FAIR;
        }
        return SoilBand.RICH;
    }

    /** The band's crop growth-speed multiplier; Rich and Fair grow at the vanilla rate. */
    public static float growthMultiplier(SoilBand band, double tiredMultiplier, double exhaustedMultiplier) {
        return switch (band) {
            case TIRED -> Math.max(MIN_GROWTH_MULTIPLIER, (float) tiredMultiplier);
            case EXHAUSTED -> Math.max(MIN_GROWTH_MULTIPLIER, (float) exhaustedMultiplier);
            case RICH, FAIR -> 1.0F;
        };
    }

    /**
     * The random-tick growth-roll bound scaled by a growth multiplier. Nether wart
     * and the sweet berry bush grow when {@code random.nextInt(bound) == 0}, so a
     * multiplier below 1 widens the bound (rarer growth) and 1.0 leaves it
     * untouched — the second-wave crops' equivalent of the {@code getGrowthSpeed}
     * scaling the farmland crops route through, since neither of these two does.
     * Never below 1 (a valid {@code nextInt} bound).
     */
    public static int scaledGrowthBound(int vanillaBound, float multiplier) {
        if (multiplier <= 0.0F) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, Math.round(vanillaBound / multiplier));
    }
}
