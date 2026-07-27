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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static com.rfizzle.cultivation.gametest.SoilFixtures.CROP;
import static com.rfizzle.cultivation.gametest.SoilFixtures.FARM;
import static com.rfizzle.cultivation.gametest.SoilFixtures.TEMPLATE;
import static com.rfizzle.cultivation.gametest.SoilFixtures.assertFertility;
import static com.rfizzle.cultivation.gametest.SoilFixtures.matureCarrots;
import static com.rfizzle.cultivation.gametest.SoilFixtures.placeTrackedFarmland;

/**
 * The bare-hand right-click harvest ({@code design/SPEC.md} §7) — the
 * single-block sibling of the scythe sweep. Uses carrots (their product is their
 * own seed, so a drop always carries one to replant) for the deterministic
 * replant assertions.
 */
public class RightClickHarvestGameTest implements FabricGameTest {
    // A second crop next to the target, to prove the harvest is single-block.
    private static final BlockPos NEIGHBOR = new BlockPos(4, 2, 3);
    private static final BlockPos NEIGHBOR_FARM = NEIGHBOR.below();

    @GameTest(template = TEMPLATE)
    public void harvestsAndReplantsASingleMatureCrop(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 100.0F, Blocks.CARROTS);
        helper.setBlock(CROP, matureCarrots());
        placeTrackedFarmland(helper, NEIGHBOR_FARM, 100.0F, Blocks.CARROTS);
        helper.setBlock(NEIGHBOR, matureCarrots());
        ServerPlayer player = bareHand(helper);
        try {
            InteractionResult result = rightClick(helper, player, CROP);

            helper.assertTrue(result == InteractionResult.SUCCESS, "a bare-hand harvest consumes the interaction");
            var harvested = helper.getBlockState(CROP);
            helper.assertTrue(harvested.is(Blocks.CARROTS) && harvested.getValue(CropBlock.AGE) == 0,
                    "the harvested crop must replant carrots at age 0");
            helper.assertItemEntityPresent(Items.CARROT, CROP, 2.0);
            assertFertility(helper, FARM, 97.0F, "a same-crop harvest drains the full amount");
            // Single-block: the neighbor is left mature and its soil untouched.
            helper.assertTrue(helper.getBlockState(NEIGHBOR).getValue(CropBlock.AGE) == CropBlock.MAX_AGE,
                    "the neighboring crop must be untouched — this is not a sweep");
            assertFertility(helper, NEIGHBOR_FARM, 100.0F, "the neighbor's soil must not drain");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = TEMPLATE)
    public void immatureCropIsUntouched(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 100.0F, Blocks.CARROTS);
        var immature = Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 3);
        helper.setBlock(CROP, immature);
        ServerPlayer player = bareHand(helper);
        try {
            InteractionResult result = rightClick(helper, player, CROP);

            helper.assertTrue(result == InteractionResult.PASS, "an immature crop is left to vanilla (no harvest)");
            helper.assertTrue(helper.getBlockState(CROP).equals(immature), "an immature crop must be untouched");
            assertFertility(helper, FARM, 100.0F, "an immature crop must not drain soil");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = TEMPLATE)
    public void heldItemDoesNotHarvest(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 100.0F, Blocks.CARROTS);
        helper.setBlock(CROP, matureCarrots());
        ServerPlayer player = bareHand(helper);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));

            InteractionResult result = rightClick(helper, player, CROP);

            helper.assertTrue(result == InteractionResult.PASS, "a held item takes its own use, never a harvest");
            helper.assertTrue(helper.getBlockState(CROP).getValue(CropBlock.AGE) == CropBlock.MAX_AGE,
                    "a crop right-clicked with a held item must be left mature");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = TEMPLATE)
    public void disabledConfigDoesNotHarvest(GameTestHelper helper) {
        boolean saved = CultivationConfig.get().enableRightClickHarvest;
        ServerPlayer player = bareHand(helper);
        try {
            CultivationConfig.get().enableRightClickHarvest = false;
            placeTrackedFarmland(helper, FARM, 100.0F, Blocks.CARROTS);
            helper.setBlock(CROP, matureCarrots());

            InteractionResult result = rightClick(helper, player, CROP);

            helper.assertTrue(result == InteractionResult.PASS, "with the toggle off, a bare-hand right-click is inert");
            helper.assertTrue(helper.getBlockState(CROP).getValue(CropBlock.AGE) == CropBlock.MAX_AGE,
                    "with the toggle off, the crop must be left mature");
            helper.succeed();
        } finally {
            CultivationConfig.get().enableRightClickHarvest = saved;
            player.discard();
        }
    }

    @GameTest(template = TEMPLATE)
    public void creativeHarvestsAndReplants(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 100.0F, Blocks.CARROTS);
        helper.setBlock(CROP, matureCarrots());
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            player.setGameMode(GameType.CREATIVE);

            InteractionResult result = rightClick(helper, player, CROP);

            helper.assertTrue(result == InteractionResult.SUCCESS, "creative harvests identically");
            var state = helper.getBlockState(CROP);
            helper.assertTrue(state.is(Blocks.CARROTS) && state.getValue(CropBlock.AGE) == 0,
                    "creative must harvest and replant at age 0");
            helper.assertItemEntityPresent(Items.CARROT, CROP, 2.0);
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = TEMPLATE)
    public void noSeedInDropsLeavesTheBlockEmpty(GameTestHelper helper) {
        // A mature torchflower drops the flower but no seeds — nothing to sow.
        placeTrackedFarmland(helper, FARM, 100.0F, Blocks.TORCHFLOWER_CROP);
        helper.setBlock(CROP, Blocks.TORCHFLOWER);
        ServerPlayer player = bareHand(helper);
        try {
            InteractionResult result = rightClick(helper, player, CROP);

            helper.assertTrue(result == InteractionResult.SUCCESS, "a seedless crop is still harvested");
            helper.assertBlockPresent(Blocks.AIR, CROP);
            helper.assertBlockPresent(Blocks.FARMLAND, FARM);
            helper.assertItemEntityPresent(Items.TORCHFLOWER, CROP, 2.0);
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    // --- helpers ---

    private static ServerPlayer bareHand(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /** Fires the real {@link UseBlockCallback} pipeline against a crop's up-face — the production path. */
    private static InteractionResult rightClick(GameTestHelper helper, ServerPlayer player, BlockPos rel) {
        BlockPos abs = helper.absolutePos(rel);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        return UseBlockCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
    }
}
