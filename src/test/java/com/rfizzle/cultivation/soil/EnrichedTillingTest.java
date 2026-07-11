package com.rfizzle.cultivation.soil;

import com.rfizzle.cultivation.config.CultivationConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnrichedTillingTest {
    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void grantsBonusRollsStrictlyBelowTheChance() {
        assertFalse(EnrichedTilling.grantsBonus(0, 0));
        assertTrue(EnrichedTilling.grantsBonus(10, 0));
        assertTrue(EnrichedTilling.grantsBonus(10, 9));
        assertFalse(EnrichedTilling.grantsBonus(10, 10));
        assertTrue(EnrichedTilling.grantsBonus(15, 14));
        assertFalse(EnrichedTilling.grantsBonus(15, 15));
        assertTrue(EnrichedTilling.grantsBonus(100, 99));
    }

    @Test
    void hoeTiersMapToTheConfiguredChances() {
        CultivationConfig config = new CultivationConfig();
        assertEquals(10, EnrichedTilling.chanceFor(new ItemStack(Items.DIAMOND_HOE), config));
        assertEquals(15, EnrichedTilling.chanceFor(new ItemStack(Items.NETHERITE_HOE), config));
        assertEquals(0, EnrichedTilling.chanceFor(new ItemStack(Items.WOODEN_HOE), config));
        assertEquals(0, EnrichedTilling.chanceFor(new ItemStack(Items.STONE_HOE), config));
        assertEquals(0, EnrichedTilling.chanceFor(new ItemStack(Items.IRON_HOE), config));
        assertEquals(0, EnrichedTilling.chanceFor(new ItemStack(Items.GOLDEN_HOE), config));
    }

    @Test
    void chanceConfigValuesFlowThrough() {
        CultivationConfig config = new CultivationConfig();
        config.diamondHoeEnrichChance = 42;
        config.netheriteHoeEnrichChance = 77;
        assertEquals(42, EnrichedTilling.chanceFor(new ItemStack(Items.DIAMOND_HOE), config));
        assertEquals(77, EnrichedTilling.chanceFor(new ItemStack(Items.NETHERITE_HOE), config));
    }

    @Test
    void onlyHoesEverEnrich() {
        CultivationConfig config = new CultivationConfig();
        // A diamond-tier tool that isn't a hoe never tills to farmland — the
        // helper's contract matches: non-hoes record 0 regardless of tier.
        assertEquals(0, EnrichedTilling.chanceFor(new ItemStack(Items.DIAMOND_PICKAXE), config));
        assertEquals(0, EnrichedTilling.chanceFor(new ItemStack(Items.STICK), config));
        assertEquals(0, EnrichedTilling.chanceFor(ItemStack.EMPTY, config));
    }
}
