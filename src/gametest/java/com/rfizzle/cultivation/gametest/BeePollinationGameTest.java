package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.soil.SoilGrowth;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

import static com.rfizzle.cultivation.gametest.SoilFixtures.CROP;
import static com.rfizzle.cultivation.gametest.SoilFixtures.TEMPLATE;

/**
 * SPEC §2's bee-pollination bonus, asserted on the computed growth multiplier
 * the growth mixins apply — deterministic, no growth statistics. Gate math lives
 * in the {@code BeePollinationTest} unit suite; these tests cover the in-world
 * wiring: the POI hive lookup, the occupancy check, the range gate, the config
 * gate, and the multiplicative stack with polyculture.
 */
public class BeePollinationGameTest implements FabricGameTest {
    private static final BlockPos HIVE = new BlockPos(1, 2, 3);

    private static void plant(GameTestHelper helper, BlockPos rel, Block crop) {
        helper.setBlock(rel.below(), Blocks.FARMLAND);
        helper.setBlock(rel, crop.defaultBlockState());
    }

    /** Places a beehive at {@code rel}, optionally seeding it with one resident bee. */
    private static void placeHive(GameTestHelper helper, BlockPos rel, boolean occupied) {
        helper.setBlock(rel, Blocks.BEEHIVE);
        if (occupied && helper.getBlockEntity(rel) instanceof BeehiveBlockEntity hive) {
            hive.storeBee(BeehiveBlockEntity.Occupant.create(600));
        }
    }

    private static float multiplier(GameTestHelper helper) {
        var level = helper.getLevel();
        var cropAbs = helper.absolutePos(CROP);
        return SoilGrowth.multiplierAt(level, cropAbs, level.getBlockState(cropAbs));
    }

    private static void assertMultiplier(GameTestHelper helper, float expected, String message) {
        float actual = multiplier(helper);
        helper.assertTrue(Math.abs(actual - expected) < 1e-4,
                message + " (expected multiplier " + expected + ", got " + actual + ")");
    }

    @GameTest(template = TEMPLATE)
    public void noHiveGrowsAtTheVanillaRate(GameTestHelper helper) {
        plant(helper, CROP, Blocks.WHEAT);
        assertMultiplier(helper, 1.0F, "a crop with no hive nearby grows at the vanilla rate");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void populatedHiveInRangeBoostsGrowth(GameTestHelper helper) {
        plant(helper, CROP, Blocks.WHEAT);
        placeHive(helper, HIVE, true);
        assertMultiplier(helper, 1.1F, "a populated hive in range earns the bee bonus");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void emptyHiveGrantsNoBonus(GameTestHelper helper) {
        plant(helper, CROP, Blocks.WHEAT);
        placeHive(helper, HIVE, false);
        assertMultiplier(helper, 1.0F, "a hive with no resident bees grants nothing");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void hiveBeyondRangeGrantsNoBonus(GameTestHelper helper) {
        CultivationConfig config = CultivationConfig.get();
        int savedRange = config.beePollinationRange;
        config.beePollinationRange = 1;
        try {
            plant(helper, CROP, Blocks.WHEAT);
            placeHive(helper, HIVE, true); // two blocks from the crop, outside a range of one
            assertMultiplier(helper, 1.0F, "a populated hive beyond range grants nothing");
        } finally {
            config.beePollinationRange = savedRange;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void disabledBeePollinationIsInert(GameTestHelper helper) {
        CultivationConfig config = CultivationConfig.get();
        boolean saved = config.enableBeePollination;
        config.enableBeePollination = false;
        try {
            plant(helper, CROP, Blocks.WHEAT);
            placeHive(helper, HIVE, true);
            assertMultiplier(helper, 1.0F, "the bonus must be inert while disabled");
        } finally {
            config.enableBeePollination = saved;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void bonusStacksMultiplicativelyWithPolyculture(GameTestHelper helper) {
        plant(helper, CROP, Blocks.WHEAT);
        plant(helper, CROP.west(), Blocks.CARROTS);
        plant(helper, CROP.east(), Blocks.POTATOES);
        placeHive(helper, HIVE, true);
        assertMultiplier(helper, 1.32F, "a polyculture crop near a hive grows at 1.2 x 1.1");
        helper.succeed();
    }
}
