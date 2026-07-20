package com.rfizzle.cultivation.compat.appleskin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 guard for the AppleSkin suppression rule (mc-mod-testing): the nutrition
 * tooltip line renders only when the player has it enabled and AppleSkin is not
 * drawing the same shanks. The loader-backed entry point over this rule is covered
 * by {@code AppleskinCompatGameTest}, which runs with AppleSkin genuinely absent.
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
}
