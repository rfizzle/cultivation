package com.rfizzle.cultivation.harvest;

import com.rfizzle.cultivation.api.CultivationHarvestCallback;
import com.rfizzle.cultivation.attachment.SoilData;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.soil.EnrichedTilling;
import com.rfizzle.cultivation.soil.SoilMath;
import com.rfizzle.cultivation.soil.SupportedCrops;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The harvest choke point ({@code design/SPEC.md} §1, {@code AGENTS.md}): every
 * mature-crop destruction that resolves drops — player, piston, water,
 * explosion, and every future harvest path — flows through
 * {@link #onDropsResolved}, wired in by the drop-resolution mixins. Never add a
 * second drop path.
 *
 * <p>Order is contractual: drain → exhausted clamp → enriched/Fertilizer
 * bonuses (SPEC §5/§6, appended between the clamp and the callback) →
 * {@link CultivationHarvestCallback}.
 */
public final class HarvestHandler {
    private HarvestHandler() {
    }

    /**
     * Runs the choke point over freshly resolved drops and returns the (possibly
     * clamped and listener-mutated) list. The list vanilla's loot resolution
     * hands over is a fresh mutable list per call; it is mutated in place.
     */
    public static List<ItemStack> onDropsResolved(
            BlockState state, ServerLevel level, BlockPos pos, @Nullable Entity harvester, List<ItemStack> drops) {
        SupportedCrops.CropProfile profile = SupportedCrops.matureProfile(state);
        if (profile == null) {
            return drops;
        }
        BlockPos soilPos = pos.below();
        if (!level.getBlockState(soilPos).is(Blocks.FARMLAND)) {
            return drops;
        }

        CultivationConfig config = CultivationConfig.get();
        boolean exhausted = false;
        if (config.enableSoilFertility) {
            ResourceLocation cropId = profile.cropId();
            SoilStores.update(level, soilPos, true, data -> {
                boolean sameCrop = data.lastCrop().map(cropId::equals).orElse(false);
                float drained = data.fertility()
                        - SoilMath.drainAmount(sameCrop, config.harvestDrain, config.rotationDrainMultiplier);
                return data.withFertility(drained).withLastCrop(cropId);
            });
            // The clamp keys on post-drain fertility: the harvest that lands the
            // soil on 0 is already the exhausted one (SPEC §1's 33-harvest count).
            exhausted = SoilStores.fertilityAt(level, soilPos) <= 0.0F;
            if (exhausted) {
                YieldClamp.clampToBareMinimum(drops, profile.product(), profile.seed());
            }
        }

        // Enriched bonus (SPEC §5): rolled after the clamp so exhausted ground
        // suppresses it, appended after vanilla loot so Fortune applied first.
        if (config.enableEnrichedTilling && !exhausted) {
            SoilData soil = SoilStores.peek(level, soilPos);
            int chance = soil == null ? 0 : soil.enrichedChance();
            if (chance > 0 && EnrichedTilling.grantsBonus(chance, level.getRandom().nextInt(100))) {
                drops.add(new ItemStack(profile.product()));
            }
        }

        CultivationHarvestCallback.EVENT.invoker().onHarvest(level, pos, state, drops, harvester);
        return drops;
    }
}
