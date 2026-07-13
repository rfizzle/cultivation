package com.rfizzle.cultivation.soil;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The broadcast-sowing seed scope ({@code design/SPEC.md} §7):
 * {@link SupportedCrops#plantableCropForSeed} maps each of the six farmland
 * replant crops' seeds to its crop block and rejects everything else — the
 * second-wave crops and any non-seed item.
 */
class PlantableCropForSeedTest {
    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Block plantableFor(net.minecraft.world.item.Item seed) {
        return SupportedCrops.plantableCropForSeed(new ItemStack(seed));
    }

    @Test
    void mapsEveryFarmlandReplantSeedToItsCropBlock() {
        assertSame(Blocks.WHEAT, plantableFor(Items.WHEAT_SEEDS));
        assertSame(Blocks.CARROTS, plantableFor(Items.CARROT));
        assertSame(Blocks.POTATOES, plantableFor(Items.POTATO));
        assertSame(Blocks.BEETROOTS, plantableFor(Items.BEETROOT_SEEDS));
        assertSame(Blocks.TORCHFLOWER_CROP, plantableFor(Items.TORCHFLOWER_SEEDS));
        assertSame(Blocks.PITCHER_CROP, plantableFor(Items.PITCHER_POD));
    }

    @Test
    void rejectsSecondWaveCropSeeds() {
        // Nether wart and sweet berries are out of scope — the gesture is farmland only.
        assertNull(plantableFor(Items.NETHER_WART), "nether wart is not a farmland replant crop");
        assertNull(plantableFor(Items.SWEET_BERRIES), "sweet berries are not a farmland replant crop");
    }

    @Test
    void rejectsNonSeedItems() {
        assertNull(plantableFor(Items.WHEAT), "the wheat product is not a plantable seed");
        assertNull(plantableFor(Items.DIAMOND_HOE), "a hoe is not a seed");
        assertNull(SupportedCrops.plantableCropForSeed(ItemStack.EMPTY), "an empty hand sows nothing");
    }
}
