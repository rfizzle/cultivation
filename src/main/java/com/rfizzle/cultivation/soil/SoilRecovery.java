package com.rfizzle.cultivation.soil;

import com.rfizzle.cultivation.attachment.SoilData;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.config.CultivationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * The live recovery path ({@code design/SPEC.md} §1): each random tick on
 * tracked fallow farmland restores {@code fallowRecoveryPerRandomTick}
 * fertility, multiplied by {@code rainRecoveryMultiplier} while rain falls on
 * the block. Every random tick on a tracked position — fallow, cropped, or
 * just reverted — advances the recovery clock, which is what keeps farmland
 * and crop-occupied spans out of the lazy path's accrual.
 */
public final class SoilRecovery {
    private SoilRecovery() {
    }

    /** Called from the FarmBlock randomTick mixin, after vanilla's moisture handling. */
    public static void onFarmlandRandomTick(ServerLevel level, BlockPos pos) {
        CultivationConfig config = CultivationConfig.get();
        if (!config.enableSoilFertility) {
            return;
        }
        SoilData data = SoilStores.peek(level, pos);
        if (data == null) {
            return; // pristine ground stays zero-data
        }
        long now = SoilClockState.get(level).time();
        // Re-read the world: vanilla's randomTick may have reverted the block to
        // dirt earlier in this same call, and the injected state argument is stale.
        boolean fallow = level.getBlockState(pos).is(Blocks.FARMLAND)
                && !SupportedCrops.isOccupying(level.getBlockState(pos.above()));
        float gain;
        if (fallow) {
            double multiplier = level.isRainingAt(pos.above()) ? config.rainRecoveryMultiplier : 1.0;
            gain = (float) (config.fallowRecoveryPerRandomTick * multiplier);
        } else {
            gain = 0.0F;
        }
        SoilStores.update(level, pos, false,
                current -> current.withFertility(current.fertility() + gain).withRecoveryCheck(now));
    }
}
