package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.attachment.SoilData;
import com.rfizzle.cultivation.attachment.SoilStores;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Shared scaffolding for the soil gametests. The canonical layout puts the
 * farmland at {@link #FARM} with the crop directly above at {@link #CROP},
 * inside the 7x4x7 empty template.
 */
final class SoilFixtures {
    static final String TEMPLATE = "cultivation:empty_7x4x7";
    static final BlockPos FARM = new BlockPos(3, 1, 3);
    static final BlockPos CROP = new BlockPos(3, 2, 3);

    private SoilFixtures() {
    }

    static BlockState matureWheat() {
        return Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE);
    }

    static BlockState matureCarrots() {
        return Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE);
    }

    /** Nether wart at its harvest age (the break drains). */
    static BlockState matureWart() {
        return Blocks.NETHER_WART.defaultBlockState().setValue(BlockStateProperties.AGE_3, 3);
    }

    /** A sweet berry bush at {@code age} (pickable from age 2). */
    static BlockState berryBush(int age) {
        return Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(BlockStateProperties.AGE_3, age);
    }

    static ResourceLocation idOf(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    /** The 3×3 of farmland centered on {@link #FARM}, empty above. */
    static void placeFarmlandGrid(GameTestHelper helper) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                helper.setBlock(FARM.offset(dx, 0, dz), Blocks.FARMLAND);
            }
        }
    }

    /** Places farmland at {@code rel} and pins its tracked fertility. */
    static void placeTrackedFarmland(GameTestHelper helper, BlockPos rel, float fertility, Block lastCrop) {
        placeTrackedGround(helper, rel, Blocks.FARMLAND, fertility, lastCrop);
    }

    /** Places an arbitrary soil ground ({@code ground}) at {@code rel} and pins its tracked fertility. */
    static void placeTrackedGround(GameTestHelper helper, BlockPos rel, Block ground, float fertility, Block lastCrop) {
        helper.setBlock(rel, ground);
        SoilStores.update(helper.getLevel(), helper.absolutePos(rel), false,
                data -> data.withFertility(fertility).withLastCrop(idOf(lastCrop)));
    }

    static float fertility(GameTestHelper helper, BlockPos rel) {
        return SoilStores.fertilityAt(helper.getLevel(), helper.absolutePos(rel));
    }

    static SoilData data(GameTestHelper helper, BlockPos rel) {
        return SoilStores.peek(helper.getLevel(), helper.absolutePos(rel));
    }

    static void assertFertility(GameTestHelper helper, BlockPos rel, float expected, String message) {
        float actual = fertility(helper, rel);
        helper.assertTrue(Math.abs(actual - expected) < 1e-4,
                message + " (expected fertility " + expected + ", got " + actual + ")");
    }
}
