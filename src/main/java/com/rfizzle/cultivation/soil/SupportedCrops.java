package com.rfizzle.cultivation.soil;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * The supported-crop table from {@code design/SPEC.md} §1: which block states
 * count as a mature harvest, the crop identity recorded as rotation memory,
 * and each crop's primary product and seed for the exhausted yield clamp.
 *
 * <p>Melon and pumpkin stems receive the growth modifier but are deliberately
 * absent here — they never drain and never receive yield bonuses. Crops not
 * grown on farmland are out of scope entirely (the harvest seam additionally
 * requires farmland directly below).
 */
public final class SupportedCrops {
    /** {@code cropId} is the plantable crop block's id — the rotation-memory identity. */
    public record CropProfile(ResourceLocation cropId, Item product, Item seed) {
    }

    private static final CropProfile WHEAT = profile(Blocks.WHEAT, Items.WHEAT, Items.WHEAT_SEEDS);
    private static final CropProfile CARROTS = profile(Blocks.CARROTS, Items.CARROT, Items.CARROT);
    private static final CropProfile POTATOES = profile(Blocks.POTATOES, Items.POTATO, Items.POTATO);
    private static final CropProfile BEETROOTS = profile(Blocks.BEETROOTS, Items.BEETROOT, Items.BEETROOT_SEEDS);
    private static final CropProfile TORCHFLOWER = profile(Blocks.TORCHFLOWER_CROP, Items.TORCHFLOWER, Items.TORCHFLOWER_SEEDS);
    private static final CropProfile PITCHER = profile(Blocks.PITCHER_CROP, Items.PITCHER_PLANT, Items.PITCHER_POD);

    private SupportedCrops() {
    }

    /**
     * The profile when {@code state} is a supported crop in its mature,
     * drops-carrying form, else null. The torchflower crop matures by becoming
     * the {@code minecraft:torchflower} flower block, so the flower is the
     * harvestable form. The pitcher crop counts only as its lower half — the
     * loot table conditions every drop on {@code half=lower}, so the lower
     * half's resolution is the one destruction-with-drops event and the
     * farmland is uniformly the block below.
     */
    @Nullable
    public static CropProfile matureProfile(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.WHEAT) {
            return mature(state, WHEAT);
        }
        if (block == Blocks.CARROTS) {
            return mature(state, CARROTS);
        }
        if (block == Blocks.POTATOES) {
            return mature(state, POTATOES);
        }
        if (block == Blocks.BEETROOTS) {
            return mature(state, BEETROOTS);
        }
        if (block == Blocks.TORCHFLOWER) {
            return TORCHFLOWER;
        }
        if (block == Blocks.PITCHER_CROP
                && state.getValue(PitcherCropBlock.AGE) == PitcherCropBlock.MAX_AGE
                && state.getValue(PitcherCropBlock.HALF) == DoubleBlockHalf.LOWER) {
            return PITCHER;
        }
        return null;
    }

    /**
     * Whether {@code state} occupies farmland as a growing/grown crop — the
     * inverse of fallow. Includes stems (they hold the ground even though they
     * never drain) and the torchflower's mature flower form.
     */
    public static boolean isOccupying(BlockState state) {
        Block block = state.getBlock();
        return block instanceof CropBlock
                || block instanceof StemBlock
                || block instanceof AttachedStemBlock
                || block instanceof PitcherCropBlock
                || state.is(Blocks.TORCHFLOWER);
    }

    @Nullable
    private static CropProfile mature(BlockState state, CropProfile profile) {
        return ((CropBlock) state.getBlock()).isMaxAge(state) ? profile : null;
    }

    private static CropProfile profile(Block cropBlock, Item product, Item seed) {
        return new CropProfile(BuiltInRegistries.BLOCK.getKey(cropBlock), product, seed);
    }
}
