package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.api.CultivationAPI;
import com.rfizzle.cultivation.attachment.DietData;
import com.rfizzle.cultivation.attachment.DietStore;
import com.rfizzle.cultivation.config.CultivationConfig;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * The dietary-fatigue seams driven through the real world (SPEC §3): the
 * {@code Player#eat} food path and the {@code CakeBlock#eat} slice path. The
 * decay/reset math itself is exhaustively covered at Tier 1 in
 * {@code DietDataTest}; these prove the mixins wire that math to real eating.
 */
public class DietFatigueGameTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void sameFoodDecaysAndTheApiTracksIt(GameTestHelper helper) {
        FoodRecorder.ensureRegistered();
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            eat(helper, player, Items.CARROT);
            eat(helper, player, Items.CARROT);
            eat(helper, player, Items.CARROT);

            List<FoodRecorder.Recorded> eats = FoodRecorder.forPlayer(player.getUUID());
            helper.assertTrue(eats.size() == 3, "three eats fired three callbacks, got " + eats.size());
            assertClose(helper, eats.get(0).effectiveness(), 1.0F, "first carrot eats at full strength");
            assertClose(helper, eats.get(1).effectiveness(), 0.9F, "second carrot steps down 10%");
            assertClose(helper, eats.get(2).effectiveness(), 0.8F, "third carrot steps down again");

            float next = CultivationAPI.getFoodEffectiveness(player, new ItemStack(Items.CARROT));
            assertClose(helper, next, 0.7F, "the API reports the next eat's multiplier");
            helper.assertTrue(DietStore.get(player).stackCount(idOf(Items.CARROT)) == 3, "three stacks recorded");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void threeFoodRotationResetsFatigue(GameTestHelper helper) {
        FoodRecorder.ensureRegistered();
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            // Pre-fatigue the carrot, then rotate three distinct foods to clear it.
            eat(helper, player, Items.CARROT);
            eat(helper, player, Items.CARROT);
            helper.assertFalse(DietStore.get(player).isDefault(), "carrot is fatigued before the rotation");

            eat(helper, player, Items.CARROT);
            eat(helper, player, Items.POTATO);
            eat(helper, player, Items.BEETROOT);

            helper.assertTrue(DietStore.get(player).isDefault(), "three distinct eats clear the whole map");
            assertClose(helper, CultivationAPI.getFoodEffectiveness(player, new ItemStack(Items.CARROT)),
                    1.0F, "the reset restores full effectiveness");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void cakeSliceSeamKeysToCake(GameTestHelper helper) {
        FoodRecorder.ensureRegistered();
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            player.getFoodData().setFoodLevel(6); // must be hungry to eat cake

            BlockPos cake = new BlockPos(1, 1, 1);
            helper.setBlock(cake, Blocks.CAKE);
            BlockPos abs = helper.absolutePos(cake);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
            helper.getLevel().getBlockState(abs).useWithoutItem(helper.getLevel(), player, hit);

            List<FoodRecorder.Recorded> eats = FoodRecorder.forPlayer(player.getUUID());
            helper.assertTrue(eats.size() == 1 && eats.get(0).item().equals(idOf(Items.CAKE)),
                    "a cake slice fires one callback keyed to minecraft:cake");
            helper.assertTrue(DietStore.get(player).stackCount(idOf(Items.CAKE)) == 1,
                    "the slice recorded a cake stack");
            assertClose(helper, CultivationAPI.getFoodEffectiveness(player, new ItemStack(Items.CAKE)),
                    0.9F, "a second slice would eat at reduced strength");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void cakeSliceScalesRestoredSaturationOnce(GameTestHelper helper) {
        FoodRecorder.ensureRegistered();
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            player.getFoodData().setFoodLevel(6);
            player.getFoodData().setSaturation(0.0F);
            // Pre-fatigue cake to the floor so this slice eats at effectiveness 0.5.
            DietStore.set(player, new DietData(Map.of(idOf(Items.CAKE), 5), List.of()));

            BlockPos cake = new BlockPos(1, 1, 1);
            helper.setBlock(cake, Blocks.CAKE);
            BlockPos abs = helper.absolutePos(cake);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
            helper.getLevel().getBlockState(abs).useWithoutItem(helper.getLevel(), player, hit);

            // Vanilla restores nutrition 2, saturation 2*0.1*2 = 0.4. At effectiveness 0.5 the SPEC
            // wants nutrition max(1, round(1.0)) = 1 and saturation 0.4*0.5 = 0.2 — not 0.1 (eff^2).
            assertClose(helper, player.getFoodData().getSaturationLevel(), 0.2F,
                    "cake saturation scales by effectiveness once, not twice");
            helper.assertTrue(player.getFoodData().getFoodLevel() == 7,
                    "cake nutrition scales to 1 at the floor (6 + 1)");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void foodPathScalesRestoredSaturationOnce(GameTestHelper helper) {
        FoodRecorder.ensureRegistered();
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            player.getFoodData().setFoodLevel(6);
            player.getFoodData().setSaturation(0.0F);
            // Pre-fatigue steak to the floor so this eat lands at effectiveness 0.5.
            DietStore.set(player, new DietData(Map.of(idOf(Items.COOKED_BEEF), 5), List.of()));

            eat(helper, player, Items.COOKED_BEEF);

            // Steak's FoodProperties#saturation is the absolute restored saturation, 8*0.8*2 = 12.8,
            // and Player#eat adds it directly via FoodData#eat(FoodProperties). At effectiveness 0.5
            // the SPEC wants nutrition max(1, round(4.0)) = 4 and saturation 12.8*0.5 = 6.4 — asserting
            // it stays 6.4 guards the food path from a cake-style double-scale (which would over-restore).
            assertClose(helper, player.getFoodData().getSaturationLevel(), 6.4F,
                    "steak saturation scales by effectiveness exactly once");
            helper.assertTrue(player.getFoodData().getFoodLevel() == 10,
                    "steak nutrition scales to 4 at the floor (6 + 4)");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void disabledConfigLeavesEatsUnscaledAndUnrecorded(GameTestHelper helper) {
        FoodRecorder.ensureRegistered();
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        boolean saved = CultivationConfig.get().enableDietaryFatigue;
        try {
            CultivationConfig.get().enableDietaryFatigue = false;
            eat(helper, player, Items.CARROT);
            eat(helper, player, Items.CARROT);

            helper.assertTrue(FoodRecorder.forPlayer(player.getUUID()).isEmpty(),
                    "the callback does not fire while disabled");
            helper.assertTrue(DietStore.get(player).isDefault(), "no fatigue is accumulated while disabled");
            assertClose(helper, CultivationAPI.getFoodEffectiveness(player, new ItemStack(Items.CARROT)),
                    1.0F, "the API reports full strength while disabled");
            helper.succeed();
        } finally {
            CultivationConfig.get().enableDietaryFatigue = saved;
            player.discard();
        }
    }

    private static void eat(GameTestHelper helper, ServerPlayer player, Item item) {
        ServerLevel level = helper.getLevel();
        ItemStack stack = new ItemStack(item);
        FoodProperties food = stack.get(DataComponents.FOOD);
        helper.assertTrue(food != null, "test food " + idOf(item) + " has a food component");
        player.eat(level, stack, food);
    }

    private static net.minecraft.resources.ResourceLocation idOf(Item item) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
    }

    private static void assertClose(GameTestHelper helper, float actual, float expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < 1e-4,
                message + " (expected " + expected + ", got " + actual + ")");
    }
}
