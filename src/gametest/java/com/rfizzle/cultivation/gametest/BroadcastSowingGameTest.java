package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.config.CultivationConfig;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static com.rfizzle.cultivation.gametest.SoilFixtures.FARM;
import static com.rfizzle.cultivation.gametest.SoilFixtures.TEMPLATE;
import static com.rfizzle.cultivation.gametest.SoilFixtures.assertFertility;
import static com.rfizzle.cultivation.gametest.SoilFixtures.matureWheat;
import static com.rfizzle.cultivation.gametest.SoilFixtures.placeTrackedFarmland;

/**
 * Broadcast sowing ({@code design/SPEC.md} §7) — the planting mirror of the
 * scythe sweep. A sneak-right-click with a farmland-crop seed sows the 3×3 of
 * farmland centered on the clicked block, one seed per planted block.
 */
public class BroadcastSowingGameTest implements FabricGameTest {
    @GameTest(template = TEMPLATE)
    public void sowsTheFullThreeByThreeOnEmptyFarmland(GameTestHelper helper) {
        placeFarmlandGrid(helper);
        ServerPlayer player = sneakingWith(helper, Items.WHEAT_SEEDS, 9);

        InteractionResult result = sow(helper, player, FARM);

        helper.assertTrue(result == InteractionResult.SUCCESS, "sowing consumes the interaction");
        helper.assertTrue(countAge0(helper, Blocks.WHEAT) == 9, "the whole 3x3 must be sown with wheat at age 0");
        helper.assertTrue(player.getMainHandItem().getCount() == 0, "one seed is spent per planted block");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void skipsOccupiedAndNonFarmland(GameTestHelper helper) {
        placeFarmlandGrid(helper);
        // One position is not farmland; one already holds a crop — both are skipped.
        helper.setBlock(FARM.offset(-1, 0, -1), Blocks.DIRT);
        helper.setBlock(FARM.offset(1, 0, 1).above(), matureWheat());
        ServerPlayer player = sneakingWith(helper, Items.WHEAT_SEEDS, 9);

        InteractionResult result = sow(helper, player, FARM);

        helper.assertTrue(result == InteractionResult.SUCCESS, "sowing the rest still consumes the interaction");
        helper.assertTrue(countAge0(helper, Blocks.WHEAT) == 7, "the 7 free farmland blocks are sown, the other 2 skipped");
        helper.assertTrue(player.getMainHandItem().getCount() == 2, "only the 7 planted blocks spend a seed");
        helper.assertBlockPresent(Blocks.AIR, FARM.offset(-1, 0, -1).above());
        helper.assertTrue(helper.getBlockState(FARM.offset(1, 0, 1).above()).getValue(CropBlock.AGE) == CropBlock.MAX_AGE,
                "the pre-existing mature crop must be left untouched");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void seedBudgetCapsPlanting(GameTestHelper helper) {
        placeFarmlandGrid(helper);
        ServerPlayer player = sneakingWith(helper, Items.WHEAT_SEEDS, 3);

        InteractionResult result = sow(helper, player, FARM);

        helper.assertTrue(result == InteractionResult.SUCCESS, "a partial sow still consumes the interaction");
        helper.assertTrue(countAge0(helper, Blocks.WHEAT) == 3, "only as many blocks as seeds are sown");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the seed stack is emptied");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void nonSneakClickIsLeftToVanilla(GameTestHelper helper) {
        placeFarmlandGrid(helper);
        ServerPlayer player = sneakingWith(helper, Items.WHEAT_SEEDS, 9);
        player.setShiftKeyDown(false);

        InteractionResult result = sow(helper, player, FARM);

        helper.assertTrue(result == InteractionResult.PASS, "without a sneak the gesture defers to vanilla single planting");
        helper.assertTrue(countAge0(helper, Blocks.WHEAT) == 0, "no broadcast sowing happens without a sneak");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void disabledConfigIsInert(GameTestHelper helper) {
        boolean saved = CultivationConfig.get().enableBroadcastSowing;
        try {
            CultivationConfig.get().enableBroadcastSowing = false;
            placeFarmlandGrid(helper);
            ServerPlayer player = sneakingWith(helper, Items.WHEAT_SEEDS, 9);

            InteractionResult result = sow(helper, player, FARM);

            helper.assertTrue(result == InteractionResult.PASS, "with the toggle off the gesture is inert");
            helper.assertTrue(countAge0(helper, Blocks.WHEAT) == 0, "with the toggle off nothing is broadcast");
            player.discard();
            helper.succeed();
        } finally {
            CultivationConfig.get().enableBroadcastSowing = saved;
        }
    }

    @GameTest(template = TEMPLATE)
    public void creativeSowsWithoutSpendingSeeds(GameTestHelper helper) {
        placeFarmlandGrid(helper);
        ServerPlayer player = sneakingWith(helper, Items.WHEAT_SEEDS, 1);
        player.setGameMode(GameType.CREATIVE);

        InteractionResult result = sow(helper, player, FARM);

        helper.assertTrue(result == InteractionResult.SUCCESS, "creative sows the whole 3x3");
        helper.assertTrue(countAge0(helper, Blocks.WHEAT) == 9, "creative is not capped by the held stack");
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "creative spends no seeds");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void sowingDoesNotDrainSoil(GameTestHelper helper) {
        placeFarmlandGrid(helper);
        placeTrackedFarmland(helper, FARM, 100.0F, Blocks.WHEAT);
        ServerPlayer player = sneakingWith(helper, Items.WHEAT_SEEDS, 9);

        sow(helper, player, FARM);

        // Planting is not a harvest — a fresh sow touches no soil state.
        assertFertility(helper, FARM, 100.0F, "sowing must not drain fertility");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void sowsPitcherPods(GameTestHelper helper) {
        placeFarmlandGrid(helper);
        ServerPlayer player = sneakingWith(helper, Items.PITCHER_POD, 9);

        InteractionResult result = sow(helper, player, FARM);

        helper.assertTrue(result == InteractionResult.SUCCESS, "a pitcher pod sows the 3x3");
        helper.assertTrue(countAge0(helper, Blocks.PITCHER_CROP) == 9, "each block sows a pitcher crop at age 0");
        player.discard();
        helper.succeed();
    }

    // --- helpers ---

    /** The 3×3 of farmland centered on {@link SoilFixtures#FARM}, empty above. */
    private static void placeFarmlandGrid(GameTestHelper helper) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                helper.setBlock(FARM.offset(dx, 0, dz), Blocks.FARMLAND);
            }
        }
    }

    private static int countAge0(GameTestHelper helper, net.minecraft.world.level.block.Block crop) {
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockState state = helper.getBlockState(FARM.offset(dx, 0, dz).above());
                if (state.is(crop) && state.getValue(CropBlock.AGE) == 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private static ServerPlayer sneakingWith(GameTestHelper helper, Item seed, int count) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(seed, count));
        return player;
    }

    /** Fires the real {@link UseBlockCallback} pipeline against the farmland's up-face. */
    private static InteractionResult sow(GameTestHelper helper, ServerPlayer player, BlockPos farmlandRel) {
        BlockPos abs = helper.absolutePos(farmlandRel);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        return UseBlockCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
    }
}
