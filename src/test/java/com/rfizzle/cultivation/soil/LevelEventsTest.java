// Tier: 1 (pure JUnit)
package com.rfizzle.cultivation.soil;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Guards the vanilla level-event ids the soil seams replay. */
class LevelEventsTest {
    @Test
    void boneMealMatchesTheVanillaId() {
        // 1505 is vanilla's bone-meal-on-block effect (green sparkles + use sound).
        // Both the Fertilizer dose and the bone-meal fertility restore replay it,
        // so a drift here silently changes the feedback on two unrelated seams.
        assertEquals(1505, LevelEvents.BONE_MEAL, "bone meal must stay the vanilla level event id");
    }
}
