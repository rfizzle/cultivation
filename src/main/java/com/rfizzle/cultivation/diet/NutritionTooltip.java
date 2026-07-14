package com.rfizzle.cultivation.diet;

import com.rfizzle.cultivation.attachment.DietData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Pure formatter for the nutrition line on a food's tooltip (SPEC §3): a food's
 * hunger and saturation, in the same quiet register as the fatigue line. Below
 * full dietary effectiveness the line leads with the fatigue-adjusted values and
 * keeps the base in parentheses, so it reports exactly what {@link
 * DietHandler#scale} feeds the player this bite. No Minecraft runtime state —
 * {@code (nutrition, saturation, effectiveness)} in, {@code List<Component>}
 * out — so every branch pins at Tier 1.
 */
public final class NutritionTooltip {
    public static final String KEY_BASE = "tooltip.cultivation.nutrition.base";
    public static final String KEY_ADJUSTED = "tooltip.cultivation.nutrition.adjusted";

    private NutritionTooltip() {
    }

    /**
     * The single nutrition line for a food restoring {@code nutrition} hunger and
     * {@code saturation} saturation points, at the current dietary {@code
     * effectiveness} (1.0 means no fatigue). At full effectiveness the base values
     * stand alone; below it the adjusted values lead with the base shown in
     * parentheses.
     */
    public static List<Component> lines(int nutrition, float saturation, double effectiveness) {
        String baseSaturation = formatSaturation(saturation);
        if (effectiveness >= 1.0) {
            return List.of(line(KEY_BASE, nutrition, baseSaturation));
        }
        int adjustedNutrition = DietData.scaledNutrition(nutrition, effectiveness);
        String adjustedSaturation = formatSaturation((float) (saturation * effectiveness));
        return List.of(line(KEY_ADJUSTED, adjustedNutrition, nutrition, adjustedSaturation, baseSaturation));
    }

    /** One-decimal saturation, trimming a whole number back to an integer (12.8, but 6 not 6.0). */
    static String formatSaturation(float value) {
        String formatted = String.format(Locale.ROOT, "%.1f", value);
        return formatted.endsWith(".0") ? formatted.substring(0, formatted.length() - 2) : formatted;
    }

    private static Component line(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.GRAY);
    }
}
