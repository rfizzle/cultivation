package com.rfizzle.cultivation.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import static com.rfizzle.cultivation.gametest.SoilFixtures.CROP;
import static com.rfizzle.cultivation.gametest.SoilFixtures.FARM;
import static com.rfizzle.cultivation.gametest.SoilFixtures.TEMPLATE;
import static com.rfizzle.cultivation.gametest.SoilFixtures.idOf;
import static com.rfizzle.cultivation.gametest.SoilFixtures.matureWheat;

/** The public {@code CultivationHarvestCallback} contract at the choke point. */
public class HarvestCallbackGameTest implements FabricGameTest {

    @GameTest(template = TEMPLATE)
    public void callbackFiresWithMutableDropsAndIsolatesThrowers(GameTestHelper helper) {
        HarvestRecorder.ensureRegistered();
        helper.setBlock(FARM, Blocks.FARMLAND);
        helper.setBlock(CROP, matureWheat());
        BlockPos cropAbs = helper.absolutePos(CROP);
        HarvestRecorder.BONUS_POSITIONS.add(cropAbs);
        HarvestRecorder.THROW_POSITIONS.add(cropAbs);
        try {
            helper.getLevel().destroyBlock(cropAbs, true);

            // The bonus listener's diamond reached the world: the drops list is live.
            helper.assertItemEntityPresent(Items.DIAMOND, CROP, 2.0);
            helper.assertItemEntityPresent(Items.WHEAT, CROP, 2.0);

            // The recorder ran after the throwing listener and saw the mutation.
            HarvestRecorder.Recorded recorded = HarvestRecorder.RECORDS.stream()
                    .filter(r -> r.pos().equals(cropAbs))
                    .reduce((first, second) -> second)
                    .orElse(null);
            helper.assertTrue(recorded != null, "a throwing listener must not stop later listeners");
            helper.assertTrue(recorded.crop().equals(idOf(Blocks.WHEAT)), "the callback carries the crop state");
            helper.assertTrue(recorded.harvester() == null, "destroyBlock has no harvesting entity");
            helper.assertTrue(recorded.drops().stream().anyMatch(stack -> stack.is(Items.DIAMOND)),
                    "listeners registered later must observe earlier listeners' mutations");
            helper.succeed();
        } finally {
            HarvestRecorder.BONUS_POSITIONS.remove(cropAbs);
            HarvestRecorder.THROW_POSITIONS.remove(cropAbs);
        }
    }
}
