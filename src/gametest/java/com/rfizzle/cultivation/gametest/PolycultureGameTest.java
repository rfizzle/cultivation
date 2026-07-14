package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.soil.SoilGrowth;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import static com.rfizzle.cultivation.gametest.SoilFixtures.CROP;
import static com.rfizzle.cultivation.gametest.SoilFixtures.FARM;
import static com.rfizzle.cultivation.gametest.SoilFixtures.TEMPLATE;
import static com.rfizzle.cultivation.gametest.SoilFixtures.placeTrackedFarmland;

/**
 * SPEC §2's polyculture bonus, asserted on the computed multiplier the growth
 * mixins apply — deterministic, no growth statistics. Layout counting detail
 * lives in the {@code PolycultureTest} unit suite; these tests cover the
 * in-world wiring: neighbor reads, config gates, and the fertility stack.
 */
public class PolycultureGameTest implements FabricGameTest {
    private static final BlockPos WEST = CROP.west();
    private static final BlockPos EAST = CROP.east();

    /** Plants {@code crop} at {@code rel} with fresh farmland below it. */
    private static void plant(GameTestHelper helper, BlockPos rel, Block crop) {
        helper.setBlock(rel.below(), Blocks.FARMLAND);
        helper.setBlock(rel, crop.defaultBlockState());
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
    public void bonusNeedsTwoDifferentNeighbors(GameTestHelper helper) {
        plant(helper, CROP, Blocks.WHEAT);

        assertMultiplier(helper, 1.0F, "a lone crop grows at the vanilla rate");

        plant(helper, WEST, Blocks.CARROTS);
        assertMultiplier(helper, 1.0F, "one different neighbor is below the default threshold");

        plant(helper, EAST, Blocks.POTATOES);
        assertMultiplier(helper, 1.2F, "two different neighbors earn the bonus");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void monocultureGrowsAtTheVanillaRate(GameTestHelper helper) {
        plant(helper, CROP, Blocks.WHEAT);
        plant(helper, CROP.north(), Blocks.WHEAT);
        plant(helper, CROP.south(), Blocks.WHEAT);
        plant(helper, WEST, Blocks.WHEAT);
        plant(helper, EAST, Blocks.WHEAT);
        assertMultiplier(helper, 1.0F, "monoculture is never penalized and never boosted");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void bonusStacksMultiplicativelyWithFertility(GameTestHelper helper) {
        plant(helper, CROP, Blocks.WHEAT);
        plant(helper, WEST, Blocks.CARROTS);
        plant(helper, EAST, Blocks.POTATOES);
        placeTrackedFarmland(helper, FARM, 0.0F, Blocks.WHEAT);
        assertMultiplier(helper, 0.6F, "an exhausted polyculture block grows at 0.5 x 1.2");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void stemsCountAsTwoDistinctCrops(GameTestHelper helper) {
        plant(helper, CROP, Blocks.MELON_STEM);
        plant(helper, WEST, Blocks.PUMPKIN_STEM);
        plant(helper, EAST, Blocks.WHEAT);
        assertMultiplier(helper, 1.2F, "a melon stem beside a pumpkin stem and wheat qualifies");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void disabledPolycultureIsInert(GameTestHelper helper) {
        CultivationConfig config = CultivationConfig.get();
        boolean saved = config.enablePolyculture;
        config.enablePolyculture = false;
        try {
            plant(helper, CROP, Blocks.WHEAT);
            plant(helper, WEST, Blocks.CARROTS);
            plant(helper, EAST, Blocks.POTATOES);
            assertMultiplier(helper, 1.0F, "the bonus must be inert while disabled");
        } finally {
            config.enablePolyculture = saved;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void configuredThresholdAndMultiplierAreRespected(GameTestHelper helper) {
        CultivationConfig config = CultivationConfig.get();
        int savedMin = config.polycultureMinDifferentNeighbors;
        double savedMultiplier = config.polycultureGrowthMultiplier;
        config.polycultureMinDifferentNeighbors = 3;
        config.polycultureGrowthMultiplier = 1.5;
        try {
            plant(helper, CROP, Blocks.WHEAT);
            plant(helper, WEST, Blocks.CARROTS);
            plant(helper, EAST, Blocks.POTATOES);
            assertMultiplier(helper, 1.0F, "two different neighbors miss a threshold of three");

            plant(helper, CROP.north(), Blocks.BEETROOTS);
            assertMultiplier(helper, 1.5F, "three different neighbors earn the configured multiplier");
        } finally {
            config.polycultureMinDifferentNeighbors = savedMin;
            config.polycultureGrowthMultiplier = savedMultiplier;
        }
        helper.succeed();
    }

    // --- sniffer premium (SPEC §2) ---

    @GameTest(template = TEMPLATE)
    public void torchflowerNeighborDoublesTheBonus(GameTestHelper helper) {
        plant(helper, CROP, Blocks.WHEAT);
        plant(helper, WEST, Blocks.TORCHFLOWER_CROP);
        plant(helper, EAST, Blocks.POTATOES);
        assertMultiplier(helper, 1.4F, "a torchflower border doubles the +20% bonus to +40%");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void pitcherNeighborDoublesTheBonus(GameTestHelper helper) {
        plant(helper, CROP, Blocks.WHEAT);
        plant(helper, WEST, Blocks.PITCHER_CROP);
        plant(helper, EAST, Blocks.CARROTS);
        assertMultiplier(helper, 1.4F, "a pitcher crop border doubles the bonus");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void matureTorchflowerCountsAsASnifferNeighbor(GameTestHelper helper) {
        // The mature flower keeps the torchflower_crop identity, so it stays a premium partner.
        plant(helper, CROP, Blocks.WHEAT);
        plant(helper, WEST, Blocks.TORCHFLOWER);
        plant(helper, EAST, Blocks.CARROTS);
        assertMultiplier(helper, 1.4F, "a matured torchflower still doubles the bonus");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void maturePitcherPlantCountsAsASnifferNeighbor(GameTestHelper helper) {
        // The two-tall pitcher plant keeps the pitcher_crop identity — a finished
        // pitcher is still a premium partner, symmetric with the torchflower.
        plant(helper, CROP, Blocks.WHEAT);
        plant(helper, WEST, Blocks.PITCHER_PLANT);
        plant(helper, EAST, Blocks.CARROTS);
        assertMultiplier(helper, 1.4F, "a matured pitcher plant still doubles the bonus");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void snifferPremiumNeedsAQualifyingLayout(GameTestHelper helper) {
        // A lone sniffer neighbor is below the threshold — the premium never conjures a bonus.
        plant(helper, CROP, Blocks.WHEAT);
        plant(helper, WEST, Blocks.TORCHFLOWER_CROP);
        assertMultiplier(helper, 1.0F, "one sniffer neighbor still misses the two-neighbor threshold");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void disabledSnifferPremiumLeavesTheBaseBonus(GameTestHelper helper) {
        CultivationConfig config = CultivationConfig.get();
        boolean saved = config.enableSnifferPolyculture;
        config.enableSnifferPolyculture = false;
        try {
            plant(helper, CROP, Blocks.WHEAT);
            plant(helper, WEST, Blocks.TORCHFLOWER_CROP);
            plant(helper, EAST, Blocks.POTATOES);
            assertMultiplier(helper, 1.2F, "with the premium off, a sniffer border earns only the base bonus");
        } finally {
            config.enableSnifferPolyculture = saved;
        }
        helper.succeed();
    }
}
