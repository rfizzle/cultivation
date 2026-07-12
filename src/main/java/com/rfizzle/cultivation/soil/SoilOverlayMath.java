package com.rfizzle.cultivation.soil;

/**
 * Pure, Minecraft-free arithmetic for the client soil-overlay renderer
 * ({@code mc-world-render}: keep fade/cull/brightness math out of the render
 * shell so it unit-tests from {@code src/test}). Lives in {@code main} because the
 * test source set does not see the client source set.
 */
public final class SoilOverlayMath {
    /** Darkest an overlay dims to in full darkness, so night cracks read without glowing. */
    public static final float MIN_BRIGHTNESS = 0.35F;

    private SoilOverlayMath() {
    }

    /** True when a camera-relative offset is within {@code maxBlocks} (compared squared). */
    public static boolean withinRenderDistanceSq(double dx, double dy, double dz, double maxBlocks) {
        double max = Math.max(0.0, maxBlocks);
        return dx * dx + dy * dy + dz * dz <= max * max;
    }

    /**
     * Maps a vanilla light level (0–15) to a brightness multiplier in
     * {@code [MIN_BRIGHTNESS, 1]} applied to the overlay's RGB, so the quad tracks
     * world lighting instead of rendering full-bright.
     */
    public static float brightnessFactor(int lightLevel) {
        int clamped = Math.clamp(lightLevel, 0, 15);
        return MIN_BRIGHTNESS + (1.0F - MIN_BRIGHTNESS) * (clamped / 15.0F);
    }
}
