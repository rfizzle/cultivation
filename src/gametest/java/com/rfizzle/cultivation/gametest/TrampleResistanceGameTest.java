package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.attachment.SoilData;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.config.CultivationConfig;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static com.rfizzle.cultivation.gametest.SoilFixtures.FARM;
import static com.rfizzle.cultivation.gametest.SoilFixtures.TEMPLATE;
import static com.rfizzle.cultivation.gametest.SoilFixtures.placeTrackedFarmland;

/**
 * SPEC §5 trample resistance: enriched farmland shrugs off a player's stomp
 * while plain farmland reverts as vanilla does, mobs still trample enriched
 * ground (Tribulation's danger stays untouched), and the toggle switches it all
 * off. Each case drives the vanilla {@code FarmBlock#fallOn} seam directly with
 * a fall distance large enough to make its RNG gate always pass, so the outcome
 * turns only on the resistance decision, not on physics luck.
 */
public class TrampleResistanceGameTest implements FabricGameTest {
    /**
     * Double the 100-tick default. These are the config-toggle tests: each flips a
     * field, drives the full in-world path behind it, and restores the field in a
     * finally. They pay for a config reload plus the same tick budget the untoggled
     * test needs, and a timeout here fails the restore as well as the assertion.
     */
    private static final int CONFIG_TOGGLE_TIMEOUT = 200;

    private static final String CONFIG_BATCH = "cultivationTrampleConfig";
    private static final float FORCED_FALL = 100.0F;

    @GameTest(template = TEMPLATE)
    public void enrichedFarmlandSurvivesPlayerTrample(GameTestHelper helper) {
        enrich(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        try {
            trample(helper, player);
        } finally {
            player.discard();
        }
        helper.assertBlockPresent(Blocks.FARMLAND, FARM);
        // A resisted trample changes nothing at the position (SPEC §1/§5).
        SoilData data = SoilFixtures.data(helper, FARM);
        helper.assertTrue(data != null && data.enrichedChance() == 15,
                "a resisted trample must leave the enrichment intact");
        helper.assertTrue(data.fertility() > 79.0F && data.fertility() < 81.0F,
                "a resisted trample must not disturb fertility");
        helper.assertTrue(data.lastCrop().map(SoilFixtures.idOf(Blocks.WHEAT)::equals).orElse(false),
                "a resisted trample must not disturb rotation memory");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void plainFarmlandTramplesForPlayer(GameTestHelper helper) {
        // No soil entry: enrichedChance is 0, so vanilla reversion must proceed.
        helper.setBlock(FARM, Blocks.FARMLAND);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        try {
            trample(helper, player);
        } finally {
            player.discard();
        }
        helper.assertBlockPresent(Blocks.DIRT, FARM);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void mobStillTramplesEnrichedFarmland(GameTestHelper helper) {
        enrich(helper);
        withMobGriefing(helper, () -> {
            Entity cow = helper.spawn(EntityType.COW, FARM);
            try {
                trample(helper, cow);
            } finally {
                cow.discard();
            }
        });
        helper.assertBlockPresent(Blocks.DIRT, FARM);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = CONFIG_BATCH, timeoutTicks = CONFIG_TOGGLE_TIMEOUT)
    public void disabledToggleTramplesEnrichedFarmland(GameTestHelper helper) {
        CultivationConfig config = CultivationConfig.get();
        boolean saved = config.enrichedSoilResistsTrampling;
        config.enrichedSoilResistsTrampling = false;
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        try {
            enrich(helper);
            trample(helper, player);
            helper.assertBlockPresent(Blocks.DIRT, FARM);
            helper.succeed();
        } finally {
            player.discard();
            config.enrichedSoilResistsTrampling = saved;
        }
    }

    /** Places enriched (15%) farmland at {@link SoilFixtures#FARM}. */
    private static void enrich(GameTestHelper helper) {
        placeTrackedFarmland(helper, FARM, 80.0F, Blocks.WHEAT);
        SoilStores.update(helper.getLevel(), helper.absolutePos(FARM), false, data -> data.withEnrichedChance(15));
    }

    /** Drives {@code FarmBlock#fallOn} at {@link SoilFixtures#FARM} with a guaranteed-trample fall distance. */
    private static void trample(GameTestHelper helper, Entity trampler) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(FARM);
        BlockState state = level.getBlockState(abs);
        state.getBlock().fallOn(level, state, abs, trampler, FORCED_FALL);
    }

    private static void withMobGriefing(GameTestHelper helper, Runnable body) {
        ServerLevel level = helper.getLevel();
        GameRules rules = level.getGameRules();
        boolean saved = rules.getBoolean(GameRules.RULE_MOBGRIEFING);
        rules.getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
        try {
            body.run();
        } finally {
            rules.getRule(GameRules.RULE_MOBGRIEFING).set(saved, level.getServer());
        }
    }
}
