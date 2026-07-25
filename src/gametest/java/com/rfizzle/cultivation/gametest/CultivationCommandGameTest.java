package com.rfizzle.cultivation.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.rfizzle.cultivation.api.CultivationAPI;
import com.rfizzle.cultivation.command.CommandText;
import com.rfizzle.cultivation.command.CultivationCommand;
import com.rfizzle.cultivation.attachment.DietData;
import com.rfizzle.cultivation.attachment.DietStore;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.config.CultivationConfig;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Drives the {@code /cultivation} tree (SPEC §9) through the real dispatcher:
 * per-node permission gating, and the two mutations plus reload run end-to-end.
 * The pure formatting is covered at Tier 1 in {@code CommandTextTest}; these
 * prove the tree is wired, gated, and routes writes through the stores.
 */
public class CultivationCommandGameTest implements FabricGameTest {
    /**
     * Reload swaps the process-wide {@link CultivationConfig} singleton, and tests in one batch
     * tick simultaneously — a swap mid-flight would hand a default-batch test that flipped a
     * config field in place (e.g. {@code ScytheSweepGameTest}) a fresh instance where its flip
     * never happened. Batches run sequentially, so the reload tests get their own.
     */
    private static final String CONFIG_BATCH = "cultivationCommandConfig";

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void treeGatesEachNodeByPermission(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CommandNode<CommandSourceStack> root = server.getCommands().getDispatcher().getRoot().getChild("cultivation");
        helper.assertTrue(root != null, "the /cultivation root is registered");

        CommandSourceStack nonOp = server.createCommandSourceStack().withPermission(0);
        CommandSourceStack op = server.createCommandSourceStack().withPermission(2);

        CommandNode<CommandSourceStack> soil = root.getChild("soil");
        CommandNode<CommandSourceStack> diet = root.getChild("diet");
        helper.assertTrue(soil.canUse(nonOp), "soil read is public");
        helper.assertTrue(root.getChild("field").canUse(nonOp), "field read is public");
        helper.assertTrue(diet.canUse(nonOp), "diet read is public");
        helper.assertFalse(soil.getChild("set").canUse(nonOp), "soil set denies non-ops");
        helper.assertTrue(soil.getChild("set").canUse(op), "soil set allows ops");
        helper.assertFalse(diet.getChild("reset").canUse(nonOp), "diet reset denies non-ops");
        helper.assertTrue(diet.getChild("reset").canUse(op), "diet reset allows ops");
        helper.assertFalse(root.getChild("reload").canUse(nonOp), "reload denies non-ops");
        helper.assertTrue(root.getChild("reload").canUse(op), "reload allows ops");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void soilSetMutatesTheLookedAtFarmland(GameTestHelper helper) throws CommandSyntaxException {
        BlockPos rel = new BlockPos(1, 1, 1);
        helper.setBlock(rel, Blocks.FARMLAND);
        BlockPos abs = helper.absolutePos(rel);

        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            aimStraightDownAt(player, abs);
            MinecraftServer server = helper.getLevel().getServer();
            CommandSourceStack op = player.createCommandSourceStack().withPermission(2);

            int result = server.getCommands().getDispatcher().execute("cultivation soil set 40", op);
            helper.assertTrue(result > 0, "soil set on targeted farmland succeeds");
            float fertility = CultivationAPI.getSoilInfo(helper.getLevel(), abs).orElseThrow().fertility();
            helper.assertTrue(Math.abs(fertility - 40.0F) < 1e-4, "fertility is set to 40, got " + fertility);
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void soilSetResolvesSecondWaveCropGround(GameTestHelper helper) throws CommandSyntaxException {
        BlockPos groundRel = new BlockPos(1, 1, 1);
        BlockPos wartRel = groundRel.above();
        helper.setBlock(groundRel, Blocks.SOUL_SAND);
        helper.setBlock(wartRel, Blocks.NETHER_WART.defaultBlockState().setValue(BlockStateProperties.AGE_3, 3));
        BlockPos groundAbs = helper.absolutePos(groundRel);

        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            // Aim at the wart itself — the command resolves the soul sand tracked below it.
            aimStraightDownAt(player, helper.absolutePos(wartRel));
            MinecraftServer server = helper.getLevel().getServer();
            CommandSourceStack op = player.createCommandSourceStack().withPermission(2);

            int result = server.getCommands().getDispatcher().execute("cultivation soil set 40", op);
            helper.assertTrue(result > 0, "soil set resolves the wart's soul sand and succeeds");
            float fertility = CultivationAPI.getSoilInfo(helper.getLevel(), groundAbs).orElseThrow().fertility();
            helper.assertTrue(Math.abs(fertility - 40.0F) < 1e-4,
                    "the soul sand below the wart is set to 40, got " + fertility);
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void soilReportFailsWhenNotLookingAtFarmland(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            // Aim up at open sky — nothing to hit within reach.
            player.moveTo(player.getX(), player.getY(), player.getZ(), 0.0F, -90.0F);
            MinecraftServer server = helper.getLevel().getServer();
            CommandSourceStack source = player.createCommandSourceStack();

            int result = server.getCommands().getDispatcher().execute("cultivation soil", source);
            helper.assertTrue(result == 0, "soil report fails with no farmland in sight");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void fieldReportFailsWhenNotLookingAtFarmland(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            // Aim up at open sky — no farmland center to survey around.
            player.moveTo(player.getX(), player.getY(), player.getZ(), 0.0F, -90.0F);
            MinecraftServer server = helper.getLevel().getServer();
            CommandSourceStack source = player.createCommandSourceStack();

            int result = server.getCommands().getDispatcher().execute("cultivation field", source);
            helper.assertTrue(result == 0, "field report fails with no farmland in sight");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void fieldSurveyReportsOverAPlot(GameTestHelper helper) throws CommandSyntaxException {
        ServerLevel level = helper.getLevel();
        ResourceLocation wheat = BuiltInRegistries.BLOCK.getKey(Blocks.WHEAT);
        ResourceLocation carrots = BuiltInRegistries.BLOCK.getKey(Blocks.CARROTS);
        // A 3×3 farmland plot; the radius-4 survey walks past it but skips non-farmland columns.
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.FARMLAND);
            }
        }
        // Vary the soil so the survey aggregates a real mix: one exhausted wheat block,
        // one enriched carrot block, the rest pristine.
        SoilStores.update(level, helper.absolutePos(new BlockPos(0, 1, 0)), false,
                data -> data.withFertility(0.0F).withLastCrop(wheat));
        SoilStores.update(level, helper.absolutePos(new BlockPos(2, 1, 2)), false,
                data -> data.withEnrichedChance(15).withLastCrop(carrots));

        BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            aimStraightDownAt(player, center);
            MinecraftServer server = helper.getLevel().getServer();

            int result = server.getCommands().getDispatcher()
                    .execute("cultivation field", player.createCommandSourceStack());
            helper.assertTrue(result > 0, "field survey succeeds over a placed plot");

            // The aggregate carries the per-block reads through: 9 farmland, the one
            // fertility-0 block exhausted, the one enriched block counted, no doses, and
            // both remembered crops in spatial encounter order (wheat before carrots).
            CultivationCommand.FieldReport report = CultivationCommand.surveyField(level, center);
            CommandText.FieldSummary summary = report.summary();
            helper.assertTrue(summary.soil() == 9, "survey covers all 9 soil blocks, got " + summary.soil());
            helper.assertTrue(summary.exhausted() == 1, "one block is exhausted, got " + summary.exhausted());
            helper.assertTrue(summary.enriched() == 1, "one block is enriched, got " + summary.enriched());
            helper.assertTrue(summary.fertilized() == 0, "no blocks are fertilized, got " + summary.fertilized());
            helper.assertTrue(report.crops().equals(List.of(wheat, carrots)),
                    "distinct crops are wheat then carrots, got " + report.crops());
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void dietResetClearsTheTargetThroughTheStore(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            ResourceLocation carrot = BuiltInRegistries.ITEM.getKey(Items.CARROT);
            DietStore.set(player, new DietData(Map.of(carrot, 3), List.of(carrot)));
            helper.assertFalse(DietStore.get(player).isDefault(), "player is fatigued before the reset");

            MinecraftServer server = helper.getLevel().getServer();
            CommandSourceStack op = player.createCommandSourceStack().withPermission(2);
            int result = server.getCommands().getDispatcher().execute("cultivation diet reset", op);

            helper.assertTrue(result > 0, "diet reset succeeds");
            helper.assertTrue(DietStore.get(player).isDefault(), "diet reset clears the player's diet data");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void dietReadReportsFatigueForTheCaller(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            ResourceLocation carrot = BuiltInRegistries.ITEM.getKey(Items.CARROT);
            DietStore.set(player, new DietData(Map.of(carrot, 2), List.of(carrot)));

            MinecraftServer server = helper.getLevel().getServer();
            CommandSourceStack source = player.createCommandSourceStack();
            int result = server.getCommands().getDispatcher().execute("cultivation diet", source);

            helper.assertTrue(result > 0, "diet read succeeds for a fatigued caller");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = CONFIG_BATCH)
    public void reloadSucceedsForOps(GameTestHelper helper) throws CommandSyntaxException {
        MinecraftServer server = helper.getLevel().getServer();
        CommandSourceStack op = server.createCommandSourceStack().withPermission(2);
        int result = server.getCommands().getDispatcher().execute("cultivation reload", op);
        helper.assertTrue(result > 0, "reload succeeds for an operator source");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = CONFIG_BATCH)
    public void reloadReportsFailureWhenTheConfigFileIsUnreadable(GameTestHelper helper) throws CommandSyntaxException {
        MinecraftServer server = helper.getLevel().getServer();
        CommandSourceStack op = server.createCommandSourceStack().withPermission(2);
        Path path = FabricLoader.getInstance().getConfigDir().resolve("cultivation.json");

        byte[] original = null;
        try {
            if (Files.exists(path)) {
                original = Files.readAllBytes(path);
            }
            Files.writeString(path, "{ this is not json");

            int result = server.getCommands().getDispatcher().execute("cultivation reload", op);

            helper.assertTrue(result == 0, "reload reports failure when the config file cannot be read");
            helper.assertTrue(CultivationConfig.get().harvestDrain == new CultivationConfig().harvestDrain,
                    "a rejected reload still leaves the server running on defaults");
            helper.assertTrue("{ this is not json".equals(Files.readString(path)),
                    "a rejected reload must never rewrite the operator's file");
        } catch (IOException e) {
            throw new AssertionError("could not stage the malformed config", e);
        } finally {
            try {
                if (original != null) {
                    Files.write(path, original);
                } else {
                    Files.deleteIfExists(path);
                }
            } catch (IOException e) {
                // The restore is the only thing keeping the corrupt file out of the rest of the
                // run, so a failure here invalidates every later test rather than just this one.
                throw new AssertionError("could not restore the config file after the test", e);
            } finally {
                CultivationConfig.reload();
            }
        }
        helper.succeed();
    }

    /** Places the player centered above {@code target} looking straight down, so a pick clips its top face. */
    private static void aimStraightDownAt(ServerPlayer player, BlockPos target) {
        player.moveTo(target.getX() + 0.5, target.getY() + 2, target.getZ() + 0.5, 0.0F, 90.0F);
    }
}
