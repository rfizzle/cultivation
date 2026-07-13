package com.rfizzle.cultivation.soil;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * The supported-crop table from {@code design/SPEC.md} §1: which block states
 * count as a mature harvest, the crop identity recorded as rotation memory,
 * and each crop's primary product and seed for the exhausted yield clamp.
 *
 * <p>Melon and pumpkin stems receive the growth modifier but are deliberately
 * absent here — they never drain and never receive yield bonuses. Two
 * second-wave crops grow on non-farmland ground — nether wart on soul sand and
 * the sweet berry bush on dirt (SPEC §1). They live in the drain registry
 * ({@link #soilProfile}) but never in the replant registry ({@link
 * #matureProfile}), so the harvest choke point tires their ground while the
 * scythe and bare-hand right-click leave a standing bush or wart alone.
 */
public final class SupportedCrops {
    /** {@code cropId} is the plantable crop block's id — the rotation-memory identity. */
    public record CropProfile(ResourceLocation cropId, Item product, Item seed) {
    }

    /**
     * The sweet berry bush yields berries — and drains its soil — on any pick
     * from age 2 up (vanilla {@code useWithoutItem}), so its soil "mature" band
     * starts one age below {@code MAX_AGE}, unlike the once-at-max farmland crops.
     */
    private static final int PICKABLE_BERRY_AGE = 2;

    private static final CropProfile WHEAT = profile(Blocks.WHEAT, Items.WHEAT, Items.WHEAT_SEEDS);
    private static final CropProfile CARROTS = profile(Blocks.CARROTS, Items.CARROT, Items.CARROT);
    private static final CropProfile POTATOES = profile(Blocks.POTATOES, Items.POTATO, Items.POTATO);
    private static final CropProfile BEETROOTS = profile(Blocks.BEETROOTS, Items.BEETROOT, Items.BEETROOT_SEEDS);
    private static final CropProfile TORCHFLOWER = profile(Blocks.TORCHFLOWER_CROP, Items.TORCHFLOWER, Items.TORCHFLOWER_SEEDS);
    private static final CropProfile PITCHER = profile(Blocks.PITCHER_CROP, Items.PITCHER_PLANT, Items.PITCHER_POD);
    private static final CropProfile NETHER_WART = profile(Blocks.NETHER_WART, Items.NETHER_WART, Items.NETHER_WART);
    private static final CropProfile SWEET_BERRIES = profile(Blocks.SWEET_BERRY_BUSH, Items.SWEET_BERRIES, Items.SWEET_BERRIES);

    private SupportedCrops() {
    }

    /**
     * The profile when {@code state} is a supported crop in its mature,
     * drops-carrying form, else null. The torchflower crop matures by becoming
     * the {@code minecraft:torchflower} flower block, so the flower is the
     * harvestable form. The pitcher crop counts only as its lower half — the
     * loot table conditions every drop on {@code half=lower}, so whichever half
     * is broken first, the lower half's resolution is the one
     * destruction-with-drops event (an upper-half break destroys the orphaned
     * lower half with drops via {@code updateOrDestroy}) and the farmland is
     * uniformly the block below.
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
     * The drain registry: every crop whose harvest tires the soil below it — the
     * {@link #matureProfile replant crops} plus the two second-wave crops. Nether
     * wart counts at {@code MAX_AGE} (its harvest is the break); the sweet berry
     * bush counts from {@link #PICKABLE_BERRY_AGE} up (its harvest is the pick,
     * available before the bush is fully grown). This is what the harvest choke
     * point looks up; {@link #matureProfile} stays the narrower set the scythe and
     * right-click harvest reap-and-replant.
     */
    @Nullable
    public static CropProfile soilProfile(BlockState state) {
        CropProfile replantable = matureProfile(state);
        if (replantable != null) {
            return replantable;
        }
        Block block = state.getBlock();
        if (block == Blocks.NETHER_WART) {
            return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE ? NETHER_WART : null;
        }
        if (block == Blocks.SWEET_BERRY_BUSH) {
            return state.getValue(SweetBerryBushBlock.AGE) >= PICKABLE_BERRY_AGE ? SWEET_BERRIES : null;
        }
        return null;
    }

    /**
     * Whether {@code ground} is a position soil tracks for the {@code crop} above
     * it — farmland always, or a second-wave crop's own ground: soul sand under
     * nether wart, dirt (the {@link BlockTags#DIRT} family) under a sweet berry
     * bush. {@code includeSecondWave} is the server's {@code enableNonFarmlandSoil}
     * toggle: farmland tracks regardless, but with the toggle off the two
     * second-wave grounds behave exactly like vanilla. Pure — the toggle rides in
     * as a parameter so callers keep it config-free and this stays unit-testable.
     */
    public static boolean isTrackedSoilGround(BlockState ground, BlockState crop, boolean includeSecondWave) {
        if (ground.is(Blocks.FARMLAND)) {
            return true;
        }
        if (!includeSecondWave) {
            return false;
        }
        Block cropBlock = crop.getBlock();
        if (cropBlock == Blocks.NETHER_WART) {
            return ground.is(Blocks.SOUL_SAND);
        }
        if (cropBlock == Blocks.SWEET_BERRY_BUSH) {
            return ground.is(BlockTags.DIRT);
        }
        return false;
    }

    /** Second-wave tracking on (the {@code enableNonFarmlandSoil} default) — for tests and the drain identity. */
    public static boolean isTrackedSoilGround(BlockState ground, BlockState crop) {
        return isTrackedSoilGround(ground, crop, true);
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

    /**
     * The crop identity a seed plants — the {@code cropId} to compare against a
     * block's {@link com.rfizzle.cultivation.attachment.SoilData#lastCrop()
     * rotation memory} (SPEC §8). Every plantable seed the farmer handles is a
     * {@link BlockItem} (wheat/beetroot/torchflower seeds and the pitcher pod
     * name their crop block; the carrot and potato items are their own crop's
     * {@code ItemNameBlockItem}), so the seed's block id is exactly the id the
     * harvest choke point records for that crop. Null for anything that is not a
     * block-placing item.
     */
    @Nullable
    public static ResourceLocation cropIdForSeed(ItemStack seed) {
        return seed.getItem() instanceof BlockItem blockItem
                ? BuiltInRegistries.BLOCK.getKey(blockItem.getBlock())
                : null;
    }

    @Nullable
    private static CropProfile mature(BlockState state, CropProfile profile) {
        return ((CropBlock) state.getBlock()).isMaxAge(state) ? profile : null;
    }

    private static CropProfile profile(Block cropBlock, Item product, Item seed) {
        return new CropProfile(BuiltInRegistries.BLOCK.getKey(cropBlock), product, seed);
    }
}
