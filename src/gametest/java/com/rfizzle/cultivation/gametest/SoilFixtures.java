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

    static ResourceLocation idOf(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    /** Places farmland at {@code rel} and pins its tracked fertility. */
    static void placeTrackedFarmland(GameTestHelper helper, BlockPos rel, float fertility, Block lastCrop) {
        helper.setBlock(rel, Blocks.FARMLAND);
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
