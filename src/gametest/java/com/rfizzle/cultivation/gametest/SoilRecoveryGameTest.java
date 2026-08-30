package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.attachment.SoilData;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.soil.SoilClockState;
import com.rfizzle.cultivation.soil.SoilMath;
import com.rfizzle.cultivation.soil.SoilRecovery;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static com.rfizzle.cultivation.gametest.SoilFixtures.CROP;
import static com.rfizzle.cultivation.gametest.SoilFixtures.FARM;
import static com.rfizzle.cultivation.gametest.SoilFixtures.TEMPLATE;
import static com.rfizzle.cultivation.gametest.SoilFixtures.assertFertility;
import static com.rfizzle.cultivation.gametest.SoilFixtures.placeTrackedFarmland;

/**
 * The recovery half of SPEC §1: the live random-tick path, its rain
 * multiplier, the lazy settle on re-till, and the bone meal amendment.
 *
 * <p>Tests that manipulate global weather run in their own {@code
 * cultivationWeather} batch and never reset it; every default-batch assertion
 * is weather-proof (the exact-gain test blocks its own sky with a roof block).
 */
public class SoilRecoveryGameTest implements FabricGameTest {
    /**
     * The weather tests set rain on the whole level, which is state a concurrent
     * sibling in the same batch would read — so they take a batch of their own.
     */
    private static final String WEATHER_BATCH = "cultivationWeather";

    /**
     * Double the 100-tick default. The weather tests set rain on the level and then
     * wait out real random-tick recovery, so they are the slowest in the suite by a
     * margin that has nothing to do with the code under test.
     */
    private static final int WEATHER_TIMEOUT = 200;

    private static final BlockPos ROOF = FARM.above(2);

    @GameTest(template = TEMPLATE)
    public void fallowFarmlandRecoversPerRandomTick(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 50.0F, Blocks.WHEAT);
        helper.setBlock(ROOF, Blocks.SMOOTH_STONE); // blocks the sky: the rain multiplier can never apply
        ServerLevel level = helper.getLevel();
        BlockPos farmAbs = helper.absolutePos(FARM);

        long clockBefore = SoilClockState.get(level).time();
        for (int i = 0; i < 3; i++) {
            SoilRecovery.onFarmlandRandomTick(level, farmAbs);
        }

        assertFertility(helper, FARM, 56.0F, "each fallow random tick must restore 2.0");
        SoilData data = SoilFixtures.data(helper, FARM);
        helper.assertTrue(data != null && data.lastRecoveryCheck() >= clockBefore,
                "the live path must advance the recovery clock");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void croppedFarmlandDoesNotRecoverButAdvancesTheClock(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 50.0F, Blocks.WHEAT);
        helper.setBlock(CROP, Blocks.WHEAT.defaultBlockState());
        ServerLevel level = helper.getLevel();

        SoilData before = SoilFixtures.data(helper, FARM);
        SoilRecovery.onFarmlandRandomTick(level, helper.absolutePos(FARM));

        assertFertility(helper, FARM, 50.0F, "occupied ground is not fallow and must not recover");
        SoilData after = SoilFixtures.data(helper, FARM);
        helper.assertTrue(before != null && after != null
                        && after.lastRecoveryCheck() >= before.lastRecoveryCheck(),
                "every tracked random tick advances the clock, cropped or not");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void fullRecoveryEvictsMemorylessEntries(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.FARMLAND);
        helper.setBlock(ROOF, Blocks.SMOOTH_STONE);
        ServerLevel level = helper.getLevel();
        BlockPos farmAbs = helper.absolutePos(FARM);

        // A tracked entry with rotation memory survives full recovery…
        placeTrackedFarmland(helper, FARM, 99.0F, Blocks.WHEAT);
        SoilRecovery.onFarmlandRandomTick(level, farmAbs);
        assertFertility(helper, FARM, 100.0F, "recovery clamps at 100");
        helper.assertTrue(SoilFixtures.data(helper, FARM) != null,
                "an entry with rotation memory is not all-default and must survive");

        // …but an entry with no memory returns to all-default values and evicts.
        SoilStores.update(level, farmAbs, false, data -> SoilData.pristine(data.lastRecoveryCheck()).withFertility(99.0F));
        SoilRecovery.onFarmlandRandomTick(level, farmAbs);
        helper.assertTrue(SoilFixtures.data(helper, FARM) == null,
                "an all-default entry must be evicted after full recovery");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void fullyRecoveredFarmlandStopsDirtyingItsChunk(GameTestHelper helper) {
        // Regression: a once-harvested field that has fully recovered must not
        // keep re-dirtying its chunk on every random tick just for bookkeeping.
        placeTrackedFarmland(helper, FARM, 100.0F, Blocks.WHEAT);
        helper.setBlock(ROOF, Blocks.SMOOTH_STONE);
        ServerLevel level = helper.getLevel();
        BlockPos farmAbs = helper.absolutePos(FARM);
        SoilData before = SoilFixtures.data(helper, FARM);
        helper.assertTrue(before != null, "the entry keeps its rotation memory at full fertility");

        level.getChunkAt(farmAbs).setUnsaved(false);
        SoilRecovery.onFarmlandRandomTick(level, farmAbs);

        helper.assertTrue(!level.getChunkAt(farmAbs).isUnsaved(),
                "a no-op recovery tick must not mark the chunk unsaved");
        helper.assertTrue(before.equals(SoilFixtures.data(helper, FARM)),
                "a no-op recovery tick must not rewrite the entry");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = WEATHER_BATCH, timeoutTicks = WEATHER_TIMEOUT)
    public void rainDoublesLiveRecovery(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 50.0F, Blocks.WHEAT);
        ServerLevel level = helper.getLevel();
        openSkyAbove(helper, FARM);
        level.setWeatherParameters(0, 6000, true, false);

        // The rain level lerps 0.01/tick toward 1.0 and isRainingAt needs > 0.2.
        helper.runAfterDelay(60, () -> {
            BlockPos farmAbs = helper.absolutePos(FARM);
            helper.assertTrue(level.isRainingAt(farmAbs.above()),
                    "test world must be raining on the open-sky farmland by now");
            float before = SoilFixtures.fertility(helper, FARM);
            SoilRecovery.onFarmlandRandomTick(level, farmAbs);
            float gained = SoilFixtures.fertility(helper, FARM) - before;
            helper.assertTrue(Math.abs(gained - 4.0F) < 1e-4,
                    "a rained-on fallow random tick must restore 2.0 x 2.0, got " + gained);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = WEATHER_BATCH, timeoutTicks = WEATHER_TIMEOUT)
    public void retillSettlesLazyRecoveryRainBlind(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 40.0F, Blocks.WHEAT);
        helper.setBlock(FARM, Blocks.DIRT); // reversion: fertility, memory, and bookkeeping stay put
        ServerLevel level = helper.getLevel();
        openSkyAbove(helper, FARM);
        level.setWeatherParameters(0, 6000, true, false);

        helper.runAfterDelay(60, () -> {
            BlockPos farmAbs = helper.absolutePos(FARM);
            helper.assertTrue(level.isRainingAt(farmAbs.above()),
                    "the dirt span must be spent under live rain to prove rain-blindness");

            SoilClockState clock = SoilClockState.get(level);
            for (int i = 0; i < 4096; i++) {
                clock.advance();
            }

            GameRules.IntegerValue randomTickRule = level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING);
            randomTickRule.set(3, level.getServer());
            try {
                SoilData before = SoilFixtures.data(helper, FARM);
                helper.assertTrue(before != null, "reversion must keep the soil entry");
                long elapsed = clock.time() - before.lastRecoveryCheck();
                // The lazy path pays the base live-path rate — no rain multiplier, ever.
                float expected = Math.min(SoilMath.MAX_FERTILITY, before.fertility()
                        + SoilMath.lazyRecovery(elapsed, CultivationConfig.get().fallowRecoveryPerRandomTick, 3));

                Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
                InteractionResult result = UseBlockCallback.EVENT.invoker().interact(player, level,
                        InteractionHand.MAIN_HAND, hitOn(farmAbs));
                helper.assertTrue(result == InteractionResult.PASS, "the till hook must let vanilla till");

                assertFertility(helper, FARM, expected, "re-till must settle accrued recovery at the live-path rate");
                helper.assertTrue(expected - before.fertility() >= 6.0F,
                        "the advanced clock span must actually accrue");

                helper.setBlock(FARM, Blocks.FARMLAND); // the till itself
                assertFertility(helper, FARM, expected, "new farmland resumes from the settled value");
                player.discard();
                helper.succeed();
            } finally {
                randomTickRule.set(0, level.getServer());
            }
        });
    }

    @GameTest(template = TEMPLATE)
    public void boneMealRestoresAndConsumes(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 50.0F, Blocks.WHEAT);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack boneMeal = new ItemStack(Items.BONE_MEAL, 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, boneMeal);

        InteractionResult result = UseBlockCallback.EVENT.invoker().interact(player, helper.getLevel(),
                InteractionHand.MAIN_HAND, hitOn(helper.absolutePos(FARM)));

        helper.assertTrue(result == InteractionResult.SUCCESS, "bone meal on tracked fallow farmland must succeed");
        assertFertility(helper, FARM, 75.0F, "bone meal must restore +25");
        helper.assertTrue(boneMeal.getCount() == 4, "the successful use must consume one bone meal");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void boneMealClampsAtPristine(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 90.0F, Blocks.WHEAT);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack boneMeal = new ItemStack(Items.BONE_MEAL, 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, boneMeal);

        UseBlockCallback.EVENT.invoker().interact(player, helper.getLevel(),
                InteractionHand.MAIN_HAND, hitOn(helper.absolutePos(FARM)));

        assertFertility(helper, FARM, 100.0F, "restoration clamps at 100");
        helper.assertTrue(boneMeal.getCount() == 4, "the clamped restore still consumes the item");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void boneMealFailsAtFullFertilityWithoutConsuming(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack boneMeal = new ItemStack(Items.BONE_MEAL, 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, boneMeal);
        ServerLevel level = helper.getLevel();

        // Untracked farmland is pristine 100…
        helper.setBlock(FARM, Blocks.FARMLAND);
        InteractionResult untracked = UseBlockCallback.EVENT.invoker().interact(player, level,
                InteractionHand.MAIN_HAND, hitOn(helper.absolutePos(FARM)));
        helper.assertTrue(untracked == InteractionResult.PASS && boneMeal.getCount() == 5,
                "bone meal at pristine fertility must fail without consuming");

        // …and a tracked entry at 100 behaves identically.
        placeTrackedFarmland(helper, FARM, 100.0F, Blocks.WHEAT);
        InteractionResult tracked = UseBlockCallback.EVENT.invoker().interact(player, level,
                InteractionHand.MAIN_HAND, hitOn(helper.absolutePos(FARM)));
        helper.assertTrue(tracked == InteractionResult.PASS && boneMeal.getCount() == 5,
                "bone meal at tracked 100 must fail without consuming");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void boneMealOnCropsAndWorkedGroundIsVanilla(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 50.0F, Blocks.WHEAT);
        helper.setBlock(CROP, Blocks.WHEAT.defaultBlockState());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack boneMeal = new ItemStack(Items.BONE_MEAL, 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, boneMeal);
        ServerLevel level = helper.getLevel();

        // Aimed at the crop block: not farmland, our handler passes to vanilla growth.
        InteractionResult onCrop = UseBlockCallback.EVENT.invoker().interact(player, level,
                InteractionHand.MAIN_HAND, hitOn(helper.absolutePos(CROP)));
        helper.assertTrue(onCrop == InteractionResult.PASS, "bone meal on a crop stays vanilla");

        // Aimed at the farmland under a crop: occupied ground is not fallow.
        InteractionResult onWorked = UseBlockCallback.EVENT.invoker().interact(player, level,
                InteractionHand.MAIN_HAND, hitOn(helper.absolutePos(FARM)));
        helper.assertTrue(onWorked == InteractionResult.PASS, "occupied farmland is not amendable");
        assertFertility(helper, FARM, 50.0F, "no amendment happened");
        helper.assertTrue(boneMeal.getCount() == 5, "nothing was consumed");
        player.discard();
        helper.succeed();
    }

    private static BlockHitResult hitOn(BlockPos absolute) {
        return new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
    }

    /**
     * The gametest framework encases every test structure under a barrier lid,
     * so {@code isRainingAt} can never see sky inside one — punch out the lid
     * directly above the column under test.
     */
    private static void openSkyAbove(GameTestHelper helper, net.minecraft.core.BlockPos rel) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(rel);
        for (int dy = 1; dy <= 10; dy++) {
            BlockPos pos = base.above(dy);
            if (level.getBlockState(pos).is(Blocks.BARRIER)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }
}
