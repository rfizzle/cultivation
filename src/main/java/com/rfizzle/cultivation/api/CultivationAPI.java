package com.rfizzle.cultivation.api;

import com.rfizzle.cultivation.attachment.DietStore;
import com.rfizzle.cultivation.attachment.SoilData;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.soil.SoilMath;
import com.rfizzle.cultivation.soil.SupportedCrops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Cultivation's read-only static facade (Concord API Standard). All reads are
 * server-authoritative and never mutate soil state — outside mods observe
 * fertility and react; they don't write it. The one sanctioned mutation point
 * is {@link CultivationHarvestCallback}'s drops list.
 */
@Stable
public final class CultivationAPI {
    private CultivationAPI() {
    }

    /**
     * The block's fertility, 0–100. Untracked soil is pristine {@code 100}; a block
     * that is not soil returns {@code -1}. Soil is farmland, plus a second-wave
     * crop's ground — soul sand under nether wart, dirt under a sweet berry bush —
     * while {@code enableNonFarmlandSoil} is on.
     */
    public static float getFertility(ServerLevel level, BlockPos pos) {
        if (!isTrackedSoil(level, pos)) {
            return -1.0F;
        }
        return SoilStores.fertilityAt(level, pos);
    }

    /** The block's full soil snapshot; empty if the block is not soil (see {@link #getFertility}). */
    public static Optional<SoilInfo> getSoilInfo(ServerLevel level, BlockPos pos) {
        if (!isTrackedSoil(level, pos)) {
            return Optional.empty();
        }
        SoilData data = SoilStores.peek(level, pos);
        if (data == null) {
            return Optional.of(new SoilInfo(SoilMath.MAX_FERTILITY, 0, 0, Optional.empty()));
        }
        return Optional.of(new SoilInfo(
                data.fertility(), data.enrichedChance(), data.fertilizerRemaining(), data.lastCrop()));
    }

    /** Whether {@code pos} is a soil position — farmland, or a second-wave crop's ground beneath it. */
    private static boolean isTrackedSoil(ServerLevel level, BlockPos pos) {
        return SupportedCrops.isTrackedSoilGround(
                level.getBlockState(pos), level.getBlockState(pos.above()),
                CultivationConfig.get().enableNonFarmlandSoil);
    }

    /**
     * The dietary-fatigue multiplier the player's next eat of {@code stack} would
     * receive, in {@code [fatigueFloor, 1.0]} (the floor is configurable, so the
     * lower bound follows the server's {@code fatigueFloor}). Returns {@code 1.0}
     * when dietary fatigue is disabled. Cake stacks key to {@code minecraft:cake}.
     */
    public static float getFoodEffectiveness(ServerPlayer player, ItemStack stack) {
        CultivationConfig config = CultivationConfig.get();
        if (!config.enableDietaryFatigue) {
            return 1.0F;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return (float) DietStore.get(player).effectiveness(id, config.fatiguePerRepeat, config.fatigueFloor);
    }
}
