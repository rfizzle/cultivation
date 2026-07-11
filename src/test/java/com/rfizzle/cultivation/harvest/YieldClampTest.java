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
import static org.junit.jupiter.api.Assertions.assertTrue;

class YieldClampTest {
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
    void wheatClampsToOneProductAndOneSeed() {
        List<ItemStack> drops = stacks(new ItemStack(Items.WHEAT, 1), new ItemStack(Items.WHEAT_SEEDS, 3));
        YieldClamp.clampToBareMinimum(drops, Items.WHEAT, Items.WHEAT_SEEDS);
        assertEquals(1, totalOf(drops, Items.WHEAT));
        assertEquals(1, totalOf(drops, Items.WHEAT_SEEDS));
    }

    @Test
    void sameProductAndSeedClampsToOneTotal() {
        // Carrots and potatoes: the seed IS the product, so the bare minimum is 1 total.
        List<ItemStack> drops = stacks(new ItemStack(Items.CARROT, 4), new ItemStack(Items.CARROT, 2));
        YieldClamp.clampToBareMinimum(drops, Items.CARROT, Items.CARROT);
        assertEquals(1, totalOf(drops, Items.CARROT));
        assertEquals(1, drops.size());
    }

    @Test
    void multipleProductStacksCollapseToOne() {
        List<ItemStack> drops = stacks(
                new ItemStack(Items.WHEAT, 2), new ItemStack(Items.WHEAT, 1), new ItemStack(Items.WHEAT_SEEDS, 2));
        YieldClamp.clampToBareMinimum(drops, Items.WHEAT, Items.WHEAT_SEEDS);
        assertEquals(1, totalOf(drops, Items.WHEAT));
        assertEquals(1, totalOf(drops, Items.WHEAT_SEEDS));
    }

    @Test
    void emptyAndSeedlessDropsSurvive() {
        List<ItemStack> empty = stacks();
        YieldClamp.clampToBareMinimum(empty, Items.WHEAT, Items.WHEAT_SEEDS);
        assertTrue(empty.isEmpty());

        // An unlucky wheat roll can drop seeds only.
        List<ItemStack> seedsOnly = stacks(new ItemStack(Items.WHEAT_SEEDS, 2));
        YieldClamp.clampToBareMinimum(seedsOnly, Items.WHEAT, Items.WHEAT_SEEDS);
        assertEquals(1, totalOf(seedsOnly, Items.WHEAT_SEEDS));
    }

    @Test
    void unrelatedStacksPassThroughUntouched() {
        List<ItemStack> drops = stacks(new ItemStack(Items.WHEAT, 3), new ItemStack(Items.DIAMOND, 2));
        YieldClamp.clampToBareMinimum(drops, Items.WHEAT, Items.WHEAT_SEEDS);
        assertEquals(1, totalOf(drops, Items.WHEAT));
        assertEquals(2, totalOf(drops, Items.DIAMOND));
    }
}
