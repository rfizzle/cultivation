package com.rfizzle.cultivation.soil;

/**
 * Display and behavior bands for a farmland block's fertility, per
 * {@code design/SPEC.md} §1: Rich 75–100, Fair {@code tiredThreshold}–74.99,
 * Tired 0&lt;f&lt;{@code tiredThreshold}, Exhausted exactly 0.
 */
public enum SoilBand {
    RICH,
    FAIR,
    TIRED,
    EXHAUSTED
}
