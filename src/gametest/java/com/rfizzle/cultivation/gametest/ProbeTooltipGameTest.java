package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.compat.common.CropProbeTooltip;
import com.rfizzle.cultivation.compat.common.FarmlandProbeTooltip;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;

import static com.rfizzle.cultivation.gametest.SoilFixtures.CROP;
import static com.rfizzle.cultivation.gametest.SoilFixtures.FARM;
import static com.rfizzle.cultivation.gametest.SoilFixtures.TEMPLATE;
import static com.rfizzle.cultivation.gametest.SoilFixtures.placeTrackedFarmland;

/**
 * Drives the Jade/WTHIT shared writers against real world state — the wiring the
 * Tier-1 formatter tests can't reach. Both writers take resolved game objects
 * ({@code ServerLevel}, {@code BlockPos}, {@code BlockState}), so the adapters
 * over them stay too thin to hide a bug.
 */
public class ProbeTooltipGameTest implements FabricGameTest {

    @GameTest(template = TEMPLATE)
    public void farmlandWriterProducesTooltipLines(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 40.0F, Blocks.WHEAT);
        CompoundTag tag = new CompoundTag();
        FarmlandProbeTooltip.writeServerData(tag, helper.getLevel(), helper.absolutePos(FARM));
        helper.assertTrue(!FarmlandProbeTooltip.buildLines(tag).isEmpty(),
                "looking at tracked farmland yields soil tooltip lines");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void nonFarmlandWritesNothing(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.STONE);
        CompoundTag tag = new CompoundTag();
        FarmlandProbeTooltip.writeServerData(tag, helper.getLevel(), helper.absolutePos(FARM));
        helper.assertTrue(FarmlandProbeTooltip.buildLines(tag).isEmpty(),
                "a non-farmland block leaves the soil tooltip empty");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void cropWriterProducesGrowthLine(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 10.0F, Blocks.WHEAT);
        helper.setBlock(CROP, Blocks.WHEAT.defaultBlockState());
        CompoundTag tag = new CompoundTag();
        var level = helper.getLevel();
        var cropAbs = helper.absolutePos(CROP);
        CropProbeTooltip.writeServerData(tag, level, cropAbs, level.getBlockState(cropAbs));
        helper.assertTrue(!CropProbeTooltip.buildLines(tag).isEmpty(),
                "looking at a supported crop yields a growth tooltip line");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void nonCropWritesNothing(GameTestHelper helper) {
        helper.setBlock(CROP, Blocks.STONE);
        CompoundTag tag = new CompoundTag();
        var level = helper.getLevel();
        var cropAbs = helper.absolutePos(CROP);
        CropProbeTooltip.writeServerData(tag, level, cropAbs, level.getBlockState(cropAbs));
        helper.assertTrue(CropProbeTooltip.buildLines(tag).isEmpty(),
                "a non-crop block leaves the crop tooltip empty");
        helper.succeed();
    }
}
