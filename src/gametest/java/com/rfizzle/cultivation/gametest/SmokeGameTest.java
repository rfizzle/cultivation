package com.rfizzle.cultivation.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Skeleton smoke test: proves the gametest source set, the fabric-gametest
 * entrypoint, and the CI wiring all work before any real feature depends on them.
 */
public class SmokeGameTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void worldAcceptsFarmland(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, Blocks.FARMLAND);
        helper.assertBlockPresent(Blocks.FARMLAND, pos);
        helper.succeed();
    }
}
