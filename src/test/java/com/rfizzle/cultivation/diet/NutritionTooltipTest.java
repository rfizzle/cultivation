// Tier: 2 (fabric-loader-junit)
package com.rfizzle.cultivation.diet;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tier-1 coverage of the nutrition tooltip formatter — (nutrition, saturation,
 * effectiveness) in, exact translation keys and args out. At full effectiveness
 * the base line stands alone; below it the adjusted values lead with the base in
 * parentheses, mirroring what {@link DietHandler#scale} actually feeds the
 * player. No Minecraft server, no Fabric.
 */
class NutritionTooltipTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static String key(Component line) {
        return ((TranslatableContents) line.getContents()).getKey();
    }

    private static Object[] args(Component line) {
        return ((TranslatableContents) line.getContents()).getArgs();
    }

    @Test
    void fullEffectivenessShowsTheBaseLineAlone() {
        // Cooked beef: 8 hunger, 12.8 absolute saturation.
        List<Component> lines = NutritionTooltip.lines(8, 12.8F, 1.0);
        assertEquals(1, lines.size());
        assertEquals(NutritionTooltip.KEY_BASE, key(lines.get(0)));
        assertEquals(8, args(lines.get(0))[0]);
        assertEquals("12.8", args(lines.get(0))[1]);
    }

    @Test
    void reducedEffectivenessLeadsWithAdjustedAndKeepsBaseInParentheses() {
        // At 75% effectiveness: 8 -> round(6.0) = 6 hunger, 12.8 -> 9.6 saturation.
        List<Component> lines = NutritionTooltip.lines(8, 12.8F, 0.75);
        assertEquals(1, lines.size());
        assertEquals(NutritionTooltip.KEY_ADJUSTED, key(lines.get(0)));
        Object[] args = args(lines.get(0));
        assertEquals(6, args[0]);       // adjusted hunger
        assertEquals(8, args[1]);       // base hunger
        assertEquals("9.6", args[2]);   // adjusted saturation
        assertEquals("12.8", args[3]);  // base saturation
    }

    @Test
    void adjustedHungerNeverDropsBelowOneForARealFood() {
        // A 1-hunger food at the floor still restores at least 1 (DietData.scaledNutrition).
        List<Component> lines = NutritionTooltip.lines(1, 0.6F, 0.5);
        assertEquals(NutritionTooltip.KEY_ADJUSTED, key(lines.get(0)));
        assertEquals(1, args(lines.get(0))[0]);
    }

    @Test
    void wholeSaturationRendersWithoutADecimal() {
        assertEquals("6", NutritionTooltip.formatSaturation(6.0F));
        assertEquals("12.8", NutritionTooltip.formatSaturation(12.8F));
        assertEquals("2.4", NutritionTooltip.formatSaturation(2.4F));
    }
}
