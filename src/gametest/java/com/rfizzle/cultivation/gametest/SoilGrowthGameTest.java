package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.soil.SoilGrowth;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import static com.rfizzle.cultivation.gametest.SoilFixtures.CROP;
import static com.rfizzle.cultivation.gametest.SoilFixtures.FARM;
import static com.rfizzle.cultivation.gametest.SoilFixtures.TEMPLATE;
import static com.rfizzle.cultivation.gametest.SoilFixtures.placeTrackedFarmland;

/**
 * SPEC §1's growth-speed modifier, asserted on the computed multiplier the
 * crop/stem/pitcher randomTick mixins apply to the vanilla growth speed —
 * deterministic, no growth statistics.
 */
public class SoilGrowthGameTest implements FabricGameTest {

    @GameTest(template = TEMPLATE)
    public void bandsModifyComputedGrowthSpeed(GameTestHelper helper) {
        helper.setBlock(CROP, Blocks.WHEAT.defaultBlockState());
        var level = helper.getLevel();
        var cropAbs = helper.absolutePos(CROP);

        placeTrackedFarmland(helper, FARM, 80.0F, Blocks.WHEAT);
        helper.assertTrue(SoilGrowth.multiplierAt(level, cropAbs) == 1.0F, "Rich soil grows at the vanilla rate");

        placeTrackedFarmland(helper, FARM, 25.0F, Blocks.WHEAT);
        helper.assertTrue(SoilGrowth.multiplierAt(level, cropAbs) == 1.0F, "exactly the Tired threshold is Fair");

        placeTrackedFarmland(helper, FARM, 10.0F, Blocks.WHEAT);
        helper.assertTrue(SoilGrowth.multiplierAt(level, cropAbs) == 0.75F, "Tired soil grows at 0.75x");

        placeTrackedFarmland(helper, FARM, 0.0F, Blocks.WHEAT);
        helper.assertTrue(SoilGrowth.multiplierAt(level, cropAbs) == 0.5F, "Exhausted soil grows at 0.5x");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void untrackedFarmlandGrowsVanilla(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.FARMLAND);
        helper.setBlock(CROP, Blocks.WHEAT.defaultBlockState());
        helper.assertTrue(SoilGrowth.multiplierAt(helper.getLevel(), helper.absolutePos(CROP)) == 1.0F,
                "pristine ground must be bit-identical to vanilla growth");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void stemsReceiveTheModifier(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 10.0F, Blocks.WHEAT);
        helper.setBlock(CROP, Blocks.MELON_STEM.defaultBlockState().setValue(BlockStateProperties.AGE_7, 3));
        helper.assertTrue(SoilGrowth.multiplierAt(helper.getLevel(), helper.absolutePos(CROP)) == 0.75F,
                "stems over tired soil grow at 0.75x even though they never drain");
        helper.succeed();
    }
}
