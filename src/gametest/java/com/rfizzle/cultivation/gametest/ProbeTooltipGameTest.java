package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.compat.common.CropProbeTooltip;
import com.rfizzle.cultivation.compat.common.FarmlandProbeTooltip;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.Blocks;

import static com.rfizzle.cultivation.gametest.SoilFixtures.CROP;
import static com.rfizzle.cultivation.gametest.SoilFixtures.FARM;
import static com.rfizzle.cultivation.gametest.SoilFixtures.TEMPLATE;
import static com.rfizzle.cultivation.gametest.SoilFixtures.berryBush;
import static com.rfizzle.cultivation.gametest.SoilFixtures.matureWart;
import static com.rfizzle.cultivation.gametest.SoilFixtures.placeTrackedFarmland;
import static com.rfizzle.cultivation.gametest.SoilFixtures.placeTrackedGround;

/**
 * Drives the Jade/WTHIT shared writers against real world state — the wiring the
 * Tier-1 formatter tests can't reach. Both writers take resolved game objects
 * ({@code ServerLevel}, {@code BlockPos}, {@code BlockState}), so the adapters
 * over them stay too thin to hide a bug.
 */
public class ProbeTooltipGameTest implements FabricGameTest {

    @GameTest(template = TEMPLATE)
    public void farmlandWriterProducesTooltipLines(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 40.0F, Blocks.WHEAT);
        CompoundTag tag = new CompoundTag();
        FarmlandProbeTooltip.writeServerData(tag, helper.getLevel(), helper.absolutePos(FARM));
        helper.assertTrue(!FarmlandProbeTooltip.buildLines(tag).isEmpty(),
                "looking at tracked farmland yields soil tooltip lines");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void nonFarmlandWritesNothing(GameTestHelper helper) {
        helper.setBlock(FARM, Blocks.STONE);
        CompoundTag tag = new CompoundTag();
        FarmlandProbeTooltip.writeServerData(tag, helper.getLevel(), helper.absolutePos(FARM));
        helper.assertTrue(FarmlandProbeTooltip.buildLines(tag).isEmpty(),
                "a non-farmland block leaves the soil tooltip empty");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void soilWriterCoversNetherWartGround(GameTestHelper helper) {
        placeTrackedGround(helper, FARM, Blocks.SOUL_SAND, 40.0F, Blocks.NETHER_WART);
        helper.setBlock(CROP, matureWart());
        CompoundTag tag = new CompoundTag();
        // Looking at the wart resolves the soul sand tracked below it.
        FarmlandProbeTooltip.writeServerData(tag, helper.getLevel(), helper.absolutePos(CROP));
        helper.assertTrue(!FarmlandProbeTooltip.buildLines(tag).isEmpty(),
                "looking at nether wart yields its soul sand's soil lines");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void soilWriterCoversSweetBerryGround(GameTestHelper helper) {
        placeTrackedGround(helper, FARM, Blocks.DIRT, 40.0F, Blocks.SWEET_BERRY_BUSH);
        helper.setBlock(CROP, berryBush(3));
        CompoundTag tag = new CompoundTag();
        FarmlandProbeTooltip.writeServerData(tag, helper.getLevel(), helper.absolutePos(CROP));
        helper.assertTrue(!FarmlandProbeTooltip.buildLines(tag).isEmpty(),
                "looking at a berry bush yields its dirt's soil lines");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void cropWriterProducesGrowthLine(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 10.0F, Blocks.WHEAT);
        helper.setBlock(CROP, Blocks.WHEAT.defaultBlockState());
        CompoundTag tag = new CompoundTag();
        var level = helper.getLevel();
        var cropAbs = helper.absolutePos(CROP);
        CropProbeTooltip.writeServerData(tag, level, cropAbs, level.getBlockState(cropAbs));
        helper.assertTrue(!CropProbeTooltip.buildLines(tag).isEmpty(),
                "looking at a supported crop yields a growth tooltip line");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void nonCropWritesNothing(GameTestHelper helper) {
        helper.setBlock(CROP, Blocks.STONE);
        CompoundTag tag = new CompoundTag();
        var level = helper.getLevel();
        var cropAbs = helper.absolutePos(CROP);
        CropProbeTooltip.writeServerData(tag, level, cropAbs, level.getBlockState(cropAbs));
        helper.assertTrue(CropProbeTooltip.buildLines(tag).isEmpty(),
                "a non-crop block leaves the crop tooltip empty");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void cropWriterFlagsTheSnifferPremium(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 100.0F, Blocks.WHEAT);
        helper.setBlock(CROP, Blocks.WHEAT.defaultBlockState());
        helper.setBlock(CROP.west(), Blocks.TORCHFLOWER_CROP.defaultBlockState());
        helper.setBlock(CROP.east(), Blocks.POTATOES.defaultBlockState());
        CompoundTag tag = new CompoundTag();
        var level = helper.getLevel();
        var cropAbs = helper.absolutePos(CROP);
        CropProbeTooltip.writeServerData(tag, level, cropAbs, level.getBlockState(cropAbs));
        helper.assertTrue(hasLine(tag, "tooltip.cultivation.crop.sniffer"),
                "a sniffer-bordered polyculture crop flags the premium line");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void cropWriterOmitsSnifferLineWithoutAPartner(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 100.0F, Blocks.WHEAT);
        helper.setBlock(CROP, Blocks.WHEAT.defaultBlockState());
        helper.setBlock(CROP.west(), Blocks.CARROTS.defaultBlockState());
        helper.setBlock(CROP.east(), Blocks.POTATOES.defaultBlockState());
        CompoundTag tag = new CompoundTag();
        var level = helper.getLevel();
        var cropAbs = helper.absolutePos(CROP);
        CropProbeTooltip.writeServerData(tag, level, cropAbs, level.getBlockState(cropAbs));
        helper.assertTrue(!hasLine(tag, "tooltip.cultivation.crop.sniffer"),
                "a plain polyculture crop shows no sniffer line");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void snifferLineIsHiddenWhenTheBaseBonusIsZeroed(GameTestHelper helper) {
        // At the degenerate polycultureGrowthMultiplier of 1.0 the premium adds
        // nothing, so the tooltip must not promise a boost it isn't delivering.
        var config = com.rfizzle.cultivation.config.CultivationConfig.get();
        double saved = config.polycultureGrowthMultiplier;
        config.polycultureGrowthMultiplier = 1.0;
        try {
            placeTrackedFarmland(helper, FARM, 100.0F, Blocks.WHEAT);
            helper.setBlock(CROP, Blocks.WHEAT.defaultBlockState());
            helper.setBlock(CROP.west(), Blocks.TORCHFLOWER_CROP.defaultBlockState());
            helper.setBlock(CROP.east(), Blocks.POTATOES.defaultBlockState());
            CompoundTag tag = new CompoundTag();
            var level = helper.getLevel();
            var cropAbs = helper.absolutePos(CROP);
            CropProbeTooltip.writeServerData(tag, level, cropAbs, level.getBlockState(cropAbs));
            helper.assertTrue(!hasLine(tag, "tooltip.cultivation.crop.sniffer"),
                    "no sniffer line when the premium raises growth by nothing");
        } finally {
            config.polycultureGrowthMultiplier = saved;
        }
        helper.succeed();
    }

    /** Whether the crop tooltip built from {@code tag} carries a line with the given translation key. */
    private static boolean hasLine(CompoundTag tag, String key) {
        return CropProbeTooltip.buildLines(tag).stream()
                .map(net.minecraft.network.chat.Component::getContents)
                .filter(TranslatableContents.class::isInstance)
                .anyMatch(contents -> ((TranslatableContents) contents).getKey().equals(key));
    }
}
