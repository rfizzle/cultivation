package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.item.CultivationItems;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

import static com.rfizzle.cultivation.gametest.SoilFixtures.TEMPLATE;
import static com.rfizzle.cultivation.gametest.SoilFixtures.assertFertility;
import static com.rfizzle.cultivation.gametest.SoilFixtures.matureCarrots;
import static com.rfizzle.cultivation.gametest.SoilFixtures.matureWheat;
import static com.rfizzle.cultivation.gametest.SoilFixtures.placeTrackedFarmland;

/** The 3×3 scythe sweep ({@code design/SPEC.md} §7). */
public class ScytheSweepGameTest implements FabricGameTest {
    // A 3×3 field centered on the soil-fixture crop, one layer of farmland below.
    private static final BlockPos CENTER = new BlockPos(3, 2, 3);
    private static final BlockPos EDGE = new BlockPos(2, 2, 2);

    @GameTest(template = TEMPLATE)
    public void sweepHarvestsAndReplantsTheFullThreeByThree(GameTestHelper helper) {
        fillField(helper, matureWheat());
        ServerPlayer player = survivalScyther(helper, CultivationItems.IRON_SCYTHE);

        int startDamage = player.getMainHandItem().getDamageValue();
        breakCenter(helper, player);

        forEachFieldPos((dx, dz) -> {
            BlockPos pos = CENTER.offset(dx, 0, dz);
            BlockState state = helper.getBlockState(pos);
            helper.assertTrue(state.is(Blocks.WHEAT) && state.getValue(CropBlock.AGE) == 0,
                    "every swept block must replant wheat at age 0");
        });
        helper.assertItemEntityPresent(Items.WHEAT, CENTER, 2.0);
        helper.assertTrue(player.getMainHandItem().getDamageValue() - startDamage == 9,
                "the scythe must lose one durability per crop harvested (9)");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void mixedFieldReplantsPerCropAndDrainsPerBlock(GameTestHelper helper) {
        // All positions start tracked at full fertility with wheat as the last crop.
        forEachFieldPos((dx, dz) -> placeTrackedFarmland(helper, farmOf(CENTER.offset(dx, 0, dz)), 100.0F, Blocks.WHEAT));
        forEachFieldPos((dx, dz) -> helper.setBlock(CENTER.offset(dx, 0, dz), matureWheat()));
        helper.setBlock(CENTER, matureCarrots());
        ServerPlayer player = survivalScyther(helper, CultivationItems.DIAMOND_SCYTHE);

        breakCenter(helper, player);

        BlockState center = helper.getBlockState(CENTER);
        helper.assertTrue(center.is(Blocks.CARROTS) && center.getValue(CropBlock.AGE) == 0,
                "the carrot at the center must replant carrots, not wheat");
        BlockState edge = helper.getBlockState(EDGE);
        helper.assertTrue(edge.is(Blocks.WHEAT) && edge.getValue(CropBlock.AGE) == 0,
                "a wheat block must replant wheat");
        // Rotation drain is evaluated per block: the rotated carrot drains 1.5, the same-crop wheat 3.0.
        assertFertility(helper, farmOf(CENTER), 98.5F, "the rotated carrot harvest drains at the reduced rate");
        assertFertility(helper, farmOf(EDGE), 97.0F, "the same-crop wheat harvest drains the full amount");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void immatureAndNonCropBlocksAreUntouched(GameTestHelper helper) {
        fillField(helper, matureWheat());
        BlockState immature = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3);
        helper.setBlock(EDGE, immature);
        BlockPos stonePos = CENTER.offset(1, 0, -1);
        helper.setBlock(stonePos, Blocks.STONE);
        ServerPlayer player = survivalScyther(helper, CultivationItems.IRON_SCYTHE);

        breakCenter(helper, player);

        helper.assertTrue(helper.getBlockState(EDGE).equals(immature), "an immature crop in the area must be untouched");
        helper.assertBlockPresent(Blocks.STONE, stonePos);
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void noSeedInDropsLeavesTheBlockEmpty(GameTestHelper helper) {
        // A mature torchflower drops the flower but no seeds — nothing to sow.
        placeTrackedFarmland(helper, farmOf(CENTER), 100.0F, Blocks.TORCHFLOWER_CROP);
        helper.setBlock(CENTER, Blocks.TORCHFLOWER);
        ServerPlayer player = survivalScyther(helper, CultivationItems.IRON_SCYTHE);

        breakCenter(helper, player);

        helper.assertBlockPresent(Blocks.AIR, CENTER);
        helper.assertBlockPresent(Blocks.FARMLAND, farmOf(CENTER));
        helper.assertItemEntityPresent(Items.TORCHFLOWER, CENTER, 2.0);
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void pitcherReplantsAPodAndClearsBothHalves(GameTestHelper helper) {
        helper.setBlock(farmOf(CENTER), Blocks.FARMLAND);
        BlockState mature = Blocks.PITCHER_CROP.defaultBlockState()
                .setValue(PitcherCropBlock.AGE, PitcherCropBlock.MAX_AGE);
        helper.setBlock(CENTER, mature.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(CENTER.above(), mature.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        ServerPlayer player = survivalScyther(helper, CultivationItems.NETHERITE_SCYTHE);

        breakCenter(helper, player);

        BlockState replanted = helper.getBlockState(CENTER);
        helper.assertTrue(replanted.is(Blocks.PITCHER_CROP)
                        && replanted.getValue(PitcherCropBlock.AGE) == 0
                        && replanted.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER,
                "the pitcher must replant a single age-0 pod (lower half)");
        helper.assertBlockPresent(Blocks.AIR, CENTER.above());
        helper.assertItemEntityPresent(Items.PITCHER_PLANT, CENTER, 2.0);
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void disabledConfigBreaksASingleBlock(GameTestHelper helper) {
        boolean saved = CultivationConfig.get().enableScytheHarvest;
        try {
            CultivationConfig.get().enableScytheHarvest = false;
            fillField(helper, matureWheat());
            ServerPlayer player = survivalScyther(helper, CultivationItems.IRON_SCYTHE);

            breakCenter(helper, player);

            helper.assertBlockPresent(Blocks.AIR, CENTER);
            helper.assertTrue(helper.getBlockState(EDGE).is(Blocks.WHEAT),
                    "with the sweep disabled, neighbors must be untouched");
            player.discard();
            helper.succeed();
        } finally {
            CultivationConfig.get().enableScytheHarvest = saved;
        }
    }

    @GameTest(template = TEMPLATE)
    public void creativeSweepsWithoutDurabilityLoss(GameTestHelper helper) {
        fillField(helper, matureWheat());
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        player.setGameMode(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CultivationItems.IRON_SCYTHE));

        breakCenter(helper, player);

        helper.assertTrue(helper.getBlockState(CENTER).is(Blocks.WHEAT), "creative must sweep and replant identically");
        helper.assertItemEntityPresent(Items.WHEAT, CENTER, 2.0);
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 0,
                "a creative sweep must not spend durability");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void protectionDeniedBlockIsSkipped(GameTestHelper helper) {
        fillField(helper, matureWheat());
        Denier.armAt(helper.absolutePos(EDGE));
        try {
            ServerPlayer player = survivalScyther(helper, CultivationItems.IRON_SCYTHE);
            breakCenter(helper, player);

            BlockState guarded = helper.getBlockState(EDGE);
            helper.assertTrue(guarded.is(Blocks.WHEAT) && guarded.getValue(CropBlock.AGE) == CropBlock.MAX_AGE,
                    "a block denied by a break-protection check must be left mature and unharvested");
            helper.assertTrue(helper.getBlockState(CENTER).is(Blocks.WHEAT)
                            && helper.getBlockState(CENTER).getValue(CropBlock.AGE) == 0,
                    "unprotected blocks must still be harvested and replanted");
            player.discard();
            helper.succeed();
        } finally {
            Denier.disarm();
        }
    }

    @GameTest(template = TEMPLATE)
    public void protectionDeniedCenterIsSkipped(GameTestHelper helper) {
        fillField(helper, matureWheat());
        Denier.armAt(helper.absolutePos(CENTER));
        try {
            ServerPlayer player = survivalScyther(helper, CultivationItems.IRON_SCYTHE);
            breakCenter(helper, player);

            BlockState center = helper.getBlockState(CENTER);
            helper.assertTrue(center.is(Blocks.WHEAT) && center.getValue(CropBlock.AGE) == CropBlock.MAX_AGE,
                    "a denied center block must be left mature — the center is checked like every other block");
            BlockState edge = helper.getBlockState(EDGE);
            helper.assertTrue(edge.is(Blocks.WHEAT) && edge.getValue(CropBlock.AGE) == 0,
                    "unprotected neighbors must still be harvested when only the center is denied");
            player.discard();
            helper.succeed();
        } finally {
            Denier.disarm();
        }
    }

    @GameTest(template = TEMPLATE)
    public void scytheBreakingMidSweepStopsCleanly(GameTestHelper helper) {
        fillField(helper, matureWheat());
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        // One durability left: the scythe breaks on the first crop of the sweep.
        ItemStack scythe = new ItemStack(CultivationItems.IRON_SCYTHE);
        scythe.setDamageValue(scythe.getMaxDamage() - 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, scythe);

        breakCenter(helper, player);

        helper.assertTrue(player.getMainHandItem().getCount() == 0,
                "a scythe that breaks mid-sweep must end empty, never a negative-count stack");
        // The break stops the sweep, so the center (reached fifth) is never harvested.
        helper.assertTrue(helper.getBlockState(CENTER).getValue(CropBlock.AGE) == CropBlock.MAX_AGE,
                "the sweep must stop once the tool breaks, leaving later blocks unharvested");
        player.discard();
        helper.succeed();
    }

    // --- helpers ---

    /**
     * A break-protection listener registered exactly once, denying breaks at a
     * single armed absolute position (a stand-in for a claim/protection mod).
     * Disarmed between tests so it stays a no-op the rest of the run.
     */
    private static final class Denier implements PlayerBlockBreakEvents.Before {
        @Nullable
        private static volatile BlockPos target;
        private static boolean registered;

        private static synchronized void armAt(BlockPos pos) {
            target = pos;
            if (!registered) {
                registered = true;
                PlayerBlockBreakEvents.BEFORE.register(new Denier());
            }
        }

        private static void disarm() {
            target = null;
        }

        @Override
        public boolean beforeBlockBreak(Level level, Player player, BlockPos pos, BlockState state,
                @Nullable BlockEntity blockEntity) {
            BlockPos denied = target;
            return denied == null || !pos.equals(denied);
        }
    }

    private interface FieldAction {
        void at(int dx, int dz);
    }

    private static void forEachFieldPos(FieldAction action) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                action.at(dx, dz);
            }
        }
    }

    private static BlockPos farmOf(BlockPos crop) {
        return crop.below();
    }

    private static void fillField(GameTestHelper helper, BlockState crop) {
        forEachFieldPos((dx, dz) -> {
            BlockPos pos = CENTER.offset(dx, 0, dz);
            helper.setBlock(farmOf(pos), Blocks.FARMLAND);
            helper.setBlock(pos, crop);
        });
    }

    private static ServerPlayer survivalScyther(GameTestHelper helper, net.minecraft.world.item.Item scythe) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(scythe));
        return player;
    }

    private static void breakCenter(GameTestHelper helper, ServerPlayer player) {
        player.gameMode.destroyBlock(helper.absolutePos(CENTER));
    }
}
