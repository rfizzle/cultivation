package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.attachment.SoilData;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.item.CultivationItems;
import com.rfizzle.cultivation.soil.FallowGateThrottle;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

import static com.rfizzle.cultivation.gametest.SoilFixtures.CROP;
import static com.rfizzle.cultivation.gametest.SoilFixtures.FARM;
import static com.rfizzle.cultivation.gametest.SoilFixtures.TEMPLATE;
import static com.rfizzle.cultivation.gametest.SoilFixtures.matureWheat;
import static com.rfizzle.cultivation.gametest.SoilFixtures.placeTrackedFarmland;

/**
 * SPEC §8 villager field stewardship: the farmer's fallow hysteresis, rotation
 * preference, and Fertilizer upkeep, driven by invoking the real
 * {@code HarvestFarmland} behavior on a spawned farmer rather than waiting on the
 * brain schedule. Config-flipping tests run in their own batch.
 *
 * <p>Layout: farmland at {@link SoilFixtures#FARM}, the work block above at
 * {@link SoilFixtures#CROP}, and the farmer standing on the farmland so
 * {@code CROP} is its own work position.
 */
public class VillagerStewardshipGameTest implements FabricGameTest {
    private static final String CONFIG_BATCH = "cultivationStewardshipConfig";

    // --- Fallow discipline & hysteresis ---

    @GameTest(template = TEMPLATE)
    public void skipsReplantingBelowFallowThreshold(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 20.0F, Blocks.WHEAT); // Tired band, below the 25 default
        Villager farmer = spawnFarmer(helper, new ItemStack(Items.WHEAT_SEEDS, 4));
        driveWork(helper, farmer);

        helper.assertTrue(helper.getBlockState(CROP).isAir(), "a fallow block below the threshold must stay unplanted");
        helper.assertTrue(SoilFixtures.data(helper, FARM).villagerFallow(), "the fallow latch must be set");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void resumesReplantingOnceRecoveredToReplantThreshold(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 60.0F, Blocks.WHEAT); // recovered past the 50 default
        latch(helper, true); // was resting; recovery should release it
        Villager farmer = spawnFarmer(helper, new ItemStack(Items.WHEAT_SEEDS, 4));
        driveWork(helper, farmer);

        helper.assertBlockPresent(Blocks.WHEAT, CROP);
        SoilData data = SoilFixtures.data(helper, FARM);
        helper.assertTrue(data == null || !data.villagerFallow(), "recovery to the replant threshold must clear the latch");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void midBandHonoursTheLatchWhenResting(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 35.0F, Blocks.WHEAT); // between the two thresholds
        latch(helper, true);
        Villager farmer = spawnFarmer(helper, new ItemStack(Items.WHEAT_SEEDS, 4));
        driveWork(helper, farmer);

        helper.assertTrue(helper.getBlockState(CROP).isAir(), "a latched block in the hysteresis band must keep resting");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void midBandReplantsWhenNotLatched(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 35.0F, Blocks.WHEAT); // same band, but on the way down
        Villager farmer = spawnFarmer(helper, new ItemStack(Items.WHEAT_SEEDS, 4));
        driveWork(helper, farmer);

        helper.assertBlockPresent(Blocks.WHEAT, CROP);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void fallowGateThrottlesRecheckButHonoursRecovery(GameTestHelper helper) {
        // The whole engagement has to fit inside the behavior's default 60-tick
        // duration, or the last tick below stops the task instead of replanting.
        helper.assertTrue(FallowGateThrottle.INTERVAL_TICKS < 55,
                "the recheck interval must stay well inside the behavior's 60-tick duration");
        placeTrackedFarmland(helper, FARM, 20.0F, Blocks.WHEAT); // Tired band, below the 25 default
        Villager farmer = spawnFarmer(helper, new ItemStack(Items.WHEAT_SEEDS, 4));

        // One behavior instance across the whole engagement, so the gate's throttle
        // persists between ticks the way it does for a real parked farmer.
        ServerLevel level = helper.getLevel();
        HarvestFarmland behavior = new HarvestFarmland();
        long start = level.getGameTime();
        helper.assertTrue(behavior.tryStart(level, farmer, start), "the farmer work task must engage the field");

        behavior.tickOrStop(level, farmer, start + 1); // denies, and arms the throttle
        helper.assertTrue(helper.getBlockState(CROP).isAir(), "a fallow block below the threshold must stay unplanted");

        // Recovery lifts the plot past the replant threshold while the farmer stands on it.
        SoilStores.update(level, helper.absolutePos(FARM), false, data -> data.withFertility(60.0F));

        behavior.tickOrStop(level, farmer, start + 5);
        helper.assertTrue(helper.getBlockState(CROP).isAir(),
                "the gate must reuse its verdict inside the throttle interval rather than re-read the soil");

        behavior.tickOrStop(level, farmer, start + 1 + FallowGateThrottle.INTERVAL_TICKS);
        helper.assertBlockPresent(Blocks.WHEAT, CROP);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void harvestsMatureCropRegardlessOfFertility(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 5.0F, Blocks.WHEAT); // exhausted-adjacent, well below the fallow threshold
        helper.setBlock(CROP, matureWheat());
        Villager farmer = spawnFarmer(helper, new ItemStack(Items.WHEAT_SEEDS, 4));
        driveWork(helper, farmer);

        helper.assertTrue(helper.getBlockState(CROP).isAir(), "a mature crop must be harvested even on tired soil");
        helper.succeed();
    }

    // --- Rotation preference ---

    @GameTest(template = TEMPLATE)
    public void prefersASeedDifferingFromTheLastCrop(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 90.0F, Blocks.WHEAT); // last crop was wheat
        Villager farmer = spawnFarmer(helper); // seeds set in slot order below
        farmer.getInventory().setItem(0, new ItemStack(Items.WHEAT_SEEDS, 4));
        farmer.getInventory().setItem(1, new ItemStack(Items.CARROT, 4));
        driveWork(helper, farmer);

        helper.assertBlockPresent(Blocks.CARROTS, CROP);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void plantsTheOnlySeedItHasEvenIfItRepeats(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 90.0F, Blocks.WHEAT);
        Villager farmer = spawnFarmer(helper, new ItemStack(Items.WHEAT_SEEDS, 4)); // wheat is the only option
        driveWork(helper, farmer);

        helper.assertBlockPresent(Blocks.WHEAT, CROP);
        helper.succeed();
    }

    // --- Fertilizer upkeep ---

    @GameTest(template = TEMPLATE)
    public void dosesASpentBlockAndConsumesOneFertilizer(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 90.0F, Blocks.WHEAT); // dose defaults to 0 (spent)
        Villager farmer = spawnFarmer(helper, new ItemStack(CultivationItems.FERTILIZER, 3));
        driveWork(helper, farmer);

        SoilData data = SoilFixtures.data(helper, FARM);
        helper.assertTrue(data != null && data.fertilizerRemaining() == CultivationConfig.get().fertilizerDoseHarvests,
                "a farmer must dose a spent block to full");
        helper.assertTrue(farmer.getInventory().countItem(CultivationItems.FERTILIZER) == 2,
                "the dose must consume exactly one Fertilizer");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void neverTopsUpAPartialDose(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 90.0F, Blocks.WHEAT);
        SoilStores.update(helper.getLevel(), helper.absolutePos(FARM), false, data -> data.withFertilizerRemaining(3));
        Villager farmer = spawnFarmer(helper, new ItemStack(CultivationItems.FERTILIZER, 3));
        driveWork(helper, farmer);

        SoilData data = SoilFixtures.data(helper, FARM);
        helper.assertTrue(data != null && data.fertilizerRemaining() == 3, "a partial dose must be left untouched");
        helper.assertTrue(farmer.getInventory().countItem(CultivationItems.FERTILIZER) == 3,
                "no Fertilizer may be spent topping up a partial dose");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void farmerWantsFertilizerButNonFarmersDoNot(GameTestHelper helper) {
        Villager farmer = spawnFarmer(helper);
        helper.assertTrue(farmer.wantsToPickUp(new ItemStack(CultivationItems.FERTILIZER)),
                "a farmer must want to pick up Fertilizer");

        Villager nitwit = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
        nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
        helper.assertFalse(nitwit.wantsToPickUp(new ItemStack(CultivationItems.FERTILIZER)),
                "a non-farmer must not want Fertilizer");
        helper.succeed();
    }

    // --- Config toggles (own batch) ---

    @GameTest(template = TEMPLATE, batch = CONFIG_BATCH, timeoutTicks = 200)
    public void disabledStewardshipRestoresTheVanillaTask(GameTestHelper helper) {
        CultivationConfig config = CultivationConfig.get();
        boolean saved = config.enableVillagerStewardship;
        config.enableVillagerStewardship = false;
        try {
            placeTrackedFarmland(helper, FARM, 10.0F, Blocks.WHEAT); // below the threshold, yet vanilla replants
            Villager farmer = spawnFarmer(helper, new ItemStack(Items.WHEAT_SEEDS, 4));
            driveWork(helper, farmer);
            helper.assertBlockPresent(Blocks.WHEAT, CROP);
            helper.succeed();
        } finally {
            config.enableVillagerStewardship = saved;
        }
    }

    @GameTest(template = TEMPLATE, batch = CONFIG_BATCH, timeoutTicks = 200)
    public void disabledFertilizingSkipsDosingAndPickup(GameTestHelper helper) {
        CultivationConfig config = CultivationConfig.get();
        boolean saved = config.enableVillagerFertilizing;
        config.enableVillagerFertilizing = false;
        try {
            placeTrackedFarmland(helper, FARM, 90.0F, Blocks.WHEAT);
            Villager farmer = spawnFarmer(helper, new ItemStack(CultivationItems.FERTILIZER, 3));
            helper.assertFalse(farmer.wantsToPickUp(new ItemStack(CultivationItems.FERTILIZER)),
                    "fertilizing off must stop Fertilizer pickup");
            driveWork(helper, farmer);
            SoilData data = SoilFixtures.data(helper, FARM);
            helper.assertTrue(data == null || data.fertilizerRemaining() == 0, "fertilizing off must apply no dose");
            helper.assertTrue(farmer.getInventory().countItem(CultivationItems.FERTILIZER) == 3,
                    "fertilizing off must consume no Fertilizer");
            helper.succeed();
        } finally {
            config.enableVillagerFertilizing = saved;
        }
    }

    // --- Helpers ---

    private static Villager spawnFarmer(GameTestHelper helper, ItemStack... inventory) {
        ServerLevel level = helper.getLevel();
        level.getServer().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
        Villager farmer = helper.spawn(EntityType.VILLAGER, CROP);
        farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER));
        // HarvestFarmland requires this memory present to start; its value is not validated.
        farmer.getBrain().setMemory(MemoryModuleType.SECONDARY_JOB_SITE,
                List.of(GlobalPos.of(level.dimension(), helper.absolutePos(FARM))));
        for (int i = 0; i < inventory.length; i++) {
            farmer.getInventory().setItem(i, inventory[i]);
        }
        return farmer;
    }

    /** Drives the real farmland behavior for a couple of work ticks. */
    private static void driveWork(GameTestHelper helper, Villager farmer) {
        ServerLevel level = helper.getLevel();
        HarvestFarmland behavior = new HarvestFarmland();
        long time = level.getGameTime();
        helper.assertTrue(behavior.tryStart(level, farmer, time), "the farmer work task must engage the field");
        behavior.tickOrStop(level, farmer, time + 1);
        behavior.tickOrStop(level, farmer, time + 2);
    }

    private static void latch(GameTestHelper helper, boolean fallow) {
        SoilStores.update(helper.getLevel(), helper.absolutePos(FARM), false, data -> data.withVillagerFallow(fallow));
    }
}
