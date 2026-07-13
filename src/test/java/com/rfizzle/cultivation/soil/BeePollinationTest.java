// Tier: 1 (pure JUnit)
package com.rfizzle.cultivation.soil;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tier-1 coverage of the bee-pollination gate math (SPEC §2). The POI query and
 * hive-occupancy reads need a live server world and are exercised in
 * {@code BeePollinationGameTest}; here we pin the pure multiplier decision.
 */
class BeePollinationTest {
    @Test
    void activeHiveAppliesTheConfiguredMultiplier() {
        assertEquals(1.1F, BeePollination.multiplier(true, 1.1), 1e-6);
        assertEquals(1.5F, BeePollination.multiplier(true, 1.5), 1e-6);
    }

    @Test
    void noHiveLeavesGrowthUntouched() {
        assertEquals(1.0F, BeePollination.multiplier(false, 1.1), 1e-6);
        assertEquals(1.0F, BeePollination.multiplier(false, 5.0), 1e-6);
    }
}
