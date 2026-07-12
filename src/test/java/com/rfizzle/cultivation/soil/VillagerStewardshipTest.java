package com.rfizzle.cultivation.soil;

import com.rfizzle.cultivation.soil.VillagerStewardship.ReplantDecision;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerStewardshipTest {
    private static final ResourceLocation WHEAT = ResourceLocation.withDefaultNamespace("wheat");

    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // --- Fallow hysteresis (SPEC §8) ---

    @Test
    void aboveReplantThresholdIsEligibleAndClearsLatch() {
        assertEquals(new ReplantDecision(true, false), VillagerStewardship.evaluateReplant(50.0F, true, 25.0, 50.0));
        assertEquals(new ReplantDecision(true, false), VillagerStewardship.evaluateReplant(80.0F, true, 25.0, 50.0));
        assertEquals(new ReplantDecision(true, false), VillagerStewardship.evaluateReplant(100.0F, false, 25.0, 50.0));
    }

    @Test
    void belowFallowThresholdIsIneligibleAndSetsLatch() {
        assertEquals(new ReplantDecision(false, true), VillagerStewardship.evaluateReplant(24.9F, false, 25.0, 50.0));
        assertEquals(new ReplantDecision(false, true), VillagerStewardship.evaluateReplant(0.0F, true, 25.0, 50.0));
    }

    @Test
    void midBandFollowsTheStoredLatchBothWays() {
        // A block on the way down (never latched) keeps being replanted through the band…
        assertEquals(new ReplantDecision(true, false), VillagerStewardship.evaluateReplant(30.0F, false, 25.0, 50.0));
        assertEquals(new ReplantDecision(true, false), VillagerStewardship.evaluateReplant(25.0F, false, 25.0, 50.0));
        // …a block resting (latched below 25) stays fallow until it reaches 50.
        assertEquals(new ReplantDecision(false, true), VillagerStewardship.evaluateReplant(30.0F, true, 25.0, 50.0));
        assertEquals(new ReplantDecision(false, true), VillagerStewardship.evaluateReplant(49.9F, true, 25.0, 50.0));
    }

    // --- Rotation preference (SPEC §8) ---

    @Test
    void rejectsRepeatSeedOnlyWhenARotationIsOnHand() {
        ItemStack wheatSeeds = new ItemStack(Items.WHEAT_SEEDS);
        // Matches lastCrop and a differing seed is available → reject, so the farmer rotates.
        assertFalse(VillagerStewardship.acceptSeed(wheatSeeds, WHEAT, true));
        // Matches lastCrop but it is the only seed → accept, the farmer plants what it has.
        assertTrue(VillagerStewardship.acceptSeed(wheatSeeds, WHEAT, false));
    }

    @Test
    void acceptsSeedsThatDifferOrHaveNoRotationContext() {
        assertTrue(VillagerStewardship.acceptSeed(new ItemStack(Items.CARROT), WHEAT, true));
        // No rotation memory on the block → any seed passes.
        assertTrue(VillagerStewardship.acceptSeed(new ItemStack(Items.WHEAT_SEEDS), null, true));
        // A non-block item carries no crop identity → passes.
        assertTrue(VillagerStewardship.acceptSeed(new ItemStack(Items.STICK), WHEAT, true));
    }
}
