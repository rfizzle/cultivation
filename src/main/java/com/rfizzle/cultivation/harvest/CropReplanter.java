package com.rfizzle.cultivation.harvest;

import com.rfizzle.cultivation.soil.SupportedCrops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The shared replant seam ({@code design/SPEC.md} §7): a harvested crop is reset
 * to age 0 from one seed withdrawn from its own drops
 * ({@link SeedWithdrawal#withdrawOne}). Both manual-harvest gestures — the
 * scythe's 3×3 sweep and the bare-hand right-click — reap through the one
 * {@link HarvestHandler} choke point and then replant here, so the age-0 reset,
 * the pitcher's two-half handling, and the no-seed fallback stay a single
 * behavior neither caller can drift from.
 */
public final class CropReplanter {
    // Clear/replant without letting the two pitcher halves react to each other or
    // to a stale neighbor shape — the harvest has already resolved every drop.
    private static final int REPLANT_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private CropReplanter() {
    }

    /**
     * Replants the harvested crop at {@code pos}. A pitcher always reseeds a pod
     * (clearing its orphaned upper half); any other crop reseeds at age 0 only
     * when {@code seedFound}, else the block is left empty with the farmland
     * intact.
     */
    public static void replant(ServerLevel level, BlockPos pos, BlockState state,
            SupportedCrops.CropProfile profile, boolean seedFound) {
        if (state.getBlock() instanceof PitcherCropBlock) {
            // §7: the pitcher harvests both halves and replants a pod at age 0.
            // Its mature drop is the plant, never a pod, so the replant is
            // unconditional; the orphaned upper half must be cleared explicitly.
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), REPLANT_FLAGS);
            level.setBlock(pos, Blocks.PITCHER_CROP.defaultBlockState(), REPLANT_FLAGS);
            return;
        }
        if (seedFound && BuiltInRegistries.BLOCK.get(profile.cropId()) instanceof CropBlock crop) {
            level.setBlock(pos, crop.getStateForAge(0), REPLANT_FLAGS);
        } else {
            // No seed to sow (an unlucky roll, or a crop whose mature drop is not
            // its seed): the block is left empty, farmland intact.
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), REPLANT_FLAGS);
        }
    }
}
