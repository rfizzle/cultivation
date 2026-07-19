package com.rfizzle.cultivation.soil;

/**
 * Vanilla {@code Level#levelEvent} ids the mod replays, named once so the two
 * soil seams that fire the same effect cannot drift apart.
 */
public final class LevelEvents {
    /** Bone meal on a block: green sparkle particles plus the use sound, client-side. */
    public static final int BONE_MEAL = 1505;

    private LevelEvents() {
    }
}
