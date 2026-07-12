package com.rfizzle.cultivation.soil;

import com.rfizzle.cultivation.attachment.SoilData;
import org.jetbrains.annotations.Nullable;

/**
 * The 4-bit overlay flag byte that soil sync carries per position
 * ({@code design/SPEC.md} §1 Visual Feedback): the minimum a client needs to
 * pick which overlay quads to draw, derived from a position's {@link SoilData}
 * and never persisted.
 *
 * <ul>
 * <li>bits 0-1 — the {@link SoilBand} ordinal (RICH 0, FAIR 1, TIRED 2, EXHAUSTED 3)</li>
 * <li>bit 2 — an active Fertilizer dose ({@code fertilizerRemaining > 0})</li>
 * <li>bit 3 — enrichment ({@code enrichedChance > 0})</li>
 * </ul>
 *
 * <p>A position is <em>visually deviating</em> — worth syncing and drawing — only
 * when its band is Tired/Exhausted (a crack overlay) or it carries an investment
 * (a fleck overlay). Rich and Fair uninvested farmland renders nothing, so it is
 * never synced.
 *
 * <p>Pure and Minecraft-light so it unit-tests without a client or server.
 */
public final class SoilOverlayFlags {
    public static final int BAND_MASK = 0b0011;
    public static final int DOSE_BIT = 0b0100;
    public static final int ENRICHED_BIT = 0b1000;

    // Cached to avoid SoilBand.values() cloning the backing array once per drawn
    // position per frame in the renderer's hot path.
    private static final SoilBand[] BANDS = SoilBand.values();

    private static final Transition REMOVE = new Transition(false, (byte) 0);

    private SoilOverlayFlags() {
    }

    /**
     * A single-position overlay delta: {@code present == false} drops the position
     * client-side, otherwise {@code flags} is the new overlay byte.
     */
    public record Transition(boolean present, byte flags) {
    }

    /** Packs a position's soil state into the overlay flag byte. */
    public static byte computeFlags(SoilData data, double tiredThreshold) {
        int band = SoilMath.band(data.fertility(), tiredThreshold).ordinal();
        int flags = band & BAND_MASK;
        if (data.fertilizerRemaining() > 0) {
            flags |= DOSE_BIT;
        }
        if (data.enrichedChance() > 0) {
            flags |= ENRICHED_BIT;
        }
        return (byte) flags;
    }

    /** The band component of a flag byte. */
    public static SoilBand band(byte flags) {
        return BANDS[flags & BAND_MASK];
    }

    /**
     * The overlay delta a write should push given the pre- and post-write flag
     * bytes, or {@code null} when the client-visible representation is unchanged
     * (both non-deviating, or both deviating with identical flags). Pure, so the
     * full transition table unit-tests without a server.
     */
    @Nullable
    public static Transition transition(byte before, byte after) {
        boolean beforeVisible = isDeviating(before);
        boolean afterVisible = isDeviating(after);
        if (!beforeVisible && !afterVisible) {
            return null;
        }
        if (beforeVisible && afterVisible && before == after) {
            return null;
        }
        return afterVisible ? new Transition(true, after) : REMOVE;
    }

    public static boolean hasDose(byte flags) {
        return (flags & DOSE_BIT) != 0;
    }

    public static boolean isEnriched(byte flags) {
        return (flags & ENRICHED_BIT) != 0;
    }

    /**
     * True when the flags describe something to draw — a Tired/Exhausted crack or
     * any investment fleck. Rich/Fair uninvested soil is not deviating.
     */
    public static boolean isDeviating(byte flags) {
        return (flags & BAND_MASK) >= SoilBand.TIRED.ordinal()
                || (flags & (DOSE_BIT | ENRICHED_BIT)) != 0;
    }
}
