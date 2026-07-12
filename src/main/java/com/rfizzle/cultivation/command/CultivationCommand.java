package com.rfizzle.cultivation.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rfizzle.cultivation.Cultivation;
import com.rfizzle.cultivation.api.CultivationAPI;
import com.rfizzle.cultivation.api.SoilInfo;
import com.rfizzle.cultivation.attachment.DietData;
import com.rfizzle.cultivation.attachment.DietStore;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.soil.SoilBand;
import com.rfizzle.cultivation.soil.SoilMath;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The {@code /cultivation} admin/debug command tree ({@code design/SPEC.md} §9).
 * Read verbs ({@code soil}, {@code diet}) stay at permission 0; every mutation
 * ({@code soil set}, {@code diet reset}, {@code reload}) is gated at permission
 * 2 on its own node. Soil and diet writes route through the {@link SoilStores}
 * and {@link DietStore} choke points so recovery settles, all-default entries
 * evict, chunks dirty, and overlay sync fires exactly as in normal play.
 *
 * <p>Localization scoping ({@code mc-commands}): every line this tree emits is
 * player-reachable (perm-0 reads) or an operator confirmation, so <em>all</em>
 * output is translatable {@code command.cultivation.*} — this tree carries no
 * literal op-telemetry dumps.
 */
public final class CultivationCommand {
    /** Fixed command reach for the looked-at farmland, independent of the player's interaction-range attribute. */
    private static final double SOIL_REACH = 10.0;

    private static final int MIN_FERTILITY = 0;
    private static final int MAX_FERTILITY = (int) SoilMath.MAX_FERTILITY;

    private CultivationCommand() {
    }

    /** Wires the tree onto the server dispatcher — called once from {@code onInitialize}. */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Cultivation.MOD_ID)
                .then(Commands.literal("soil")
                        .executes(CultivationCommand::runSoilReport)
                        .then(Commands.literal("set")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("fertility",
                                                IntegerArgumentType.integer(MIN_FERTILITY, MAX_FERTILITY))
                                        .executes(CultivationCommand::runSoilSet))))
                .then(Commands.literal("diet")
                        .executes(CultivationCommand::runDietSelf)
                        .then(Commands.literal("reset")
                                .requires(src -> src.hasPermission(2))
                                .executes(CultivationCommand::runDietResetSelf)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(CultivationCommand::runDietResetOther))))
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(CultivationCommand::runReload)));
    }

    // --- soil ---

    private static int runSoilReport(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        Optional<BlockPos> target = lookedAtFarmland(player);
        if (target.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable(
                    "command.cultivation.soil.not_farmland", (int) SOIL_REACH));
            return 0;
        }
        SoilInfo info = CultivationAPI.getSoilInfo(level, target.get()).orElseThrow();
        CultivationConfig config = CultivationConfig.get();
        SoilBand band = SoilMath.band(info.fertility(), config.tiredThreshold);
        MutableComponent msg = Component.translatable("command.cultivation.soil.report",
                CommandText.percent(info.fertility()), Component.translatable(CommandText.bandKey(band)));
        info.lastCrop().ifPresent(id -> msg.append(Component.translatable(
                "command.cultivation.soil.crop", BuiltInRegistries.BLOCK.get(id).getName())));
        if (info.enrichedChance() > 0) {
            msg.append(Component.translatable("command.cultivation.soil.enriched", info.enrichedChance()));
        }
        if (info.fertilizerRemaining() > 0) {
            msg.append(Component.translatable("command.cultivation.soil.fertilizer",
                    info.fertilizerRemaining(), config.fertilizerDoseHarvests));
        }
        ctx.getSource().sendSuccess(() -> msg, false);
        return Command.SINGLE_SUCCESS;
    }

    private static int runSoilSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        Optional<BlockPos> target = lookedAtFarmland(player);
        if (target.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable(
                    "command.cultivation.soil.not_farmland", (int) SOIL_REACH));
            return 0;
        }
        BlockPos pos = target.get();
        int value = IntegerArgumentType.getInteger(ctx, "fertility");
        float applied = applySoilSet(level, pos, value);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.cultivation.soil.set",
                CommandText.percent(applied), pos.toShortString()), true);
        return Command.SINGLE_SUCCESS;
    }

    /** Core soil-set op — routes the write through the store choke point; returns the applied (clamped) fertility. */
    public static float applySoilSet(ServerLevel level, BlockPos pos, int fertility) {
        SoilStores.update(level, pos, true, data -> data.withFertility(fertility));
        return SoilStores.fertilityAt(level, pos);
    }

    private static Optional<BlockPos> lookedAtFarmland(ServerPlayer player) {
        HitResult hit = player.pick(SOIL_REACH, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        if (!player.serverLevel().getBlockState(pos).is(Blocks.FARMLAND)) {
            return Optional.empty();
        }
        return Optional.of(pos);
    }

    // --- diet ---

    private static int runDietSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        DietData data = DietStore.get(player);
        if (data.isDefault()) {
            src.sendSuccess(() -> Component.translatable("command.cultivation.diet.none"), false);
            return Command.SINGLE_SUCCESS;
        }
        if (!data.stacks().isEmpty()) {
            MutableComponent entries = joinFatigue(data, CultivationConfig.get());
            src.sendSuccess(() -> Component.translatable("command.cultivation.diet.fatigue", entries), false);
        }
        List<ResourceLocation> recent = CommandText.lastFoods(data.history(), 3);
        if (!recent.isEmpty()) {
            MutableComponent foods = joinFoods(recent);
            src.sendSuccess(() -> Component.translatable("command.cultivation.diet.recent", foods), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int runDietResetSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        resetDiet(player);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.cultivation.diet.reset", player.getDisplayName()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int runDietResetOther(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        resetDiet(target);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.cultivation.diet.reset", target.getDisplayName()), true);
        return Command.SINGLE_SUCCESS;
    }

    /** Core diet-reset op — clears the player's diet through the store, which evicts the attachment. */
    public static void resetDiet(ServerPlayer player) {
        DietStore.set(player, DietData.EMPTY);
    }

    private static MutableComponent joinFatigue(DietData data, CultivationConfig config) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (Map.Entry<ResourceLocation, Integer> entry : data.stacks().entrySet()) {
            if (!first) {
                out.append(Component.literal(", "));
            }
            first = false;
            int reduction = DietData.reductionPercent(
                    DietData.effectiveness(entry.getValue(), config.fatiguePerRepeat, config.fatigueFloor));
            out.append(Component.translatable("command.cultivation.diet.entry",
                    BuiltInRegistries.ITEM.get(entry.getKey()).getDescription(), reduction));
        }
        return out;
    }

    private static MutableComponent joinFoods(List<ResourceLocation> foods) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (ResourceLocation id : foods) {
            if (!first) {
                out.append(Component.literal(", "));
            }
            first = false;
            out.append(BuiltInRegistries.ITEM.get(id).getDescription());
        }
        return out;
    }

    // --- reload ---

    private static int runReload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            CultivationConfig.reload();
        } catch (Exception e) {
            Cultivation.LOGGER.error("Config reload failed via command", e);
            src.sendFailure(Component.translatable(
                    "command.cultivation.reload_failed", String.valueOf(e.getMessage())));
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("command.cultivation.reload"), true);
        return Command.SINGLE_SUCCESS;
    }
}
