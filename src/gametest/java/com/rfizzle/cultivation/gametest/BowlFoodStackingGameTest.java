package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.meal.BowlFoodStacking;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.List;

/**
 * The bowl-food stacking behavior driven through the real world (SPEC §4). The
 * mod raises the four stews to a stack of 16 at init, which activates vanilla's
 * latent stack-{@code >1} bowl-return branch in {@code Player#eat}; these prove
 * the stack size actually lands in the running server and that the returned bowl
 * is never lost when the inventory is full. The mutation itself is covered at
 * Tier 2 in {@code BowlFoodStackingTest}.
 */
public class BowlFoodStackingGameTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void bowlFoodsStackInTheRunningServer(GameTestHelper helper) {
        // onInitialize ran BowlFoodStacking.apply() under the default enableMealBuffs.
        helper.assertTrue(Items.RABBIT_STEW.getDefaultMaxStackSize() == BowlFoodStacking.STACK_SIZE,
                "rabbit stew stacks to " + BowlFoodStacking.STACK_SIZE);
        ItemStack stack = new ItemStack(Items.MUSHROOM_STEW, BowlFoodStacking.STACK_SIZE);
        helper.assertTrue(stack.getCount() == BowlFoodStacking.STACK_SIZE && !stack.isEmpty(),
                "a mushroom stew stack holds " + BowlFoodStacking.STACK_SIZE);
        helper.assertTrue(stack.getMaxStackSize() == BowlFoodStacking.STACK_SIZE,
                "the live stack reports max " + BowlFoodStacking.STACK_SIZE);
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void eatingFromAStackReturnsBowlToInventory(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        ItemStack stew = new ItemStack(Items.RABBIT_STEW, 3);
        FoodProperties food = stew.get(DataComponents.FOOD);
        helper.assertTrue(food != null, "rabbit stew has a food component");

        player.eat(helper.getLevel(), stew, food);

        helper.assertTrue(stew.getCount() == 2,
                "one stew consumed, two remain (got " + stew.getCount() + ")");
        helper.assertTrue(player.getInventory().contains(new ItemStack(Items.BOWL)),
                "the returned bowl lands in the inventory");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void bowlIsDroppedWhenInventoryIsFull(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        // Fill every main inventory slot so the returned bowl cannot be stored.
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        }
        ItemStack stew = new ItemStack(Items.RABBIT_STEW, 3);
        FoodProperties food = stew.get(DataComponents.FOOD);
        helper.assertTrue(food != null, "rabbit stew has a food component");

        player.eat(helper.getLevel(), stew, food);

        List<ItemEntity> dropped = player.level().getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(6.0),
                e -> e.getItem().is(Items.BOWL));
        helper.assertTrue(!dropped.isEmpty(), "the returned bowl is dropped in-world, not lost");
        player.discard();
        helper.succeed();
    }

    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        // Vanilla's bowl-return branch only runs for a player without infinite
        // materials; MockPlayers forces creative, so drop to survival to reach it.
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }
}
