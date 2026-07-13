package com.rfizzle.cultivation.meal;

import com.rfizzle.cultivation.meal.MealBuffs.Buff;
import com.rfizzle.cultivation.meal.MealBuffs.Grant;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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

    /** A roll supplier that fails if consulted — proves the fixed foods never draw. */
    private static IntSupplier noRoll() {
        return () -> {
            fail("the roll must not be drawn for a non-suspicious food");
            return 0; // unreachable — fail always throws
        };
    }

    private static IntSupplier roll(int value) {
        return () -> value;
    }

    @Test
    void eachStewGrantsItsSingleBuffAtLevelOne() {
        assertEquals(List.of(new Grant(Buff.NIMBLE, 0)), MealBuffs.grants(mc("rabbit_stew"), noRoll()));
        assertEquals(List.of(new Grant(Buff.DILIGENT, 0)), MealBuffs.grants(mc("beetroot_soup"), noRoll()));
        assertEquals(List.of(new Grant(Buff.SATED, 0)), MealBuffs.grants(mc("mushroom_stew"), noRoll()));
    }

    @Test
    void snackFoodsGrantOneStewEffectAtLevelOne() {
        // Pumpkin pie and cookies reuse the stews' effects at level I; the snack tier
        // sits below the stews through its shorter duration register, not a weaker level.
        assertEquals(List.of(new Grant(Buff.NIMBLE, 0)), MealBuffs.grants(mc("cookie"), noRoll()));
        assertEquals(List.of(new Grant(Buff.SATED, 0)), MealBuffs.grants(mc("pumpkin_pie"), noRoll()));
    }

    @Test
    void cakeGrantsTheWholeTrioAtLevelOne() {
        assertEquals(
                List.of(new Grant(Buff.NIMBLE, 0), new Grant(Buff.DILIGENT, 0), new Grant(Buff.SATED, 0)),
                MealBuffs.grants(mc("cake"), noRoll()));
    }

    @Test
    void suspiciousStewPicksOneBuffAtLevelTwoByRoll() {
        assertEquals(List.of(new Grant(Buff.NIMBLE, 1)), MealBuffs.grants(mc("suspicious_stew"), roll(0)));
        assertEquals(List.of(new Grant(Buff.DILIGENT, 1)), MealBuffs.grants(mc("suspicious_stew"), roll(1)));
        assertEquals(List.of(new Grant(Buff.SATED, 1)), MealBuffs.grants(mc("suspicious_stew"), roll(2)));
    }

    @Test
    void suspiciousRollWrapsAcrossTheThreeBuffs() {
        // The caller passes random.nextInt(3), but the mapping must be total for any int.
        assertEquals(List.of(new Grant(Buff.NIMBLE, 1)), MealBuffs.grants(mc("suspicious_stew"), roll(3)));
        assertEquals(List.of(new Grant(Buff.SATED, 1)), MealBuffs.grants(mc("suspicious_stew"), roll(-1)));
    }

    @Test
    void unbuffedFoodGrantsNothing() {
        assertTrue(MealBuffs.grants(mc("carrot"), noRoll()).isEmpty());
        assertTrue(MealBuffs.grants(mc("cooked_beef"), noRoll()).isEmpty());
        assertTrue(MealBuffs.grants(ResourceLocation.fromNamespaceAndPath("cultivation", "fertilizer"), noRoll()).isEmpty());
    }

    @Test
    void durationTicksSelectsTheRightRegister() {
        // Distinct sentinels so a swapped argument would surface as the wrong value.
        int meal = 2400;
        int cake = 1200;
        int snack = 600;
        assertEquals(cake, MealBuffs.durationTicks(mc("cake"), meal, cake, snack));
        assertEquals(snack, MealBuffs.durationTicks(mc("cookie"), meal, cake, snack));
        assertEquals(snack, MealBuffs.durationTicks(mc("pumpkin_pie"), meal, cake, snack));
        assertEquals(meal, MealBuffs.durationTicks(mc("rabbit_stew"), meal, cake, snack));
        assertEquals(meal, MealBuffs.durationTicks(mc("suspicious_stew"), meal, cake, snack));
        // An unbuffed id falls to the meal default; grant() never reads it (grants is empty).
        assertEquals(meal, MealBuffs.durationTicks(mc("carrot"), meal, cake, snack));
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
