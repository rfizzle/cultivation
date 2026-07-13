package com.rfizzle.cultivation.soil;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportedCropsTest {
    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void matureCropsMapToTheirProductsAndSeeds() {
        var wheat = SupportedCrops.matureProfile(Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        assertNotNull(wheat);
        assertEquals("minecraft:wheat", wheat.cropId().toString());
        assertEquals(Items.WHEAT, wheat.product());
        assertEquals(Items.WHEAT_SEEDS, wheat.seed());

        var carrots = SupportedCrops.matureProfile(Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 7));
        assertNotNull(carrots);
        assertEquals(Items.CARROT, carrots.product());
        assertEquals(Items.CARROT, carrots.seed());

        var potatoes = SupportedCrops.matureProfile(Blocks.POTATOES.defaultBlockState().setValue(CropBlock.AGE, 7));
        assertNotNull(potatoes);
        assertEquals(Items.POTATO, potatoes.product());
        assertEquals(Items.POTATO, potatoes.seed());

        var beetroots = SupportedCrops.matureProfile(
                Blocks.BEETROOTS.defaultBlockState().setValue(BlockStateProperties.AGE_3, 3));
        assertNotNull(beetroots);
        assertEquals(Items.BEETROOT, beetroots.product());
        assertEquals(Items.BEETROOT_SEEDS, beetroots.seed());
    }

    @Test
    void immatureCropsAreNotHarvestable() {
        assertNull(SupportedCrops.matureProfile(Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 6)));
        assertNull(SupportedCrops.matureProfile(Blocks.BEETROOTS.defaultBlockState().setValue(BlockStateProperties.AGE_3, 2)));
        assertNull(SupportedCrops.matureProfile(Blocks.CARROTS.defaultBlockState()));
    }

    @Test
    void torchflowerMaturesAsTheFlowerBlock() {
        // The crop block never reaches its nominal max age — age 2 is the flower itself.
        assertNull(SupportedCrops.matureProfile(Blocks.TORCHFLOWER_CROP.defaultBlockState()));
        assertNull(SupportedCrops.matureProfile(
                Blocks.TORCHFLOWER_CROP.defaultBlockState().setValue(BlockStateProperties.AGE_1, 1)));

        var torchflower = SupportedCrops.matureProfile(Blocks.TORCHFLOWER.defaultBlockState());
        assertNotNull(torchflower);
        assertEquals("minecraft:torchflower_crop", torchflower.cropId().toString());
        assertEquals(Items.TORCHFLOWER, torchflower.product());
        assertEquals(Items.TORCHFLOWER_SEEDS, torchflower.seed());
    }

    @Test
    void pitcherCountsOnlyAsItsMatureLowerHalf() {
        BlockState lower = Blocks.PITCHER_CROP.defaultBlockState()
                .setValue(PitcherCropBlock.AGE, PitcherCropBlock.MAX_AGE)
                .setValue(PitcherCropBlock.HALF, DoubleBlockHalf.LOWER);
        var pitcher = SupportedCrops.matureProfile(lower);
        assertNotNull(pitcher);
        assertEquals(Items.PITCHER_PLANT, pitcher.product());
        assertEquals(Items.PITCHER_POD, pitcher.seed());

        assertNull(SupportedCrops.matureProfile(lower.setValue(PitcherCropBlock.HALF, DoubleBlockHalf.UPPER)));
        assertNull(SupportedCrops.matureProfile(lower.setValue(PitcherCropBlock.AGE, 3)));
    }

    @Test
    void stemsAndNonCropsAreNeverHarvestProfiles() {
        assertNull(SupportedCrops.matureProfile(Blocks.MELON_STEM.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7)));
        assertNull(SupportedCrops.matureProfile(Blocks.PUMPKIN_STEM.defaultBlockState()));
        assertNull(SupportedCrops.matureProfile(Blocks.NETHER_WART.defaultBlockState()));
        assertNull(SupportedCrops.matureProfile(Blocks.SWEET_BERRY_BUSH.defaultBlockState()));
        assertNull(SupportedCrops.matureProfile(Blocks.POPPY.defaultBlockState()));
        assertNull(SupportedCrops.matureProfile(Blocks.AIR.defaultBlockState()));
    }

    @Test
    void soilProfileCoversSecondWaveCropsButMatureProfileDoesNot() {
        // Nether wart drains on the break, so its soil profile keys on MAX_AGE.
        var wartMature = SupportedCrops.soilProfile(
                Blocks.NETHER_WART.defaultBlockState().setValue(BlockStateProperties.AGE_3, 3));
        assertNotNull(wartMature);
        assertEquals("minecraft:nether_wart", wartMature.cropId().toString());
        assertEquals(Items.NETHER_WART, wartMature.product());
        assertEquals(Items.NETHER_WART, wartMature.seed());
        assertNull(SupportedCrops.soilProfile(
                Blocks.NETHER_WART.defaultBlockState().setValue(BlockStateProperties.AGE_3, 2)));

        // The sweet berry bush drains on every pick from age 2 up.
        var berriesReady = SupportedCrops.soilProfile(
                Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(BlockStateProperties.AGE_3, 2));
        assertNotNull(berriesReady);
        assertEquals("minecraft:sweet_berry_bush", berriesReady.cropId().toString());
        assertEquals(Items.SWEET_BERRIES, berriesReady.product());
        assertEquals(Items.SWEET_BERRIES, berriesReady.seed());
        assertNotNull(SupportedCrops.soilProfile(
                Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(BlockStateProperties.AGE_3, 3)));
        assertNull(SupportedCrops.soilProfile(
                Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(BlockStateProperties.AGE_3, 1)));

        // The second-wave crops never enter the replant registry the scythe and right-click use.
        assertNull(SupportedCrops.matureProfile(
                Blocks.NETHER_WART.defaultBlockState().setValue(BlockStateProperties.AGE_3, 3)));
        assertNull(SupportedCrops.matureProfile(
                Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(BlockStateProperties.AGE_3, 3)));
        // A farmland crop's soil profile matches its replant profile.
        assertEquals(SupportedCrops.matureProfile(Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7)),
                SupportedCrops.soilProfile(Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7)));
    }

    @Test
    void trackedSoilGroundHonorsCropAndToggle() {
        BlockState wart = Blocks.NETHER_WART.defaultBlockState();
        BlockState bush = Blocks.SWEET_BERRY_BUSH.defaultBlockState();
        // Farmland tracks for any crop above it, toggle or not.
        assertTrue(SupportedCrops.isTrackedSoilGround(
                Blocks.FARMLAND.defaultBlockState(), Blocks.AIR.defaultBlockState()));
        assertTrue(SupportedCrops.isTrackedSoilGround(Blocks.FARMLAND.defaultBlockState(), wart, false));
        // Each second-wave crop tracks only on its own ground.
        assertTrue(SupportedCrops.isTrackedSoilGround(Blocks.SOUL_SAND.defaultBlockState(), wart));
        assertFalse(SupportedCrops.isTrackedSoilGround(Blocks.SOUL_SAND.defaultBlockState(), bush));
        assertFalse(SupportedCrops.isTrackedSoilGround(Blocks.STONE.defaultBlockState(), wart));
        // The enableNonFarmlandSoil toggle gates the non-farmland grounds.
        assertFalse(SupportedCrops.isTrackedSoilGround(Blocks.SOUL_SAND.defaultBlockState(), wart, false));
    }

    @Test
    void seedsMapToTheCropIdTheyPlant() {
        // The seed's block id is the same identity the choke point records as lastCrop.
        assertEquals("minecraft:wheat", SupportedCrops.cropIdForSeed(new ItemStack(Items.WHEAT_SEEDS)).toString());
        assertEquals("minecraft:beetroots", SupportedCrops.cropIdForSeed(new ItemStack(Items.BEETROOT_SEEDS)).toString());
        assertEquals("minecraft:torchflower_crop", SupportedCrops.cropIdForSeed(new ItemStack(Items.TORCHFLOWER_SEEDS)).toString());
        assertEquals("minecraft:pitcher_crop", SupportedCrops.cropIdForSeed(new ItemStack(Items.PITCHER_POD)).toString());
        // Carrot and potato items are their own crop's block item.
        assertEquals("minecraft:carrots", SupportedCrops.cropIdForSeed(new ItemStack(Items.CARROT)).toString());
        assertEquals("minecraft:potatoes", SupportedCrops.cropIdForSeed(new ItemStack(Items.POTATO)).toString());
    }

    @Test
    void cropIdForSeedIsNullForNonBlockItems() {
        assertNull(SupportedCrops.cropIdForSeed(new ItemStack(Items.STICK)));
        assertNull(SupportedCrops.cropIdForSeed(new ItemStack(Items.WHEAT)));
        assertNull(SupportedCrops.cropIdForSeed(ItemStack.EMPTY));
    }

    @Test
    void occupancyCoversCropsStemsAndTheMatureTorchflower() {
        assertTrue(SupportedCrops.isOccupying(Blocks.WHEAT.defaultBlockState()));
        assertTrue(SupportedCrops.isOccupying(Blocks.TORCHFLOWER_CROP.defaultBlockState()));
        assertTrue(SupportedCrops.isOccupying(Blocks.TORCHFLOWER.defaultBlockState()));
        assertTrue(SupportedCrops.isOccupying(Blocks.MELON_STEM.defaultBlockState()));
        assertTrue(SupportedCrops.isOccupying(Blocks.ATTACHED_PUMPKIN_STEM.defaultBlockState()));
        assertTrue(SupportedCrops.isOccupying(Blocks.PITCHER_CROP.defaultBlockState()));

        assertFalse(SupportedCrops.isOccupying(Blocks.AIR.defaultBlockState()));
        assertFalse(SupportedCrops.isOccupying(Blocks.POPPY.defaultBlockState()));
        assertFalse(SupportedCrops.isOccupying(Blocks.MELON.defaultBlockState()));
    }
}
