package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.attachment.SoilData;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.harvest.HarvestHandler;
import com.rfizzle.cultivation.item.CultivationItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.rfizzle.cultivation.gametest.SoilFixtures.CROP;
import static com.rfizzle.cultivation.gametest.SoilFixtures.FARM;
import static com.rfizzle.cultivation.gametest.SoilFixtures.TEMPLATE;
import static com.rfizzle.cultivation.gametest.SoilFixtures.matureWheat;
import static com.rfizzle.cultivation.gametest.SoilFixtures.placeTrackedFarmland;

/**
 * SPEC §6 Compost Fertilizer: the composter yields it at level 8 (player and
 * hopper paths), a use fills a farmland dose, and the harvest choke point spends
 * one dose per mature harvest for a guaranteed bonus — suppressed and unspent on
 * exhausted ground, stacking with §5. Config-flipping tests run in their own batch.
 */
public class FertilizerGameTest implements FabricGameTest {
    /**
     * Double the 100-tick default. These are the config-toggle tests: each flips a
     * field, drives the full in-world path behind it, and restores the field in a
     * finally. They pay for a config reload plus the same tick budget the untoggled
     * test needs, and a timeout here fails the restore as well as the assertion.
     */
    private static final int CONFIG_TOGGLE_TIMEOUT = 200;
    /**
     * The 100-tick default, restated rather than inherited. The hopper transfer is
     * the one test here whose runtime is set by vanilla's transfer cooldown rather
     * than by Cultivation's own work, so it should not silently inherit a future
     * change to the framework default.
     */
    private static final int HOPPER_TIMEOUT = 100;

    private static final String CONFIG_BATCH = "cultivationFertilizerConfig";

    // --- Composter seam ---

    @GameTest(template = TEMPLATE)
    public void composterPlayerExtractionYieldsFertilizer(GameTestHelper helper) {
        BlockPos abs = extractFromLevel8Composter(helper);
        ServerLevel level = helper.getLevel();
        helper.assertTrue(countItems(level, abs, CultivationItems.FERTILIZER) == 1,
                "a level-8 composter emptied by a player must yield Fertilizer");
        helper.assertTrue(countItems(level, abs, Items.BONE_MEAL) == 0,
                "no bone meal must drop while the feature is on");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = HOPPER_TIMEOUT)
    public void hopperExtractionYieldsFertilizer(GameTestHelper helper) {
        // Composter (level 8) above a hopper above a chest: the hopper pulls the
        // output down on its cooldown, exercising getContainer + the output
        // container's take-through-face gate.
        BlockPos chest = new BlockPos(3, 1, 3);
        BlockPos hopper = new BlockPos(3, 2, 3);
        BlockPos composter = new BlockPos(3, 3, 3);
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(hopper, Blocks.HOPPER);
        helper.setBlock(composter, Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, 8));
        helper.succeedWhen(() -> helper.assertContainerContains(chest, CultivationItems.FERTILIZER));
    }

    // --- Application ---

    @GameTest(template = TEMPLATE)
    public void applyToFarmlandSetsFullDoseAndConsumes(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.FARMLAND);
        Use use = useFertilizer(helper, FARM, Direction.UP, 2);
        helper.assertTrue(use.result.consumesAction(), "a use on farmland must succeed");
        helper.assertTrue(use.stack.getCount() == 1, "the use must consume exactly one item");
        SoilData data = SoilFixtures.data(helper, FARM);
        helper.assertTrue(data != null && data.fertilizerRemaining() == CultivationConfig.get().fertilizerDoseHarvests,
                "the use must set a full dose");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void partialDoseTopsUpToFull(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 80.0F, Blocks.WHEAT);
        SoilStores.update(helper.getLevel(), helper.absolutePos(FARM), false,
                data -> data.withFertilizerRemaining(3));
        Use use = useFertilizer(helper, FARM, Direction.UP, 1);
        helper.assertTrue(use.result.consumesAction(), "topping up a partial dose must succeed");
        helper.assertTrue(use.stack.isEmpty(), "the top-up must consume the item");
        SoilData data = SoilFixtures.data(helper, FARM);
        helper.assertTrue(data != null && data.fertilizerRemaining() == CultivationConfig.get().fertilizerDoseHarvests,
                "topping up must reset the dose to full");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void fullDoseFailsWithoutConsuming(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.FARMLAND);
        int full = CultivationConfig.get().fertilizerDoseHarvests;
        SoilStores.update(helper.getLevel(), helper.absolutePos(FARM), false,
                data -> data.withFertilizerRemaining(full));
        Use use = useFertilizer(helper, FARM, Direction.UP, 2);
        helper.assertTrue(!use.result.consumesAction(), "a use at a full dose must fail");
        helper.assertTrue(use.stack.getCount() == 2, "a failed use must not consume the item");
        SoilData data = SoilFixtures.data(helper, FARM);
        helper.assertTrue(data != null && data.fertilizerRemaining() == full, "the dose must stay full");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void useOnCropAppliesBeneathAndNeverAdvancesAge(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.FARMLAND);
        helper.setBlock(CROP, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3));
        Use use = useFertilizer(helper, CROP, Direction.UP, 1);
        helper.assertTrue(use.result.consumesAction(), "a use on a crop must apply to the farmland beneath");
        SoilData data = SoilFixtures.data(helper, FARM);
        helper.assertTrue(data != null && data.fertilizerRemaining() == CultivationConfig.get().fertilizerDoseHarvests,
                "the dose must land on the farmland under the crop");
        helper.assertTrue(helper.getBlockState(CROP).getValue(CropBlock.AGE) == 3,
                "Fertilizer must never advance crop age");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void useOnNonFarmlandDoesNothing(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.STONE);
        Use use = useFertilizer(helper, FARM, Direction.UP, 1);
        helper.assertTrue(!use.result.consumesAction(), "a use on non-farmland must pass");
        helper.assertTrue(use.stack.getCount() == 1, "a use on non-farmland must not consume the item");
        helper.assertTrue(SoilFixtures.data(helper, FARM) == null, "a use on non-farmland must write no soil entry");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void useOnNonCropRestingOnFarmlandDoesNothing(GameTestHelper helper) {
        // A torch sits on farmland without reverting it; Fertilizer is "not
        // usable on any other block", so a click on the torch must not dose below.
        placeTrackedFarmland(helper, FARM, 80.0F, Blocks.WHEAT);
        helper.setBlock(CROP, Blocks.TORCH);
        Use use = useFertilizer(helper, CROP, Direction.UP, 1);
        helper.assertTrue(!use.result.consumesAction(), "a use on a non-crop block must pass");
        helper.assertTrue(use.stack.getCount() == 1, "a use on a non-crop block must not consume the item");
        SoilData data = SoilFixtures.data(helper, FARM);
        helper.assertTrue(data != null && data.fertilizerRemaining() == 0,
                "a use on a non-crop block must not dose the farmland beneath it");
        helper.succeed();
    }

    // --- Harvest bonus ---

    @GameTest(template = TEMPLATE)
    public void dosedHarvestAddsOneAndDecrements(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 80.0F, Blocks.WHEAT);
        ServerLevel level = helper.getLevel();
        SoilStores.update(level, helper.absolutePos(FARM), false, data -> data.withFertilizerRemaining(2));

        List<ItemStack> first = harvest(helper, level);
        helper.assertTrue(count(first, Items.WHEAT) == 2, "a dosed harvest must append +1 primary product");
        helper.assertTrue(SoilFixtures.data(helper, FARM).fertilizerRemaining() == 1,
                "the harvest must spend one dose");

        List<ItemStack> second = harvest(helper, level);
        helper.assertTrue(count(second, Items.WHEAT) == 2, "the bonus rides every harvest with a live dose");
        SoilData spent = SoilFixtures.data(helper, FARM);
        helper.assertTrue(spent == null || spent.fertilizerRemaining() == 0, "the dose must count down to zero");

        List<ItemStack> third = harvest(helper, level);
        helper.assertTrue(count(third, Items.WHEAT) == 1, "a spent dose pays no bonus");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void exhaustedSoilSuppressesBonusAndSpendsNoDose(GameTestHelper helper) {
        // Post-drain fertility lands on 0: the clamp fires, the bonus must not,
        // and the dose must be preserved — exhausted ground never spends a dose.
        placeTrackedFarmland(helper, FARM, 1.0F, Blocks.WHEAT);
        ServerLevel level = helper.getLevel();
        SoilStores.update(level, helper.absolutePos(FARM), false, data -> data.withFertilizerRemaining(5));

        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Items.WHEAT, 2), new ItemStack(Items.WHEAT_SEEDS, 3)));
        HarvestHandler.onDropsResolved(matureWheat(), level, helper.absolutePos(CROP), null, drops);

        helper.assertTrue(count(drops, Items.WHEAT) == 1, "exhausted ground must clamp and suppress the bonus");
        helper.assertTrue(SoilFixtures.data(helper, FARM).fertilizerRemaining() == 5,
                "exhausted ground must not spend a dose");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void bonusStacksWithEnrichedTilling(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 80.0F, Blocks.WHEAT);
        ServerLevel level = helper.getLevel();
        SoilStores.update(level, helper.absolutePos(FARM), false,
                data -> data.withEnrichedChance(100).withFertilizerRemaining(3));

        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Items.WHEAT)));
        HarvestHandler.onDropsResolved(matureWheat(), level, helper.absolutePos(CROP), null, drops);

        helper.assertTrue(count(drops, Items.WHEAT) == 3, "base + enriched + fertilizer must all stack");
        helper.assertTrue(SoilFixtures.data(helper, FARM).fertilizerRemaining() == 2,
                "the fertilizer dose still decrements when stacked with enriched");
        helper.succeed();
    }

    // --- Config toggles (own batch) ---

    @GameTest(template = TEMPLATE, batch = CONFIG_BATCH, timeoutTicks = CONFIG_TOGGLE_TIMEOUT)
    public void composterRevertsToBoneMealWhenProductionOff(GameTestHelper helper) {
        CultivationConfig config = CultivationConfig.get();
        boolean saved = config.composterProducesFertilizer;
        config.composterProducesFertilizer = false;
        try {
            BlockPos abs = extractFromLevel8Composter(helper);
            ServerLevel level = helper.getLevel();
            helper.assertTrue(countItems(level, abs, Items.BONE_MEAL) == 1,
                    "composterProducesFertilizer=false must restore bone meal");
            helper.assertTrue(countItems(level, abs, CultivationItems.FERTILIZER) == 0,
                    "no Fertilizer must drop while production is off");
            helper.succeed();
        } finally {
            config.composterProducesFertilizer = saved;
        }
    }

    @GameTest(template = TEMPLATE, batch = CONFIG_BATCH, timeoutTicks = CONFIG_TOGGLE_TIMEOUT)
    public void disabledFertilizerIsInertButRetainsCounters(GameTestHelper helper) {
        CultivationConfig config = CultivationConfig.get();
        boolean saved = config.enableFertilizer;
        config.enableFertilizer = false;
        try {
            // The composter reverts to bone meal regardless of the production flag…
            BlockPos abs = extractFromLevel8Composter(helper);
            ServerLevel level = helper.getLevel();
            helper.assertTrue(countItems(level, abs, Items.BONE_MEAL) == 1,
                    "enableFertilizer=false must revert the composter to bone meal");

            // …application is inert and consumes nothing…
            helper.setBlock(FARM, Blocks.FARMLAND);
            Use use = useFertilizer(helper, FARM, Direction.UP, 1);
            helper.assertTrue(!use.result.consumesAction() && use.stack.getCount() == 1,
                    "a disabled use must not apply or consume");

            // …a stored dose is retained untouched and pays no bonus.
            SoilStores.update(level, helper.absolutePos(FARM), false, data -> data.withFertilizerRemaining(4));
            List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Items.WHEAT)));
            HarvestHandler.onDropsResolved(matureWheat(), level, helper.absolutePos(CROP), null, drops);
            helper.assertTrue(count(drops, Items.WHEAT) == 1, "a disabled toggle must suppress the bonus");
            helper.assertTrue(SoilFixtures.data(helper, FARM).fertilizerRemaining() == 4,
                    "a disabled toggle must retain the stored dose untouched");
            helper.succeed();
        } finally {
            config.enableFertilizer = saved;
        }
    }

    // --- Helpers ---

    private record Use(InteractionResult result, ItemStack stack) {
    }

    /** Sets a level-8 composter at {@link SoilFixtures#FARM} and empties it via the player path. Returns its absolute pos. */
    private static BlockPos extractFromLevel8Composter(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, 8));
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(FARM);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ComposterBlock.extractProduce(player, level.getBlockState(abs), level, abs);
        player.discard();
        return abs;
    }

    private static Use useFertilizer(GameTestHelper helper, BlockPos targetRel, Direction face, int count) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(CultivationItems.FERTILIZER, count);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = helper.absolutePos(targetRel);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), face, abs, false);
        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        player.discard();
        return new Use(result, stack);
    }

    /** Runs a fresh single-wheat drop list of mature wheat over {@link SoilFixtures#CROP} through the choke point. */
    private static List<ItemStack> harvest(GameTestHelper helper, ServerLevel level) {
        BlockPos cropAbs = helper.absolutePos(CROP);
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Items.WHEAT)));
        return HarvestHandler.onDropsResolved(matureWheat(), level, cropAbs, null, drops);
    }

    private static int countItems(ServerLevel level, BlockPos around, Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(around).inflate(3.0)).stream()
                .filter(entity -> entity.getItem().is(item))
                .mapToInt(entity -> entity.getItem().getCount())
                .sum();
    }

    private static int count(List<ItemStack> drops, Item item) {
        return drops.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
}
