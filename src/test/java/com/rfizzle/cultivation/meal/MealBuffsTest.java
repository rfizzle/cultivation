package com.rfizzle.cultivation.meal;

import com.rfizzle.cultivation.meal.MealBuffs.Buff;
import com.rfizzle.cultivation.meal.MealBuffs.Grant;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier 1 coverage of the pure meal-buff core (SPEC §4): the item→grants mapping,
 * Suspicious Stew's uniform roll, and Sated's exhaustion multiplier. The in-world
 * wiring — effect grant/removal on real eats — is proven at Tier 3 in
 * {@code MealBuffGameTest}.
 */
class MealBuffsTest {

    private static ResourceLocation mc(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    @Test
    void eachStewGrantsItsSingleBuffAtLevelOne() {
        assertEquals(List.of(new Grant(Buff.NIMBLE, 0)), MealBuffs.grants(mc("rabbit_stew"), 0));
        assertEquals(List.of(new Grant(Buff.DILIGENT, 0)), MealBuffs.grants(mc("beetroot_soup"), 0));
        assertEquals(List.of(new Grant(Buff.SATED, 0)), MealBuffs.grants(mc("mushroom_stew"), 0));
    }

    @Test
    void cakeGrantsTheWholeTrioAtLevelOne() {
        assertEquals(
                List.of(new Grant(Buff.NIMBLE, 0), new Grant(Buff.DILIGENT, 0), new Grant(Buff.SATED, 0)),
                MealBuffs.grants(mc("cake"), 0));
    }

    @Test
    void suspiciousStewPicksOneBuffAtLevelTwoByRoll() {
        assertEquals(List.of(new Grant(Buff.NIMBLE, 1)), MealBuffs.grants(mc("suspicious_stew"), 0));
        assertEquals(List.of(new Grant(Buff.DILIGENT, 1)), MealBuffs.grants(mc("suspicious_stew"), 1));
        assertEquals(List.of(new Grant(Buff.SATED, 1)), MealBuffs.grants(mc("suspicious_stew"), 2));
    }

    @Test
    void suspiciousRollWrapsAcrossTheThreeBuffs() {
        // The caller passes random.nextInt(3), but the mapping must be total for any int.
        assertEquals(List.of(new Grant(Buff.NIMBLE, 1)), MealBuffs.grants(mc("suspicious_stew"), 3));
        assertEquals(List.of(new Grant(Buff.SATED, 1)), MealBuffs.grants(mc("suspicious_stew"), -1));
    }

    @Test
    void unbuffedFoodGrantsNothing() {
        assertTrue(MealBuffs.grants(mc("carrot"), 0).isEmpty());
        assertTrue(MealBuffs.grants(mc("cooked_beef"), 1).isEmpty());
        assertTrue(MealBuffs.grants(ResourceLocation.fromNamespaceAndPath("cultivation", "fertilizer"), 0).isEmpty());
    }

    @Test
    void satedMultiplierCutsTenPercentPerLevel() {
        assertEquals(0.9, MealBuffs.satedMultiplier(0), 1e-9);
        assertEquals(0.8, MealBuffs.satedMultiplier(1), 1e-9);
    }

    @Test
    void satedMultiplierFloorsAtZero() {
        // A hand-tuned high amplifier must never turn hunger drain negative.
        assertEquals(0.0, MealBuffs.satedMultiplier(9), 1e-9);
        assertEquals(0.0, MealBuffs.satedMultiplier(20), 1e-9);
    }
}
