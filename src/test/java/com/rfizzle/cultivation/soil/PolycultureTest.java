package com.rfizzle.cultivation.soil;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PolycultureTest {
    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // --- threshold math ---

    @Test
    void multiplierAppliesAtOrAboveTheThreshold() {
        assertEquals(1.0F, Polyculture.multiplier(0, 2, 1.2), 1e-6F);
        assertEquals(1.0F, Polyculture.multiplier(1, 2, 1.2), 1e-6F);
        assertEquals(1.2F, Polyculture.multiplier(2, 2, 1.2), 1e-6F);
        assertEquals(1.2F, Polyculture.multiplier(3, 2, 1.2), 1e-6F);
        assertEquals(1.2F, Polyculture.multiplier(4, 2, 1.2), 1e-6F);
    }

    @Test
    void thresholdExtremesAreRespected() {
        assertEquals(1.5F, Polyculture.multiplier(1, 1, 1.5), 1e-6F);
        assertEquals(1.0F, Polyculture.multiplier(3, 4, 1.5), 1e-6F);
        assertEquals(1.5F, Polyculture.multiplier(4, 4, 1.5), 1e-6F);
    }

    // --- crop identity ---

    @Test
    void cropsCarryTheirOwnBlockId() {
        assertEquals("minecraft:wheat", identity(Blocks.WHEAT));
        assertEquals("minecraft:carrots", identity(Blocks.CARROTS));
        assertEquals("minecraft:potatoes", identity(Blocks.POTATOES));
        assertEquals("minecraft:beetroots", identity(Blocks.BEETROOTS));
        assertEquals("minecraft:torchflower_crop", identity(Blocks.TORCHFLOWER_CROP));
        assertEquals("minecraft:pitcher_crop", identity(Blocks.PITCHER_CROP));
    }

    @Test
    void stemsAreTwoDistinctCropsAndAttachedStemsKeepTheBaseId() {
        assertEquals("minecraft:melon_stem", identity(Blocks.MELON_STEM));
        assertEquals("minecraft:pumpkin_stem", identity(Blocks.PUMPKIN_STEM));
        assertNotEquals(identity(Blocks.MELON_STEM), identity(Blocks.PUMPKIN_STEM));

        assertEquals("minecraft:melon_stem", identity(Blocks.ATTACHED_MELON_STEM));
        assertEquals("minecraft:pumpkin_stem", identity(Blocks.ATTACHED_PUMPKIN_STEM));
    }

    @Test
    void matureTorchflowerKeepsItsCropIdentity() {
        // The one crop whose maturity changes its block id — maturing must not
        // silently drop it out of the field's neighbor counts.
        assertEquals("minecraft:torchflower_crop", identity(Blocks.TORCHFLOWER));
    }

    @Test
    void nonCropsHaveNoIdentity() {
        assertNull(Polyculture.cropIdentity(Blocks.AIR.defaultBlockState()));
        assertNull(Polyculture.cropIdentity(Blocks.FARMLAND.defaultBlockState()));
        assertNull(Polyculture.cropIdentity(Blocks.POPPY.defaultBlockState()));
        assertNull(Polyculture.cropIdentity(Blocks.MELON.defaultBlockState()));
        assertNull(Polyculture.cropIdentity(Blocks.NETHER_WART.defaultBlockState()));
        assertNull(Polyculture.cropIdentity(Blocks.SWEET_BERRY_BUSH.defaultBlockState()));
    }

    @Test
    void neighborMaturityIsIrrelevant() {
        ResourceLocation wheat = Polyculture.cropIdentity(Blocks.WHEAT.defaultBlockState());
        BlockState freshCarrots = Blocks.CARROTS.defaultBlockState();
        BlockState matureCarrots = Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE);
        assertEquals(Polyculture.countDifferent(wheat, freshCarrots),
                Polyculture.countDifferent(wheat, matureCarrots));
        assertEquals(1, Polyculture.countDifferent(wheat, freshCarrots));
    }

    // --- field layouts (SPEC §2 layout math) ---

    @Test
    void alternatingRowsCountTwoForInteriorRowBlocks() {
        Block[][] field = alternatingRows(5, 5);
        // Every block of an interior row — end caps included — sees the two
        // different rows beside it.
        for (int row = 1; row < 4; row++) {
            for (int col = 0; col < 5; col++) {
                assertEquals(2, countAt(field, row, col), "interior row " + row + ", col " + col);
            }
        }
    }

    @Test
    void alternatingRowsOutermostRowsCountOne() {
        Block[][] field = alternatingRows(5, 5);
        for (int col = 0; col < 5; col++) {
            assertEquals(1, countAt(field, 0, col), "outermost row, col " + col);
            assertEquals(1, countAt(field, 4, col), "outermost row, col " + col);
        }
    }

    @Test
    void checkerboardQualifiesEverywhere() {
        Block[][] field = checkerboard(4, 4);
        assertEquals(4, countAt(field, 1, 1), "checkerboard interior");
        assertEquals(4, countAt(field, 2, 2), "checkerboard interior");
        assertEquals(3, countAt(field, 0, 1), "checkerboard edge");
        assertEquals(3, countAt(field, 1, 0), "checkerboard edge");
        assertEquals(2, countAt(field, 0, 0), "checkerboard corner");
        assertEquals(2, countAt(field, 3, 3), "checkerboard corner");
    }

    @Test
    void monocultureCountsZeroEverywhere() {
        Block[][] field = new Block[4][4];
        for (Block[] row : field) {
            java.util.Arrays.fill(row, Blocks.WHEAT);
        }
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                assertEquals(0, countAt(field, row, col), "monoculture " + row + "," + col);
            }
        }
    }

    @Test
    void nonCropNeighborsNeverCount() {
        ResourceLocation wheat = Polyculture.cropIdentity(Blocks.WHEAT.defaultBlockState());
        assertEquals(0, Polyculture.countDifferent(wheat,
                Blocks.AIR.defaultBlockState(), Blocks.POPPY.defaultBlockState(),
                Blocks.FARMLAND.defaultBlockState(), Blocks.WHEAT.defaultBlockState()));
    }

    @Test
    void stemFieldCountsAcrossAttachedAndFreeStems() {
        // A pumpkin stem beside its own attached form (same crop), an attached
        // melon stem (different crop), and a wheat block: two different neighbors.
        ResourceLocation pumpkin = Polyculture.cropIdentity(Blocks.PUMPKIN_STEM.defaultBlockState());
        assertEquals(2, Polyculture.countDifferent(pumpkin,
                Blocks.ATTACHED_PUMPKIN_STEM.defaultBlockState(),
                Blocks.ATTACHED_MELON_STEM.defaultBlockState(),
                Blocks.WHEAT.defaultBlockState(),
                Blocks.AIR.defaultBlockState()));
    }

    // --- helpers ---

    private static String identity(Block block) {
        ResourceLocation id = Polyculture.cropIdentity(block.defaultBlockState());
        return id == null ? null : id.toString();
    }

    /** Rows cycling wheat / carrots / potatoes — SPEC §2's alternating-row field. */
    private static Block[][] alternatingRows(int rows, int cols) {
        Block[] cycle = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES};
        Block[][] field = new Block[rows][cols];
        for (int row = 0; row < rows; row++) {
            java.util.Arrays.fill(field[row], cycle[row % 3]);
        }
        return field;
    }

    /** Two-crop wheat/carrot checkerboard. */
    private static Block[][] checkerboard(int rows, int cols) {
        Block[][] field = new Block[rows][cols];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                field[row][col] = (row + col) % 2 == 0 ? Blocks.WHEAT : Blocks.CARROTS;
            }
        }
        return field;
    }

    /** Different-crop neighbor count at a grid position; outside the field is air. */
    private static int countAt(Block[][] field, int row, int col) {
        ResourceLocation selfId = Polyculture.cropIdentity(field[row][col].defaultBlockState());
        return Polyculture.countDifferent(selfId,
                stateAt(field, row - 1, col), stateAt(field, row + 1, col),
                stateAt(field, row, col - 1), stateAt(field, row, col + 1));
    }

    private static BlockState stateAt(Block[][] field, int row, int col) {
        if (row < 0 || row >= field.length || col < 0 || col >= field[row].length) {
            return Blocks.AIR.defaultBlockState();
        }
        return field[row][col].defaultBlockState();
    }
}
