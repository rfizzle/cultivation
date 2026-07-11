package com.rfizzle.cultivation.soil;

import com.rfizzle.cultivation.config.CultivationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The polyculture growth bonus ({@code design/SPEC.md} §2): a crop or stem
 * whose four cardinal same-Y neighbors include at least
 * {@code polycultureMinDifferentNeighbors} crops of a different identity grows
 * at {@code polycultureGrowthMultiplier}. Evaluated live at each growth roll —
 * four block reads, no stored state, no sync. Monoculture is never penalized;
 * the multiplier is always ≥ 1.0.
 */
public final class Polyculture {
    private Polyculture() {
    }

    /**
     * The polyculture multiplier for the growth roll of {@code self} at
     * {@code pos}. Exactly 1.0 when the feature is disabled, the ticking block
     * has no crop identity, or the field around it is too uniform.
     */
    public static float multiplierAt(ServerLevel level, BlockPos pos, BlockState self) {
        CultivationConfig config = CultivationConfig.get();
        if (!config.enablePolyculture) {
            return 1.0F;
        }
        ResourceLocation selfId = cropIdentity(self);
        if (selfId == null) {
            return 1.0F;
        }
        int different = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            ResourceLocation neighbor = cropIdentity(level.getBlockState(pos.relative(direction)));
            if (neighbor != null && !neighbor.equals(selfId)) {
                different++;
            }
        }
        return multiplier(different, config.polycultureMinDifferentNeighbors, config.polycultureGrowthMultiplier);
    }

    /** Pure threshold math: the configured multiplier at or above the neighbor count, else 1.0. */
    public static float multiplier(int differentNeighbors, int minRequired, double configMultiplier) {
        return differentNeighbors >= minRequired ? (float) configMultiplier : 1.0F;
    }

    /** How many of {@code neighbors} carry a crop identity different from {@code selfId}. */
    public static int countDifferent(ResourceLocation selfId, BlockState... neighbors) {
        int count = 0;
        for (BlockState neighbor : neighbors) {
            ResourceLocation id = cropIdentity(neighbor);
            if (id != null && !id.equals(selfId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * The crop identity {@code state} contributes to neighbor comparison, or
     * null when it is not a crop. Identity survives maturity: an attached stem
     * keeps its base stem's id (melon and pumpkin stay two distinct crops) and
     * the mature torchflower — the one crop whose maturity changes its block
     * id — keeps {@code torchflower_crop}. Vanilla's attached stems hold their
     * base-stem link in a private field, so the two blocks are mapped by
     * identity; a modded {@link AttachedStemBlock} falls back to its own id.
     */
    @Nullable
    public static ResourceLocation cropIdentity(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.ATTACHED_MELON_STEM) {
            return id(Blocks.MELON_STEM);
        }
        if (block == Blocks.ATTACHED_PUMPKIN_STEM) {
            return id(Blocks.PUMPKIN_STEM);
        }
        if (state.is(Blocks.TORCHFLOWER)) {
            return id(Blocks.TORCHFLOWER_CROP);
        }
        if (block instanceof CropBlock
                || block instanceof StemBlock
                || block instanceof AttachedStemBlock
                || block instanceof PitcherCropBlock) {
            return id(block);
        }
        return null;
    }

    private static ResourceLocation id(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }
}
