package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.attachment.SoilData;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.soil.SoilRecovery;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

import static com.rfizzle.cultivation.gametest.SoilFixtures.CROP;
import static com.rfizzle.cultivation.gametest.SoilFixtures.FARM;
import static com.rfizzle.cultivation.gametest.SoilFixtures.TEMPLATE;
import static com.rfizzle.cultivation.gametest.SoilFixtures.assertFertility;
import static com.rfizzle.cultivation.gametest.SoilFixtures.idOf;
import static com.rfizzle.cultivation.gametest.SoilFixtures.matureCarrots;
import static com.rfizzle.cultivation.gametest.SoilFixtures.matureWheat;
import static com.rfizzle.cultivation.gametest.SoilFixtures.placeTrackedFarmland;

/**
 * The drain half of SPEC §1: actor-agnostic harvest drain through the choke
 * point, the exhausted yield clamp, and the zero-data invariant.
 */
public class SoilDrainGameTest implements FabricGameTest {

    @GameTest(template = TEMPLATE)
    public void firstHarvestDrainsAsRotated(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.FARMLAND);
        helper.setBlock(CROP, matureWheat());
        helper.getLevel().destroyBlock(helper.absolutePos(CROP), true);

        assertFertility(helper, FARM, 98.5F, "first-ever harvest must drain at the rotated rate");
        SoilData data = SoilFixtures.data(helper, FARM);
        helper.assertTrue(data != null && data.lastCrop().map(idOf(Blocks.WHEAT)::equals).orElse(false),
                "harvest must record rotation memory");
        helper.assertTrue(helper.getLevel().getChunkAt(helper.absolutePos(FARM)).isUnsaved(),
                "a soil write must mark the chunk unsaved");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void sameCropDrainsFullThree(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 100.0F, Blocks.WHEAT);
        helper.setBlock(CROP, matureWheat());
        helper.getLevel().destroyBlock(helper.absolutePos(CROP), true);
        assertFertility(helper, FARM, 97.0F, "same-crop harvest must drain 3.0");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void rotatedCropDrainsHalf(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 100.0F, Blocks.WHEAT);
        helper.setBlock(CROP, matureCarrots());
        helper.getLevel().destroyBlock(helper.absolutePos(CROP), true);
        assertFertility(helper, FARM, 98.5F, "rotated harvest must drain 1.5");
        SoilData data = SoilFixtures.data(helper, FARM);
        helper.assertTrue(data != null && data.lastCrop().map(idOf(Blocks.CARROTS)::equals).orElse(false),
                "rotation memory must follow the newest harvest");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void survivalPlayerBreakDrains(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.FARMLAND);
        helper.setBlock(CROP, matureWheat());
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.gameMode.destroyBlock(helper.absolutePos(CROP));

        assertFertility(helper, FARM, 98.5F, "a survival player break must drain");
        helper.assertItemEntityPresent(Items.WHEAT, CROP, 2.0);
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void creativeBreakNeverDrains(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.FARMLAND);
        helper.setBlock(CROP, matureWheat());
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        player.setGameMode(GameType.CREATIVE);
        player.gameMode.destroyBlock(helper.absolutePos(CROP));

        helper.assertBlockPresent(Blocks.AIR, CROP);
        helper.assertTrue(SoilFixtures.data(helper, FARM) == null,
                "a creative break produces no drops and must not drain");
        helper.assertItemEntityNotPresent(Items.WHEAT, CROP, 2.0);
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void immatureBreakNeverDrains(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.FARMLAND);
        helper.setBlock(CROP, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3));
        helper.getLevel().destroyBlock(helper.absolutePos(CROP), true);
        helper.assertTrue(SoilFixtures.data(helper, FARM) == null,
                "breaking an immature crop must not create or drain soil state");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void pistonDestructionDrains(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.FARMLAND);
        helper.setBlock(CROP, matureWheat());
        helper.setBlock(new BlockPos(2, 2, 3), Blocks.PISTON.defaultBlockState()
                .setValue(DirectionalBlock.FACING, net.minecraft.core.Direction.EAST));
        helper.setBlock(new BlockPos(1, 2, 3), Blocks.REDSTONE_BLOCK);

        helper.succeedWhen(() ->
                assertFertility(helper, FARM, 98.5F, "a piston destroying a mature crop must drain"));
    }

    @GameTest(template = TEMPLATE)
    public void explosionDestructionDrains(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.FARMLAND);
        helper.setBlock(CROP, matureWheat());
        ServerLevel level = helper.getLevel();
        BlockPos cropAbs = helper.absolutePos(CROP);

        // Explosions resolve drops in onExplosionHit, never through dropResources —
        // drive that exact seam with a real Explosion, deterministically.
        Explosion explosion = new Explosion(level, null,
                cropAbs.getX() + 0.5, cropAbs.getY() + 0.5, cropAbs.getZ() + 0.5,
                1.0F, false, Explosion.BlockInteraction.DESTROY);
        level.getBlockState(cropAbs).onExplosionHit(level, cropAbs, explosion, (stack, pos) -> {
        });

        helper.assertBlockPresent(Blocks.AIR, CROP);
        assertFertility(helper, FARM, 98.5F, "an explosion destroying a mature crop must drain identically");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void pitcherLowerHalfDrainsOnce(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.FARMLAND);
        BlockState mature = Blocks.PITCHER_CROP.defaultBlockState()
                .setValue(PitcherCropBlock.AGE, PitcherCropBlock.MAX_AGE);
        helper.setBlock(CROP, mature.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(CROP.above(), mature.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));

        helper.getLevel().destroyBlock(helper.absolutePos(CROP), true);

        assertFertility(helper, FARM, 98.5F, "the pitcher's lower half is the one drops-carrying harvest");
        helper.assertItemEntityPresent(Items.PITCHER_PLANT, CROP, 2.0);
        helper.assertBlockPresent(Blocks.AIR, CROP.above());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void pitcherUpperHalfBreakDrainsOnce(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.FARMLAND);
        BlockState mature = Blocks.PITCHER_CROP.defaultBlockState()
                .setValue(PitcherCropBlock.AGE, PitcherCropBlock.MAX_AGE);
        helper.setBlock(CROP, mature.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(CROP.above(), mature.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));

        helper.getLevel().destroyBlock(helper.absolutePos(CROP.above()), true);

        // The upper half's own loot is empty (vanilla conditions all pitcher drops
        // on half=lower); the orphaned lower half is then destroyed WITH drops via
        // updateOrDestroy. Exactly one drops-resolution — the lower's — so exactly
        // one drain, whichever half was broken.
        helper.assertBlockPresent(Blocks.AIR, CROP);
        assertFertility(helper, FARM, 98.5F, "an upper-half break harvests through the lower half exactly once");
        helper.assertItemEntityPresent(Items.PITCHER_PLANT, CROP, 2.0);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void torchflowerMaturesAsFlowerAndDrains(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.FARMLAND);
        helper.setBlock(CROP, Blocks.TORCHFLOWER);
        helper.getLevel().destroyBlock(helper.absolutePos(CROP), true);

        assertFertility(helper, FARM, 98.5F, "harvesting the mature torchflower must drain");
        SoilData data = SoilFixtures.data(helper, FARM);
        helper.assertTrue(data != null && data.lastCrop().map(idOf(Blocks.TORCHFLOWER_CROP)::equals).orElse(false),
                "torchflower rotation memory must record the plantable crop id");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void torchflowerCropBlockIsAlwaysImmature(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.FARMLAND);
        helper.setBlock(CROP, Blocks.TORCHFLOWER_CROP.defaultBlockState()
                .setValue(BlockStateProperties.AGE_1, 1));
        helper.getLevel().destroyBlock(helper.absolutePos(CROP), true);
        helper.assertTrue(SoilFixtures.data(helper, FARM) == null,
                "the torchflower crop block never counts as a mature harvest");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void stemsNeverDrain(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.FARMLAND);
        helper.setBlock(CROP, Blocks.MELON_STEM.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7));
        helper.getLevel().destroyBlock(helper.absolutePos(CROP), true);
        helper.assertTrue(SoilFixtures.data(helper, FARM) == null,
                "stems receive the growth modifier but must never drain");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void exhaustedHarvestClampsToOneCarrot(GameTestHelper helper) {
        // 1.0 - 1.5 (rotated) clamps to 0: this harvest is the exhausted one.
        placeTrackedFarmland(helper, FARM, 1.0F, Blocks.WHEAT);
        helper.setBlock(CROP, matureCarrots());
        helper.getLevel().destroyBlock(helper.absolutePos(CROP), true);

        assertFertility(helper, FARM, 0.0F, "the draining harvest must clamp at 0");
        // Unclamped mature carrots drop 2-5; the exhausted clamp makes exactly 1 deterministic.
        helper.assertItemEntityCountIs(Items.CARROT, CROP, 2.0, 1);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void exhaustedWheatKeepsOneProductAndOneSeed(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 0.0F, Blocks.WHEAT);
        helper.setBlock(CROP, matureWheat());
        helper.getLevel().destroyBlock(helper.absolutePos(CROP), true);

        assertFertility(helper, FARM, 0.0F, "fertility stays clamped at 0");
        helper.assertItemEntityCountIs(Items.WHEAT, CROP, 2.0, 1);
        helper.assertTrue(countItems(helper, Items.WHEAT_SEEDS) <= 1,
                "exhausted wheat must drop at most 1 seed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void untouchedGroundStoresNoData(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.FARMLAND);
        SoilRecovery.onFarmlandRandomTick(helper.getLevel(), helper.absolutePos(FARM));
        helper.setBlock(CROP, Blocks.WHEAT.defaultBlockState());
        helper.getLevel().destroyBlock(helper.absolutePos(CROP), true);

        helper.assertTrue(SoilFixtures.data(helper, FARM) == null,
                "random ticks and immature breaks must never create soil entries");
        helper.succeed();
    }

    private static int countItems(GameTestHelper helper, Item item) {
        AABB bounds = new AABB(helper.absolutePos(CROP)).inflate(3.0);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds).stream()
                .filter(entity -> entity.getItem().is(item))
                .mapToInt(entity -> entity.getItem().getCount())
                .sum();
    }
}
