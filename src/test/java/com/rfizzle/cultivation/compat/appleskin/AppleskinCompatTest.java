package com.rfizzle.cultivation.compat.appleskin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 guard for the AppleSkin suppression rule (mc-mod-testing): the nutrition
 * tooltip line renders only when the player has it enabled and AppleSkin is not
 * drawing the same shanks.
 */
class AppleskinCompatTest {
    @Test
    void showsTheLineOnADefaultInstall() {
        assertTrue(AppleskinCompat.showsNutritionLine(true, false));
    }

    @Test
    void defersToAppleskinWhenItIsPresent() {
        assertFalse(AppleskinCompat.showsNutritionLine(true, true));
    }

    @Test
    void honorsTheConfigOptOutWithoutAppleskin() {
        assertFalse(AppleskinCompat.showsNutritionLine(false, false));
    }

    @Test
    void staysSuppressedWhenBothOptOutAndAppleskinApply() {
        assertFalse(AppleskinCompat.showsNutritionLine(false, true));
    }

    @Test
    void resolvesTheRuleWithoutTouchingTheLoader() {
        // The loader lookup lives in a nested holder precisely so this class initializes
        // outside a Fabric environment. Hoisting the constant back onto the outer class
        // would fail here with a FabricLoader bootstrap error rather than silently.
        assertDoesNotThrow(() -> AppleskinCompat.showsNutritionLine(true, false));
    }
}
