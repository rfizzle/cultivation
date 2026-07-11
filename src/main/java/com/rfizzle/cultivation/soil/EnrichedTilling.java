package com.rfizzle.cultivation.soil;

import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.config.CultivationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;

/**
 * Enriched tilling ({@code design/SPEC.md} §5): a hoe use that creates farmland
 * records a permanent bonus-drop chance by hoe tier — diamond and netherite
 * till better farmland, everything below records nothing. The chance is rolled
 * per mature harvest at the choke point; reversion to dirt clears it
 * ({@link com.rfizzle.cultivation.attachment.SoilData#withInvestmentsCleared()}),
 * and re-tilling records whatever hoe performs the new till.
 */
public final class EnrichedTilling {
    private EnrichedTilling() {
    }

    /**
     * The bonus chance a tilling hoe records, in percent. Keyed by the hoe's
     * {@link Tier} rather than item identity, so a modded hoe on a vanilla tier
     * earns that tier's chance; every tier below diamond — and any non-hoe —
     * records 0.
     */
    public static int chanceFor(ItemStack stack, CultivationConfig config) {
        if (!(stack.getItem() instanceof HoeItem hoe)) {
            return 0;
        }
        Tier tier = hoe.getTier();
        if (tier == Tiers.DIAMOND) {
            return config.diamondHoeEnrichChance;
        }
        if (tier == Tiers.NETHERITE) {
            return config.netheriteHoeEnrichChance;
        }
        return 0;
    }

    /**
     * Pure roll decision for the harvest choke point: {@code roll} is uniform
     * in {@code [0, 100)}, so a chance of 15 grants on rolls 0–14.
     */
    public static boolean grantsBonus(int chance, int roll) {
        return chance > 0 && roll < chance;
    }

    /**
     * Records the tilling tool's tier chance. Called from the {@code HoeItem}
     * mixin after a confirmed farmland-creating till, so a failed use can never
     * write. A 0-chance till still overwrites a tracked entry — re-tilling
     * records whatever hoe is used — but never creates one just to evict it.
     */
    public static void onFarmlandTilled(ServerLevel level, BlockPos pos, ItemStack tool) {
        CultivationConfig config = CultivationConfig.get();
        if (!config.enableEnrichedTilling) {
            return;
        }
        int chance = chanceFor(tool, config);
        if (chance == 0 && SoilStores.peek(level, pos) == null) {
            return;
        }
        SoilStores.update(level, pos, true, data -> data.withEnrichedChance(chance));
    }
}
