package com.rfizzle.cultivation.harvest;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeedWithdrawalTest {
    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<ItemStack> stacks(ItemStack... stacks) {
        return new ArrayList<>(List.of(stacks));
    }

    private static int totalOf(List<ItemStack> drops, net.minecraft.world.item.Item item) {
        return drops.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    @Test
    void withdrawsOneSeedFromALargerStack() {
        List<ItemStack> drops = stacks(new ItemStack(Items.WHEAT, 1), new ItemStack(Items.WHEAT_SEEDS, 3));
        assertTrue(SeedWithdrawal.withdrawOne(drops, Items.WHEAT_SEEDS));
        assertEquals(2, totalOf(drops, Items.WHEAT_SEEDS), "one seed is sown into the replant");
        assertEquals(1, totalOf(drops, Items.WHEAT), "the product is untouched");
    }

    @Test
    void withdrawsAndRemovesASingletonSeedStack() {
        List<ItemStack> drops = stacks(new ItemStack(Items.WHEAT, 1), new ItemStack(Items.WHEAT_SEEDS, 1));
        assertTrue(SeedWithdrawal.withdrawOne(drops, Items.WHEAT_SEEDS));
        assertEquals(0, totalOf(drops, Items.WHEAT_SEEDS));
        assertEquals(1, drops.size(), "the emptied seed stack is dropped from the list");
    }

    @Test
    void withdrawsOneWhenSeedEqualsProduct() {
        // Carrots and potatoes replant from their own drop.
        List<ItemStack> drops = stacks(new ItemStack(Items.CARROT, 2));
        assertTrue(SeedWithdrawal.withdrawOne(drops, Items.CARROT));
        assertEquals(1, totalOf(drops, Items.CARROT));
    }

    @Test
    void reportsNoSeedAndLeavesDropsUntouched() {
        // A mature torchflower drops the flower but no seeds — nothing to sow.
        List<ItemStack> drops = stacks(new ItemStack(Items.TORCHFLOWER, 1));
        assertFalse(SeedWithdrawal.withdrawOne(drops, Items.TORCHFLOWER_SEEDS));
        assertEquals(1, totalOf(drops, Items.TORCHFLOWER), "the drops are left intact when no seed is present");
    }

    @Test
    void withdrawsOnlyTheFirstSeedUnit() {
        List<ItemStack> drops = stacks(new ItemStack(Items.BEETROOT_SEEDS, 1), new ItemStack(Items.BEETROOT_SEEDS, 1));
        assertTrue(SeedWithdrawal.withdrawOne(drops, Items.BEETROOT_SEEDS));
        assertEquals(1, totalOf(drops, Items.BEETROOT_SEEDS), "exactly one unit is withdrawn, not one per stack");
    }
}
