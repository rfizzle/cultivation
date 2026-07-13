package com.rfizzle.cultivation.client;

import com.rfizzle.cultivation.attachment.DietData;
import com.rfizzle.cultivation.config.CultivationConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Renders the dietary-fatigue line on a food's tooltip (SPEC §3): once a food
 * carries any fatigue, "Losing its appeal (-N%)", and at the floor "Thoroughly
 * tired of this (-N%)". Vanilla owns these items, so this rides {@code
 * ItemTooltipCallback} rather than {@code appendHoverText}; the toggle is the
 * client-side {@code showFatigueTooltips}.
 */
public final class DietTooltip {
    private DietTooltip() {
    }

    public static void append(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
        if (!CultivationConfig.get().showFatigueTooltips) {
            return;
        }
        ClientDietData.Snapshot snapshot = ClientDietData.snapshot();
        if (snapshot.isEmpty()) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        int stacks = snapshot.stackCount(id);
        if (stacks <= 0) {
            return;
        }
        // The fatigue formula is server-authoritative: read its knobs from the synced
        // server config, falling back to the local file only when standalone.
        CultivationConfig config = ClientCultivationConfig.effective();
        float perRepeat = (float) config.fatiguePerRepeat;
        float floor = (float) config.fatigueFloor;
        double effectiveness = DietData.effectiveness(stacks, perRepeat, floor);
        int percent = DietData.reductionPercent(effectiveness);
        String key = DietData.atFloor(stacks, perRepeat, floor)
                ? "tooltip.cultivation.fatigue.tired"
                : "tooltip.cultivation.fatigue.appeal";
        lines.add(Component.translatable(key, percent).withStyle(ChatFormatting.GRAY));
    }
}
