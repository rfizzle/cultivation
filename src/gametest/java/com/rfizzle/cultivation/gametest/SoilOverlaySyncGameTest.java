package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.attachment.SoilStore;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.network.SoilBandsS2CPayload;
import com.rfizzle.cultivation.network.SoilOverlayServer;
import com.rfizzle.cultivation.soil.SoilBand;
import com.rfizzle.cultivation.soil.SoilOverlayFlags;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

import static com.rfizzle.cultivation.gametest.SoilFixtures.FARM;
import static com.rfizzle.cultivation.gametest.SoilFixtures.TEMPLATE;
import static com.rfizzle.cultivation.gametest.SoilFixtures.placeTrackedFarmland;

/**
 * The server side of soil overlay sync (SPEC §1): the deviating-position set that
 * answers a chunk request, and the disabled-path inertness. The actual quad
 * rendering (placement, depth test, Sodium/EBE/Iris) is Manual Testing (SPEC §17)
 * — no headless client exists to assert pixels.
 */
public class SoilOverlaySyncGameTest implements FabricGameTest {
    private static final String BATCH = "cultivationOverlayDisabled";

    @GameTest(template = TEMPLATE)
    public void deviatingPositionsAreCollected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos farmAbs = helper.absolutePos(FARM);
        ChunkPos chunkPos = new ChunkPos(farmAbs);
        int key = SoilStore.pack(farmAbs);

        // Fair soil renders nothing — never in the set.
        placeTrackedFarmland(helper, FARM, 50.0F, Blocks.WHEAT);
        helper.assertTrue(flagsFor(level, chunkPos, key) == null,
                "Fair farmland must not be a deviating overlay position");

        // Tired soil is a crack overlay.
        SoilStores.update(level, farmAbs, false, data -> data.withFertility(10.0F));
        Byte tired = flagsFor(level, chunkPos, key);
        helper.assertTrue(tired != null && SoilOverlayFlags.band(tired) == SoilBand.TIRED,
                "Tired farmland must appear with the Tired band");

        // Exhausted soil is the heavier crack.
        SoilStores.update(level, farmAbs, false, data -> data.withFertility(0.0F));
        Byte exhausted = flagsFor(level, chunkPos, key);
        helper.assertTrue(exhausted != null && SoilOverlayFlags.band(exhausted) == SoilBand.EXHAUSTED,
                "Exhausted farmland must appear with the Exhausted band");

        // A Fertilizer dose on that Exhausted block composes both flecks and the crack.
        SoilStores.update(level, farmAbs, false,
                data -> data.withFertilizerRemaining(5).withEnrichedChance(10));
        Byte both = flagsFor(level, chunkPos, key);
        helper.assertTrue(both != null
                        && SoilOverlayFlags.band(both) == SoilBand.EXHAUSTED
                        && SoilOverlayFlags.hasDose(both)
                        && SoilOverlayFlags.isEnriched(both),
                "an invested Exhausted block must carry band, dose, and enriched flags together");

        // Removing the farmland drops the position from the set even though soil memory persists.
        helper.setBlock(FARM, Blocks.DIRT);
        helper.assertTrue(SoilStores.peek(level, farmAbs) != null,
                "soil memory must persist under the reverted block");
        helper.assertTrue(flagsFor(level, chunkPos, key) == null,
                "a non-farmland position must never be a deviating overlay position");

        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public void disabledSoilCollectsNothing(GameTestHelper helper) {
        CultivationConfig config = CultivationConfig.get();
        boolean saved = config.enableSoilFertility;
        config.enableSoilFertility = false;
        try {
            ServerLevel level = helper.getLevel();
            BlockPos farmAbs = helper.absolutePos(FARM);
            placeTrackedFarmland(helper, FARM, 10.0F, Blocks.WHEAT);
            helper.assertTrue(
                    SoilOverlayServer.collectChunkEntries(level, new ChunkPos(farmAbs)).isEmpty(),
                    "no overlay positions may be collected while soil fertility is disabled");
            helper.succeed();
        } finally {
            config.enableSoilFertility = saved;
        }
    }

    private static Byte flagsFor(ServerLevel level, ChunkPos chunkPos, int key) {
        List<SoilBandsS2CPayload.Entry> entries = SoilOverlayServer.collectChunkEntries(level, chunkPos);
        return entries.stream()
                .filter(entry -> entry.packedPos() == key)
                .map(SoilBandsS2CPayload.Entry::flags)
                .findFirst()
                .orElse(null);
    }

    @AfterBatch(batch = BATCH)
    public void restoreSoilToggle(ServerLevel level) {
        CultivationConfig.get().enableSoilFertility = true;
    }
}
