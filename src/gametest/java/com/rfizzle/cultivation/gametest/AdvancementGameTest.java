package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.Cultivation;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.diet.DietHandler;
import com.rfizzle.cultivation.harvest.HarvestHandler;
import com.rfizzle.cultivation.item.CultivationItems;
import com.rfizzle.cultivation.soil.Fertilizer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.rfizzle.cultivation.gametest.SoilFixtures.CROP;
import static com.rfizzle.cultivation.gametest.SoilFixtures.FARM;
import static com.rfizzle.cultivation.gametest.SoilFixtures.TEMPLATE;
import static com.rfizzle.cultivation.gametest.SoilFixtures.matureWheat;
import static com.rfizzle.cultivation.gametest.SoilFixtures.placeFarmlandGrid;
import static com.rfizzle.cultivation.gametest.SoilFixtures.placeTrackedFarmland;

/**
 * The Husbandry-tab advancements ({@code design/SPEC.md} §10). Each trigger
 * grants for the acting player when its condition is met, stays silent on the
 * near-miss (an 8-crop sweep, a sow that falls short of nine, a repeated food,
 * half of the enriched+dosed combination), and never grants to a bystander or an
 * automated no-player path.
 */
public class AdvancementGameTest implements FabricGameTest {
    // The 3×3 scythe field, mirroring ScytheSweepGameTest's geometry.
    private static final BlockPos CENTER = new BlockPos(3, 2, 3);
    private static final Item[] DISTINCT_FOODS =
            {Items.APPLE, Items.BREAD, Items.CARROT, Items.POTATO, Items.BEETROOT};

    // --- balanced_table ---

    @GameTest(template = TEMPLATE)
    public void balancedTableGrantsOnVarietyReset(GameTestHelper helper) {
        ServerPlayer player = listeningPlayer(helper);
        eatDistinctFoods(player, CultivationConfig.get().fatigueResetDistinctFoods);
        assertGranted(helper, player, "balanced_table");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void balancedTableSilentWithoutVariety(GameTestHelper helper) {
        ServerPlayer player = listeningPlayer(helper);
        // The same food, over and over — the reset window never sees enough distinct foods.
        for (int i = 0; i < CultivationConfig.get().fatigueResetDistinctFoods + 1; i++) {
            DietHandler.consume(player, Items.APPLE);
        }
        assertNotGranted(helper, player, "balanced_table");
        helper.succeed();
    }

    // --- long_term_investment ---

    @GameTest(template = TEMPLATE)
    public void longTermInvestmentGrantsOnDose(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.FARMLAND);
        ServerPlayer player = listeningPlayer(helper);
        useFertilizer(helper, player, FARM);
        assertGranted(helper, player, "long_term_investment");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void longTermInvestmentSilentForVillagerDose(GameTestHelper helper) {
        // The villager stewardship path doses with a null player: nobody is granted.
        helper.setBlock(FARM, Blocks.FARMLAND);
        ServerPlayer bystander = listeningPlayer(helper);
        Fertilizer.applyDose(helper.getLevel(), helper.absolutePos(FARM), null);
        assertNotGranted(helper, bystander, "long_term_investment");
        helper.succeed();
    }

    // --- reap_what_you_sow ---

    @GameTest(template = TEMPLATE)
    public void reapWhatYouSowGrantsOnFullSweep(GameTestHelper helper) {
        fillField(helper, matureWheat());
        ServerPlayer player = listeningScyther(helper);
        breakCenter(helper, player);
        assertGranted(helper, player, "reap_what_you_sow");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void reapWhatYouSowSilentOnEightCropSweep(GameTestHelper helper) {
        fillField(helper, matureWheat());
        // One corner is immature: only eight of the nine positions are reaped.
        helper.setBlock(CENTER.offset(1, 0, 1), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3));
        ServerPlayer player = listeningScyther(helper);
        breakCenter(helper, player);
        assertNotGranted(helper, player, "reap_what_you_sow");
        helper.succeed();
    }

    // --- full_broadcast ---

    @GameTest(template = TEMPLATE)
    public void fullBroadcastGrantsOnNineBlockSow(GameTestHelper helper) {
        placeFarmlandGrid(helper);
        ServerPlayer player = listeningRaker(helper, Items.WHEAT_SEEDS, 9);
        sow(helper, player, FARM);
        assertGranted(helper, player, "full_broadcast");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void fullBroadcastSilentOnOccupiedBlock(GameTestHelper helper) {
        placeFarmlandGrid(helper);
        // One corner is not farmland and another already carries a crop: seven sown.
        helper.setBlock(FARM.offset(-1, 0, -1), Blocks.DIRT);
        helper.setBlock(FARM.offset(1, 0, 1).above(), matureWheat());
        ServerPlayer player = listeningRaker(helper, Items.WHEAT_SEEDS, 9);
        assertPartialSow(helper, sow(helper, player, FARM));
        assertNotGranted(helper, player, "full_broadcast");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void fullBroadcastSilentWhenSeedsRunShort(GameTestHelper helper) {
        placeFarmlandGrid(helper);
        // Three seeds cap the pass at three blocks.
        ServerPlayer player = listeningRaker(helper, Items.WHEAT_SEEDS, 3);
        assertPartialSow(helper, sow(helper, player, FARM));
        assertNotGranted(helper, player, "full_broadcast");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void fullBroadcastSilentWhenRakeBreaksMidPass(GameTestHelper helper) {
        placeFarmlandGrid(helper);
        ServerPlayer player = listeningRaker(helper, Items.WHEAT_SEEDS, 9);
        ItemStack rake = new ItemStack(CultivationItems.IRON_RAKE);
        rake.setDamageValue(rake.getMaxDamage() - 3); // three uses left, then it breaks
        player.setItemInHand(InteractionHand.MAIN_HAND, rake);
        assertPartialSow(helper, sow(helper, player, FARM));
        assertNotGranted(helper, player, "full_broadcast");
        player.discard();
        helper.succeed();
    }

    // --- old_growth ---

    @GameTest(template = TEMPLATE)
    public void oldGrowthGrantsOnEnrichedAndDosed(GameTestHelper helper) {
        ServerPlayer player = listeningPlayer(helper);
        harvestFrom(helper, player, 80.0F, 100, 2);
        assertGranted(helper, player, "old_growth");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void oldGrowthSilentWhenOnlyEnriched(GameTestHelper helper) {
        ServerPlayer player = listeningPlayer(helper);
        harvestFrom(helper, player, 80.0F, 100, 0);
        assertNotGranted(helper, player, "old_growth");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void oldGrowthSilentWhenOnlyDosed(GameTestHelper helper) {
        ServerPlayer player = listeningPlayer(helper);
        harvestFrom(helper, player, 80.0F, 0, 2);
        assertNotGranted(helper, player, "old_growth");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void oldGrowthSilentOnExhaustedSoil(GameTestHelper helper) {
        // Enriched and dosed, but the harvest drains fertility to zero: the bonus
        // is suppressed and no dose is spent, so the grant must not fire either.
        ServerPlayer player = listeningPlayer(helper);
        harvestFrom(helper, player, 1.0F, 100, 2);
        assertNotGranted(helper, player, "old_growth");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void oldGrowthSilentForNoPlayerHarvest(GameTestHelper helper) {
        // A piston/explosion harvest resolves drops with a null harvester.
        ServerPlayer bystander = listeningPlayer(helper);
        placeTrackedFarmland(helper, FARM, 80.0F, Blocks.WHEAT);
        enrichAndDose(helper, 100, 2);
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Items.WHEAT)));
        HarvestHandler.onDropsResolved(matureWheat(), helper.getLevel(), helper.absolutePos(CROP), null, drops);
        assertNotGranted(helper, bystander, "old_growth");
        helper.succeed();
    }

    // --- helpers ---

    private static ServerPlayer listeningPlayer(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        // Reload against the live manager so the freshly-registered triggers have
        // an advancement listener for this player before the first fire.
        player.getAdvancements().reload(helper.getLevel().getServer().getAdvancements());
        return player;
    }

    private static ServerPlayer listeningScyther(GameTestHelper helper) {
        ServerPlayer player = listeningPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CultivationItems.IRON_SCYTHE));
        return player;
    }

    /** A listening survival player holding an iron rake, {@code count} seeds in the off-hand. */
    private static ServerPlayer listeningRaker(GameTestHelper helper, Item seed, int count) {
        ServerPlayer player = listeningPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CultivationItems.IRON_RAKE));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(seed, count));
        return player;
    }

    /** Fires the real {@link UseBlockCallback} pipeline against the farmland's up-face. */
    private static InteractionResult sow(GameTestHelper helper, ServerPlayer player, BlockPos farmlandRel) {
        BlockPos abs = helper.absolutePos(farmlandRel);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        return UseBlockCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
    }

    /**
     * Guards the partial-pass cases against a vacuous pass: the handler returns
     * SUCCESS only when at least one block was sown, so SUCCESS plus an ungranted
     * advancement pins the pass to somewhere between one and eight blocks.
     */
    private static void assertPartialSow(GameTestHelper helper, InteractionResult result) {
        helper.assertTrue(result == InteractionResult.SUCCESS,
                "the partial sow must still plant something, or the no-grant assertion proves nothing");
    }

    private static void eatDistinctFoods(ServerPlayer player, int count) {
        for (int i = 0; i < count; i++) {
            DietHandler.consume(player, DISTINCT_FOODS[i % DISTINCT_FOODS.length]);
        }
    }

    private static void useFertilizer(GameTestHelper helper, ServerPlayer player, BlockPos rel) {
        ItemStack stack = new ItemStack(CultivationItems.FERTILIZER);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = helper.absolutePos(rel);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    /** Sets a mature-wheat block on farmland pinned to {@code fertility}, enriched+dosed, and runs the choke point. */
    private static void harvestFrom(GameTestHelper helper, ServerPlayer player, float fertility, int enrichedChance, int dose) {
        placeTrackedFarmland(helper, FARM, fertility, Blocks.WHEAT);
        enrichAndDose(helper, enrichedChance, dose);
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Items.WHEAT)));
        HarvestHandler.onDropsResolved(matureWheat(), helper.getLevel(), helper.absolutePos(CROP), player, drops);
    }

    private static void enrichAndDose(GameTestHelper helper, int enrichedChance, int dose) {
        SoilStores.update(helper.getLevel(), helper.absolutePos(FARM), false,
                data -> data.withEnrichedChance(enrichedChance).withFertilizerRemaining(dose));
    }

    private static void fillField(GameTestHelper helper, BlockState crop) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = CENTER.offset(dx, 0, dz);
                helper.setBlock(pos.below(), Blocks.FARMLAND);
                helper.setBlock(pos, crop);
            }
        }
    }

    private static void breakCenter(GameTestHelper helper, ServerPlayer player) {
        player.gameMode.destroyBlock(helper.absolutePos(CENTER));
    }

    private static AdvancementHolder holder(GameTestHelper helper, String path) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation id = Cultivation.id(path);
        AdvancementHolder found = server.getAdvancements().get(id);
        helper.assertTrue(found != null, "advancement " + id + " must be loaded (JSON present under data/cultivation/advancement)");
        return found;
    }

    private static void assertGranted(GameTestHelper helper, ServerPlayer player, String path) {
        helper.assertTrue(player.getAdvancements().getOrStartProgress(holder(helper, path)).isDone(),
                "advancement " + path + " should be granted for the acting player");
    }

    private static void assertNotGranted(GameTestHelper helper, ServerPlayer player, String path) {
        helper.assertTrue(!player.getAdvancements().getOrStartProgress(holder(helper, path)).isDone(),
                "advancement " + path + " must not be granted");
    }
}
