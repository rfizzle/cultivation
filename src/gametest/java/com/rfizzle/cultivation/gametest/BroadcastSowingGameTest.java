package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.item.CultivationItems;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
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
 * scythe sweep, gated behind the iron rake. Right-clicking farmland with a rake
 * in the main hand and a crop seed in the off-hand sows the 3×3, one seed and one
 * rake durability per planted block.
 */
public class BroadcastSowingGameTest implements FabricGameTest {
    @GameTest(template = TEMPLATE)
    public void sowsTheFullThreeByThreeWithARake(GameTestHelper helper) {
        placeFarmlandGrid(helper);
        ServerPlayer player = rakeWith(helper, Items.WHEAT_SEEDS, 9);

        InteractionResult result = sow(helper, player, FARM);

        helper.assertTrue(result == InteractionResult.SUCCESS, "sowing consumes the interaction");
        helper.assertTrue(countAge0(helper, Blocks.WHEAT) == 9, "the whole 3x3 must be sown with wheat at age 0");
        helper.assertTrue(player.getOffhandItem().getCount() == 0, "one off-hand seed is spent per planted block");
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 9, "the rake spends one durability per block");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void noRakeDoesNotBroadcast(GameTestHelper helper) {
        placeFarmlandGrid(helper);
        // A seed in the main hand (no rake) is left to vanilla single-block planting.
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS, 9));

        InteractionResult result = sow(helper, player, FARM);

        helper.assertTrue(result == InteractionResult.PASS, "without a rake the gesture defers to vanilla");
        helper.assertTrue(countAge0(helper, Blocks.WHEAT) == 0, "no broadcast sowing happens without a rake");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void rakeWithoutASeedSowsNothing(GameTestHelper helper) {
        placeFarmlandGrid(helper);
        ServerPlayer player = rakeWith(helper, Items.AIR, 0); // rake in hand, empty off-hand

        InteractionResult result = sow(helper, player, FARM);

        helper.assertTrue(result == InteractionResult.PASS, "a rake with no off-hand seed sows nothing");
        helper.assertTrue(countAge0(helper, Blocks.WHEAT) == 0, "nothing is sown without a seed");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void skipsOccupiedAndNonFarmland(GameTestHelper helper) {
        placeFarmlandGrid(helper);
        helper.setBlock(FARM.offset(-1, 0, -1), Blocks.DIRT);
        helper.setBlock(FARM.offset(1, 0, 1).above(), matureWheat());
        ServerPlayer player = rakeWith(helper, Items.WHEAT_SEEDS, 9);

        InteractionResult result = sow(helper, player, FARM);

        helper.assertTrue(result == InteractionResult.SUCCESS, "sowing the rest still consumes the interaction");
        helper.assertTrue(countAge0(helper, Blocks.WHEAT) == 7, "the 7 free farmland blocks are sown, the other 2 skipped");
        helper.assertTrue(player.getOffhandItem().getCount() == 2, "only the 7 planted blocks spend a seed");
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 7, "only the 7 planted blocks spend durability");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void seedBudgetCapsPlanting(GameTestHelper helper) {
        placeFarmlandGrid(helper);
        ServerPlayer player = rakeWith(helper, Items.WHEAT_SEEDS, 3);

        InteractionResult result = sow(helper, player, FARM);

        helper.assertTrue(result == InteractionResult.SUCCESS, "a partial sow still consumes the interaction");
        helper.assertTrue(countAge0(helper, Blocks.WHEAT) == 3, "only as many blocks as off-hand seeds are sown");
        helper.assertTrue(player.getOffhandItem().isEmpty(), "the seed stack is emptied");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void rakeDurabilityCapsPlantingAndBreaks(GameTestHelper helper) {
        placeFarmlandGrid(helper);
        ServerPlayer player = rakeWith(helper, Items.WHEAT_SEEDS, 9);
        ItemStack rake = new ItemStack(CultivationItems.IRON_RAKE);
        rake.setDamageValue(rake.getMaxDamage() - 3); // three uses left
        player.setItemInHand(InteractionHand.MAIN_HAND, rake);

        InteractionResult result = sow(helper, player, FARM);

        helper.assertTrue(result == InteractionResult.SUCCESS, "the partial sow consumes the interaction");
        helper.assertTrue(countAge0(helper, Blocks.WHEAT) == 3, "the rake sows only until it breaks");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the rake breaks when its durability runs out mid-sow");
        helper.assertTrue(player.getOffhandItem().getCount() == 6, "only the 3 planted blocks spend a seed");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void disabledConfigIsInert(GameTestHelper helper) {
        boolean saved = CultivationConfig.get().enableBroadcastSowing;
        try {
            CultivationConfig.get().enableBroadcastSowing = false;
            placeFarmlandGrid(helper);
            ServerPlayer player = rakeWith(helper, Items.WHEAT_SEEDS, 9);

            InteractionResult result = sow(helper, player, FARM);

            helper.assertTrue(result == InteractionResult.PASS, "with the toggle off the rake is inert");
            helper.assertTrue(countAge0(helper, Blocks.WHEAT) == 0, "with the toggle off nothing is broadcast");
            player.discard();
            helper.succeed();
        } finally {
            CultivationConfig.get().enableBroadcastSowing = saved;
        }
    }

    @GameTest(template = TEMPLATE)
    public void creativeSowsWithoutSpendingSeedsOrDurability(GameTestHelper helper) {
        placeFarmlandGrid(helper);
        ServerPlayer player = rakeWith(helper, Items.WHEAT_SEEDS, 1);
        player.setGameMode(GameType.CREATIVE);

        InteractionResult result = sow(helper, player, FARM);

        helper.assertTrue(result == InteractionResult.SUCCESS, "creative sows the whole 3x3");
        helper.assertTrue(countAge0(helper, Blocks.WHEAT) == 9, "creative is not capped by the held stack");
        helper.assertTrue(player.getOffhandItem().getCount() == 1, "creative spends no seeds");
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 0, "creative spends no durability");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void sowingDoesNotDrainSoil(GameTestHelper helper) {
        placeFarmlandGrid(helper);
        placeTrackedFarmland(helper, FARM, 100.0F, Blocks.WHEAT);
        ServerPlayer player = rakeWith(helper, Items.WHEAT_SEEDS, 9);

        sow(helper, player, FARM);

        // Planting is not a harvest — a fresh sow touches no soil state.
        assertFertility(helper, FARM, 100.0F, "sowing must not drain fertility");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void sowsPitcherPods(GameTestHelper helper) {
        placeFarmlandGrid(helper);
        ServerPlayer player = rakeWith(helper, Items.PITCHER_POD, 9);

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

    /** Counts positions in the 3×3 crop layer holding {@code crop} freshly sown at age 0. */
    private static int countAge0(GameTestHelper helper, Block crop) {
        BlockState age0 = crop instanceof PitcherCropBlock
                ? crop.defaultBlockState() // age 0 is the single-block pod
                : ((CropBlock) crop).getStateForAge(0);
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (helper.getBlockState(FARM.offset(dx, 0, dz).above()).equals(age0)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** A survival player holding an iron rake in the main hand and {@code count} seeds in the off-hand. */
    private static ServerPlayer rakeWith(GameTestHelper helper, Item seed, int count) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CultivationItems.IRON_RAKE));
        player.setItemInHand(InteractionHand.OFF_HAND, count > 0 ? new ItemStack(seed, count) : ItemStack.EMPTY);
        return player;
    }

    /** Fires the real {@link UseBlockCallback} pipeline against the farmland's up-face. */
    private static InteractionResult sow(GameTestHelper helper, ServerPlayer player, BlockPos farmlandRel) {
        BlockPos abs = helper.absolutePos(farmlandRel);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        return UseBlockCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
    }
}
