package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.attachment.SoilData;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.harvest.HarvestHandler;
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
 * SPEC §5 Enriched Tilling: the till seam records by hoe tier, the harvest
 * choke point rolls the recorded chance, reversion clears it, and the
 * exhausted clamp suppresses it. Config-flipping tests run in their own batch.
 */
public class EnrichedTillingGameTest implements FabricGameTest {
    private static final String CONFIG_BATCH = "cultivationEnrichedConfig";

    @GameTest(template = TEMPLATE)
    public void diamondHoeTillRecordsItsChance(GameTestHelper helper) {
        InteractionResult result = till(helper, Items.DIAMOND_HOE, GameType.SURVIVAL);
        helper.assertTrue(result.consumesAction(), "a diamond hoe on open dirt must till");
        helper.assertBlockPresent(Blocks.FARMLAND, FARM);
        SoilData data = SoilFixtures.data(helper, FARM);
        helper.assertTrue(data != null && data.enrichedChance() == CultivationConfig.get().diamondHoeEnrichChance,
                "diamond tilling must record the configured 10% chance");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void netheriteHoeTillRecordsItsChance(GameTestHelper helper) {
        till(helper, Items.NETHERITE_HOE, GameType.SURVIVAL);
        SoilData data = SoilFixtures.data(helper, FARM);
        helper.assertTrue(data != null && data.enrichedChance() == CultivationConfig.get().netheriteHoeEnrichChance,
                "netherite tilling must record the configured 15% chance");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void lowTierTillLeavesNoEntry(GameTestHelper helper) {
        till(helper, Items.IRON_HOE, GameType.SURVIVAL);
        helper.assertBlockPresent(Blocks.FARMLAND, FARM);
        helper.assertTrue(SoilFixtures.data(helper, FARM) == null,
                "a 0-chance till on pristine ground must not create a soil entry");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void creativeTillingCountsNormally(GameTestHelper helper) {
        till(helper, Items.DIAMOND_HOE, GameType.CREATIVE);
        SoilData data = SoilFixtures.data(helper, FARM);
        helper.assertTrue(data != null && data.enrichedChance() == CultivationConfig.get().diamondHoeEnrichChance,
                "creative tilling must record the tier chance like survival");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void retillOverwritesWithTheNewHoe(GameTestHelper helper) {
        // A tracked dirt position carrying soil memory: the new till records
        // whatever hoe is used, including a low tier's 0.
        helper.setBlock(FARM, Blocks.DIRT);
        ServerLevel level = helper.getLevel();
        BlockPos farmAbs = helper.absolutePos(FARM);
        SoilStores.update(level, farmAbs, false,
                data -> data.withFertility(50.0F).withLastCrop(SoilFixtures.idOf(Blocks.WHEAT)).withEnrichedChance(25));

        till(helper, Items.IRON_HOE, GameType.SURVIVAL);

        SoilData data = SoilFixtures.data(helper, FARM);
        helper.assertTrue(data != null && data.enrichedChance() == 0,
                "re-tilling must record the new hoe's tier, a low tier resetting to 0");
        helper.assertTrue(data.fertility() > 49.0F && data.fertility() < 51.0F,
                "re-tilling must not disturb the position's fertility memory");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void reversionClearsEnrichmentAndKeepsSoilMemory(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 50.0F, Blocks.WHEAT);
        ServerLevel level = helper.getLevel();
        BlockPos farmAbs = helper.absolutePos(FARM);
        SoilStores.update(level, farmAbs, false, data -> data.withEnrichedChance(100));

        helper.setBlock(FARM, Blocks.DIRT); // reversion: the onRemove seam fires

        SoilData data = SoilFixtures.data(helper, FARM);
        helper.assertTrue(data != null, "reversion must keep the soil entry's memory");
        helper.assertTrue(data.enrichedChance() == 0, "reversion must clear the enriched chance");
        helper.assertTrue(data.fertilizerRemaining() == 0, "reversion must clear the fertilizer dose");
        helper.assertTrue(data.fertility() > 49.0F && data.fertility() < 51.0F,
                "fertility must persist through reversion (small settle accrual aside)");
        helper.assertTrue(data.lastCrop().map(SoilFixtures.idOf(Blocks.WHEAT)::equals).orElse(false),
                "rotation memory must persist through reversion");

        // Re-tilling rolls a fresh value from whatever hoe is used.
        till(helper, Items.NETHERITE_HOE, GameType.SURVIVAL);
        SoilData retilled = SoilFixtures.data(helper, FARM);
        helper.assertTrue(retilled != null
                        && retilled.enrichedChance() == CultivationConfig.get().netheriteHoeEnrichChance,
                "re-tilling after reversion must record the new hoe's tier");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void failedTillWritesNothing(GameTestHelper helper) {
        // Occupied above: vanilla's tillable predicate fails and no till happens.
        helper.setBlock(FARM, Blocks.DIRT);
        helper.setBlock(CROP, Blocks.SMOOTH_STONE);
        InteractionResult blocked = till(helper, Items.DIAMOND_HOE, GameType.SURVIVAL);
        helper.assertTrue(!blocked.consumesAction(), "a blocked-above till attempt must pass");
        helper.assertBlockPresent(Blocks.DIRT, FARM);
        helper.assertTrue(SoilFixtures.data(helper, FARM) == null,
                "a failed till must never write a soil entry");

        // Clicked from below: same predicate, same non-result.
        helper.setBlock(CROP, Blocks.AIR);
        InteractionResult fromBelow = tillWithHit(helper, Items.DIAMOND_HOE, GameType.SURVIVAL,
                new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(FARM)), Direction.DOWN,
                        helper.absolutePos(FARM), false));
        helper.assertTrue(!fromBelow.consumesAction(), "a from-below till attempt must pass");
        helper.assertTrue(SoilFixtures.data(helper, FARM) == null,
                "a from-below till must never write a soil entry");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void forcedChanceAddsOneProductPerHarvest(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.FARMLAND);
        ServerLevel level = helper.getLevel();
        BlockPos farmAbs = helper.absolutePos(FARM);
        BlockPos cropAbs = helper.absolutePos(CROP);
        SoilStores.update(level, farmAbs, false, data -> data.withEnrichedChance(100));

        // Mature wheat's base loot is exactly 1 wheat, so +1 is deterministic.
        for (int harvest = 1; harvest <= 2; harvest++) {
            helper.setBlock(CROP, matureWheat());
            level.destroyBlock(cropAbs, true);
            int wheat = countItems(level, cropAbs, Items.WHEAT);
            helper.assertTrue(wheat == 2,
                    "harvest " + harvest + " at 100% must yield base 1 + bonus 1 wheat, got " + wheat);
            clearItemEntities(level, cropAbs);
        }

        SoilData data = SoilFixtures.data(helper, FARM);
        helper.assertTrue(data != null && data.enrichedChance() == 100,
                "the enriched chance must survive repeated harvests");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void enrichedRollAppendsThePrimaryProduct(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 80.0F, Blocks.CARROTS);
        ServerLevel level = helper.getLevel();
        SoilStores.update(level, helper.absolutePos(FARM), false, data -> data.withEnrichedChance(100));

        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT_SEEDS, 2)));
        HarvestHandler.onDropsResolved(matureWheat(), level, helper.absolutePos(CROP), null, drops);

        helper.assertTrue(count(drops, Items.WHEAT) == 2, "the bonus must append +1 primary product");
        helper.assertTrue(count(drops, Items.WHEAT_SEEDS) == 2, "the bonus must never touch seeds");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void exhaustedSoilSuppressesTheBonus(GameTestHelper helper) {
        // Post-drain fertility lands on 0: the clamp fires and the 100% bonus must not.
        placeTrackedFarmland(helper, FARM, 1.0F, Blocks.CARROTS);
        ServerLevel level = helper.getLevel();
        SoilStores.update(level, helper.absolutePos(FARM), false, data -> data.withEnrichedChance(100));

        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Items.WHEAT, 2), new ItemStack(Items.WHEAT_SEEDS, 3)));
        HarvestHandler.onDropsResolved(matureWheat(), level, helper.absolutePos(CROP), null, drops);

        helper.assertTrue(count(drops, Items.WHEAT) == 1, "exhausted ground must clamp and suppress the bonus");
        helper.assertTrue(count(drops, Items.WHEAT_SEEDS) == 1, "the exhausted clamp caps seeds at 1");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = CONFIG_BATCH, timeoutTicks = 200)
    public void disabledToggleIsInert(GameTestHelper helper) {
        CultivationConfig config = CultivationConfig.get();
        boolean saved = config.enableEnrichedTilling;
        config.enableEnrichedTilling = false;
        try {
            // No write at till time…
            till(helper, Items.DIAMOND_HOE, GameType.SURVIVAL);
            helper.assertTrue(SoilFixtures.data(helper, FARM) == null,
                    "a disabled toggle must prevent the till-time write");

            // …and no roll at harvest time, even over a recorded chance.
            ServerLevel level = helper.getLevel();
            SoilStores.update(level, helper.absolutePos(FARM), false, data -> data.withEnrichedChance(100));
            List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Items.WHEAT)));
            HarvestHandler.onDropsResolved(matureWheat(), level, helper.absolutePos(CROP), null, drops);
            helper.assertTrue(count(drops, Items.WHEAT) == 1,
                    "a disabled toggle must suppress the harvest roll");
            helper.succeed();
        } finally {
            config.enableEnrichedTilling = saved;
        }
    }

    @GameTest(template = TEMPLATE, batch = CONFIG_BATCH, timeoutTicks = 200)
    public void configuredChanceIsRespectedEndToEnd(GameTestHelper helper) {
        CultivationConfig config = CultivationConfig.get();
        int saved = config.diamondHoeEnrichChance;
        config.diamondHoeEnrichChance = 100;
        try {
            till(helper, Items.DIAMOND_HOE, GameType.SURVIVAL);
            SoilData data = SoilFixtures.data(helper, FARM);
            helper.assertTrue(data != null && data.enrichedChance() == 100,
                    "the configured diamond chance must be what tilling records");

            ServerLevel level = helper.getLevel();
            BlockPos cropAbs = helper.absolutePos(CROP);
            helper.setBlock(CROP, matureWheat());
            level.destroyBlock(cropAbs, true);
            helper.assertTrue(countItems(level, cropAbs, Items.WHEAT) == 2,
                    "a 100% configured chance must add +1 wheat on every harvest");
            clearItemEntities(level, cropAbs);
            helper.succeed();
        } finally {
            config.diamondHoeEnrichChance = saved;
        }
    }

    /** Drives the real vanilla till path — {@code HoeItem#useOn} — on dirt at {@link SoilFixtures#FARM}. */
    private static InteractionResult till(GameTestHelper helper, Item hoe, GameType gameType) {
        if (!helper.getBlockState(FARM).is(Blocks.DIRT)) {
            helper.setBlock(FARM, Blocks.DIRT);
        }
        BlockPos farmAbs = helper.absolutePos(FARM);
        return tillWithHit(helper, hoe, gameType,
                new BlockHitResult(Vec3.atCenterOf(farmAbs), Direction.UP, farmAbs, false));
    }

    private static InteractionResult tillWithHit(
            GameTestHelper helper, Item hoe, GameType gameType, BlockHitResult hit) {
        Player player = helper.makeMockPlayer(gameType);
        ItemStack stack = new ItemStack(hoe);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        player.discard();
        return result;
    }

    private static int countItems(ServerLevel level, BlockPos around, Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(around).inflate(3.0)).stream()
                .filter(entity -> entity.getItem().is(item))
                .mapToInt(entity -> entity.getItem().getCount())
                .sum();
    }

    private static void clearItemEntities(ServerLevel level, BlockPos around) {
        level.getEntitiesOfClass(ItemEntity.class, new AABB(around).inflate(3.0)).forEach(Entity::discard);
    }

    private static int count(List<ItemStack> drops, Item item) {
        return drops.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
}
