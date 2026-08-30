package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.gametest.util.MockPlayers;
import com.rfizzle.cultivation.soil.SoilGrowth;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static com.rfizzle.cultivation.gametest.SoilFixtures.CROP;
import static com.rfizzle.cultivation.gametest.SoilFixtures.FARM;
import static com.rfizzle.cultivation.gametest.SoilFixtures.TEMPLATE;
import static com.rfizzle.cultivation.gametest.SoilFixtures.assertFertility;
import static com.rfizzle.cultivation.gametest.SoilFixtures.berryBush;
import static com.rfizzle.cultivation.gametest.SoilFixtures.matureWart;
import static com.rfizzle.cultivation.gametest.SoilFixtures.placeTrackedGround;

/**
 * The second-wave crops (SPEC §1, issue #25): nether wart on soul sand and the
 * sweet berry bush on dirt drain their ground and grow slower when it tires,
 * while the scythe and bare-hand right-click leave them alone.
 */
public class SecondWaveSoilGameTest implements FabricGameTest {

    // --- nether wart: harvested by the break ---

    @GameTest(template = TEMPLATE)
    public void netherWartBreakDrains(GameTestHelper helper) {
        placeTrackedGround(helper, FARM, Blocks.SOUL_SAND, 100.0F, Blocks.NETHER_WART);
        helper.setBlock(CROP, matureWart());
        helper.getLevel().destroyBlock(helper.absolutePos(CROP), true);

        assertFertility(helper, FARM, 97.0F, "breaking mature nether wart over soul sand must drain");
        helper.assertItemEntityPresent(Items.NETHER_WART, CROP, 2.0);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void netherWartOnWrongGroundNeverDrains(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.DIRT);
        helper.setBlock(CROP, matureWart());
        helper.getLevel().destroyBlock(helper.absolutePos(CROP), true);
        helper.assertTrue(SoilFixtures.data(helper, FARM) == null,
                "nether wart only tracks soul sand — dirt below must not drain");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void immatureNetherWartNeverDrains(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.SOUL_SAND);
        helper.setBlock(CROP, Blocks.NETHER_WART.defaultBlockState().setValue(BlockStateProperties.AGE_3, 2));
        helper.getLevel().destroyBlock(helper.absolutePos(CROP), true);
        helper.assertTrue(SoilFixtures.data(helper, FARM) == null,
                "breaking immature nether wart must not create or drain soil state");
        helper.succeed();
    }

    // --- sweet berries: harvested by the pick, and also the break ---

    @GameTest(template = TEMPLATE)
    public void sweetBerryPickDrainsAndKeepsTheBush(GameTestHelper helper) {
        placeTrackedGround(helper, FARM, Blocks.DIRT, 100.0F, Blocks.SWEET_BERRY_BUSH);
        helper.setBlock(CROP, berryBush(3));
        ServerPlayer player = bareHand(helper);
        try {
            pick(helper, player, CROP);

            assertFertility(helper, FARM, 97.0F, "picking a ripe bush drains its dirt");
            helper.assertTrue(helper.getBlockState(CROP).is(Blocks.SWEET_BERRY_BUSH)
                            && helper.getBlockState(CROP).getValue(BlockStateProperties.AGE_3) == 1,
                    "the bush persists and resets to age 1, as vanilla picking does");
            helper.assertItemEntityPresent(Items.SWEET_BERRIES, CROP, 2.0);
            helper.succeed();
        } finally {
            MockPlayers.retire(player);
        }
    }

    @GameTest(template = TEMPLATE)
    public void exhaustedBerryPickClampsToOneBerry(GameTestHelper helper) {
        // 1.0 − 3.0 (same-crop) clamps to 0: this pick lands the dirt on exhausted.
        placeTrackedGround(helper, FARM, Blocks.DIRT, 1.0F, Blocks.SWEET_BERRY_BUSH);
        helper.setBlock(CROP, berryBush(3));
        ServerPlayer player = bareHand(helper);
        try {
            pick(helper, player, CROP);

            assertFertility(helper, FARM, 0.0F, "the draining pick must clamp at 0");
            // A ripe bush pops 2–3 berries; the exhausted clamp makes exactly 1 deterministic.
            helper.assertItemEntityCountIs(Items.SWEET_BERRIES, CROP, 2.0, 1);
            helper.succeed();
        } finally {
            MockPlayers.retire(player);
        }
    }

    @GameTest(template = TEMPLATE)
    public void sweetBerryBreakDrains(GameTestHelper helper) {
        placeTrackedGround(helper, FARM, Blocks.DIRT, 100.0F, Blocks.SWEET_BERRY_BUSH);
        helper.setBlock(CROP, berryBush(3));
        helper.getLevel().destroyBlock(helper.absolutePos(CROP), true);
        assertFertility(helper, FARM, 97.0F, "breaking a ripe bush is a harvest and drains its dirt");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void immatureBerryPickIsInert(GameTestHelper helper) {
        // Age 1 is below the pickable age — vanilla pops nothing, so nothing drains.
        placeTrackedGround(helper, FARM, Blocks.DIRT, 100.0F, Blocks.SWEET_BERRY_BUSH);
        helper.setBlock(CROP, berryBush(1));
        ServerPlayer player = bareHand(helper);
        try {
            pick(helper, player, CROP);

            assertFertility(helper, FARM, 100.0F, "picking a bush too young to fruit must not drain");
            helper.assertItemEntityNotPresent(Items.SWEET_BERRIES, CROP, 2.0);
            helper.succeed();
        } finally {
            MockPlayers.retire(player);
        }
    }

    // --- growth slows as the ground tires (deterministic, on the roll bound) ---

    @GameTest(template = TEMPLATE)
    public void growthBoundWidensAsGroundTires(GameTestHelper helper) {
        helper.setBlock(CROP, matureWart());
        var level = helper.getLevel();
        var cropAbs = helper.absolutePos(CROP);

        placeTrackedGround(helper, FARM, Blocks.SOUL_SAND, 100.0F, Blocks.NETHER_WART);
        helper.assertValueEqual(SoilGrowth.secondWaveGrowthBound(10, level, cropAbs), 10,
                "rich soul sand keeps the vanilla roll bound");
        placeTrackedGround(helper, FARM, Blocks.SOUL_SAND, 10.0F, Blocks.NETHER_WART);
        helper.assertValueEqual(SoilGrowth.secondWaveGrowthBound(10, level, cropAbs), 13,
                "tired soul sand widens the bound to 13");
        placeTrackedGround(helper, FARM, Blocks.SOUL_SAND, 0.0F, Blocks.NETHER_WART);
        helper.assertValueEqual(SoilGrowth.secondWaveGrowthBound(10, level, cropAbs), 20,
                "exhausted soul sand widens the bound to 20");
        helper.succeed();
    }

    // --- config toggle reverts both crops to vanilla ---

    @GameTest(template = TEMPLATE)
    public void disabledToggleLeavesSecondWaveCropsVanilla(GameTestHelper helper) {
        boolean saved = CultivationConfig.get().enableNonFarmlandSoil;
        try {
            CultivationConfig.get().enableNonFarmlandSoil = false;
            helper.setBlock(FARM, Blocks.SOUL_SAND);
            helper.setBlock(CROP, matureWart());
            var level = helper.getLevel();
            var cropAbs = helper.absolutePos(CROP);

            // Growth roll is bit-identical to vanilla.
            helper.assertValueEqual(SoilGrowth.secondWaveGrowthBound(10, level, cropAbs), 10,
                    "with the toggle off, growth uses the vanilla roll bound");
            // Breaking the wart drains nothing.
            level.destroyBlock(cropAbs, true);
            helper.assertTrue(SoilFixtures.data(helper, FARM) == null,
                    "with the toggle off, breaking nether wart must not drain");
            helper.succeed();
        } finally {
            CultivationConfig.get().enableNonFarmlandSoil = saved;
        }
    }

    // --- the replant harvests never claim a bush ---

    @GameTest(template = TEMPLATE)
    public void rightClickHarvestLeavesTheBushToVanilla(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.DIRT);
        helper.setBlock(CROP, berryBush(3));
        ServerPlayer player = bareHand(helper);
        try {
            BlockPos abs = helper.absolutePos(CROP);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);

            InteractionResult result = UseBlockCallback.EVENT.invoker()
                    .interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);

            helper.assertTrue(result == InteractionResult.PASS,
                    "the right-click harvest must pass a bush through — it is not a replant crop");
            helper.assertTrue(helper.getBlockState(CROP).getValue(BlockStateProperties.AGE_3) == 3,
                    "the callback must not disturb the bush");
            helper.succeed();
        } finally {
            MockPlayers.retire(player);
        }
    }

    // --- helpers ---

    private static ServerPlayer bareHand(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /**
     * The production pick path: the {@link UseBlockCallback} pipeline gets first
     * refusal (the right-click harvest passes a bush through), then vanilla's own
     * {@code useWithoutItem} runs — where the pick-drain mixin lives.
     */
    private static void pick(GameTestHelper helper, ServerPlayer player, BlockPos rel) {
        BlockPos abs = helper.absolutePos(rel);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        InteractionResult callback = UseBlockCallback.EVENT.invoker()
                .interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(callback == InteractionResult.PASS,
                "a bare-hand bush interaction must fall through to vanilla picking");
        player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
    }
}
