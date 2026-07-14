package com.rfizzle.cultivation.client;

import com.rfizzle.cultivation.attachment.DietData;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.diet.NutritionTooltip;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Renders Cultivation's food-tooltip lines (SPEC §3): a nutrition line — hunger
 * and saturation, fatigue-adjusted for this bite when a penalty is active — above
 * the dietary-fatigue line ("Losing its appeal (-N%)", or "Thoroughly tired of
 * this (-N%)" at the floor). Vanilla owns these items, so this rides {@code
 * ItemTooltipCallback} rather than {@code appendHoverText}; the two lines carry
 * independent client toggles, {@code showNutritionTooltips} and {@code
 * showFatigueTooltips}.
 */
public final class DietTooltip {
    // AppleSkin already draws hunger/saturation shanks under food tooltips; when it is
    // present we defer the nutrition line to it rather than double-print. Resolved once —
    // the loaded mod set is fixed for the session. The fatigue line has no AppleSkin
    // counterpart, so it is never suppressed.
    private static final boolean APPLESKIN_PRESENT = FabricLoader.getInstance().isModLoaded("appleskin");

    private DietTooltip() {
    }

    public static void append(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
        CultivationConfig config = CultivationConfig.get();
        boolean wantNutrition = config.showNutritionTooltips && !APPLESKIN_PRESENT;
        boolean wantFatigue = config.showFatigueTooltips;
        if (!wantNutrition && !wantFatigue) {
            return;
        }

        // Resolve the food component first: a non-food hover (the common inventory case) has no
        // nutrition line to draw, so it skips the diet-stack lookup and only runs the fatigue branch.
        FoodProperties food = wantNutrition ? stack.get(DataComponents.FOOD) : null;
        if (food == null && !wantFatigue) {
            return;
        }

        // The fatigue formula is server-authoritative: read its knobs from the synced server
        // config, falling back to the local file only when standalone. With fatigue disabled,
        // retained stacks are inert (SPEC §3), so treat the food as unfatigued — the nutrition
        // line shows base values and no fatigue line renders.
        CultivationConfig fatigueConfig = ClientCultivationConfig.effective();
        float perRepeat = (float) fatigueConfig.fatiguePerRepeat;
        float floor = (float) fatigueConfig.fatigueFloor;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        int stacks = fatigueConfig.enableDietaryFatigue ? ClientDietData.snapshot().stackCount(id) : 0;
        double effectiveness = DietData.effectiveness(stacks, perRepeat, floor);

        if (food != null) {
            lines.addAll(NutritionTooltip.lines(food.nutrition(), food.saturation(), effectiveness));
        }

        if (wantFatigue && stacks > 0) {
            int percent = DietData.reductionPercent(effectiveness);
            String key = DietData.atFloor(stacks, perRepeat, floor)
                    ? "tooltip.cultivation.fatigue.tired"
                    : "tooltip.cultivation.fatigue.appeal";
            lines.add(Component.translatable(key, percent).withStyle(ChatFormatting.GRAY));
        }
    }
}
