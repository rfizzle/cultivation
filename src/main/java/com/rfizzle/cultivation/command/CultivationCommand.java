package com.rfizzle.cultivation.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rfizzle.cultivation.Cultivation;
import com.rfizzle.cultivation.api.CultivationAPI;
import com.rfizzle.cultivation.api.SoilInfo;
import com.rfizzle.cultivation.attachment.CultivationAttachments;
import com.rfizzle.cultivation.attachment.DietData;
import com.rfizzle.cultivation.attachment.DietStore;
import com.rfizzle.cultivation.attachment.SoilData;
import com.rfizzle.cultivation.attachment.SoilStore;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.network.ConfigNetworking;
import com.rfizzle.cultivation.soil.SoilBand;
import com.rfizzle.cultivation.soil.SoilMath;
import com.rfizzle.cultivation.soil.SupportedCrops;
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
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The {@code /cultivation} admin/debug command tree ({@code design/SPEC.md} §9).
 * Read verbs ({@code soil}, {@code field}, {@code diet}) stay at permission 0; every mutation
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

    /** Half-extent of the {@code field} survey square around the looked-at block — radius 4 is a 9×9 plot. */
    private static final int FIELD_RADIUS = 4;

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
                .then(Commands.literal("field")
                        .executes(CultivationCommand::runFieldReport))
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
        Optional<BlockPos> target = lookedAtSoil(player);
        if (target.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable(
                    "command.cultivation.soil.not_soil", (int) SOIL_REACH));
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
        Optional<BlockPos> target = lookedAtSoil(player);
        if (target.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable(
                    "command.cultivation.soil.not_soil", (int) SOIL_REACH));
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

    // --- field ---

    private static int runFieldReport(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        Optional<BlockPos> target = lookedAtSoil(player);
        if (target.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable(
                    "command.cultivation.soil.not_soil", (int) SOIL_REACH));
            return 0;
        }
        FieldReport report = surveyField(level, target.get());
        CommandText.FieldSummary summary = report.summary();
        int diameter = FIELD_RADIUS * 2 + 1;

        ctx.getSource().sendSuccess(() -> Component.translatable("command.cultivation.field.report",
                diameter, summary.soil(), summary.avgPercent(),
                Component.translatable(CommandText.bandKey(summary.band()))), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.cultivation.field.counts",
                summary.exhausted(), summary.enriched(), summary.fertilized()), false);
        List<ResourceLocation> distinctCrops = report.crops();
        if (distinctCrops.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.cultivation.field.crops.none"), false);
        } else {
            MutableComponent names = joinCrops(distinctCrops);
            ctx.getSource().sendSuccess(() -> Component.translatable("command.cultivation.field.crops", names), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * The testable core of {@code /cultivation field}: surveys the plot around
     * {@code center} and returns its aggregate. Read-only — walks only loaded
     * chunks and never touches the soil write choke point. The Brigadier handler
     * is the thin shell over this; gametests call it directly to assert the
     * aggregated numbers.
     */
    public static FieldReport surveyField(ServerLevel level, BlockPos center) {
        List<CommandText.FieldBlock> blocks = new ArrayList<>();
        List<ResourceLocation> crops = new ArrayList<>();
        surveyPlot(level, center, blocks, crops);
        CommandText.FieldSummary summary = CommandText.summarize(blocks, CultivationConfig.get().tiredThreshold);
        return new FieldReport(summary, CommandText.distinct(crops));
    }

    /** The aggregate a field survey produces: the numeric summary plus the distinct crops in rotation. */
    public record FieldReport(CommandText.FieldSummary summary, List<ResourceLocation> crops) {
    }

    /**
     * Walks the {@link #FIELD_RADIUS} square around {@code center} at its Y level,
     * collecting each farmland column's soil into {@code blocks} and its remembered
     * crop into {@code crops}. Reads only already-loaded chunks ({@code getChunkNow}
     * + null-skip) so a plot straddling the render-distance edge never force-loads;
     * untracked columns count as pristine full fertility.
     */
    private static void surveyPlot(ServerLevel level, BlockPos center,
                                   List<CommandText.FieldBlock> blocks, List<ResourceLocation> crops) {
        int y = center.getY();
        boolean toggle = CultivationConfig.get().enableNonFarmlandSoil;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -FIELD_RADIUS; dx <= FIELD_RADIUS; dx++) {
            for (int dz = -FIELD_RADIUS; dz <= FIELD_RADIUS; dz++) {
                pos.set(center.getX() + dx, y, center.getZ() + dz);
                LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                if (chunk == null || !SupportedCrops.isTrackedSoilGround(
                        chunk.getBlockState(pos), chunk.getBlockState(pos.above()), toggle)) {
                    continue;
                }
                SoilStore store = chunk.getAttached(CultivationAttachments.SOIL);
                SoilData data = store == null ? null : store.get(SoilStore.pack(pos));
                if (data == null) {
                    blocks.add(new CommandText.FieldBlock(SoilMath.MAX_FERTILITY, 0, 0));
                } else {
                    blocks.add(new CommandText.FieldBlock(
                            data.fertility(), data.enrichedChance(), data.fertilizerRemaining()));
                    data.lastCrop().ifPresent(crops::add);
                }
            }
        }
    }

    private static MutableComponent joinCrops(List<ResourceLocation> crops) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (ResourceLocation id : crops) {
            if (!first) {
                out.append(Component.literal(", "));
            }
            first = false;
            out.append(BuiltInRegistries.BLOCK.get(id).getName());
        }
        return out;
    }

    /**
     * The soil position the player is looking at: farmland or a second-wave crop's
     * ground, whether the crosshair is on the soil block itself or on the crop
     * standing on it. Empty when neither the looked-at block nor the block below it
     * is tracked soil.
     */
    private static Optional<BlockPos> lookedAtSoil(ServerPlayer player) {
        HitResult hit = player.pick(SOIL_REACH, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        ServerLevel level = player.serverLevel();
        boolean toggle = CultivationConfig.get().enableNonFarmlandSoil;
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        // Looking straight at the soil (farmland, or a crop's ground with the crop above).
        if (SupportedCrops.isTrackedSoilGround(level.getBlockState(pos), level.getBlockState(pos.above()), toggle)) {
            return Optional.of(pos);
        }
        // Looking at the crop itself — read the tracked ground directly below it.
        BlockPos below = pos.below();
        if (SupportedCrops.isTrackedSoilGround(level.getBlockState(below), level.getBlockState(pos), toggle)) {
            return Optional.of(below);
        }
        return Optional.empty();
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
        } else {
            // Foods eaten but no fatigue accrued (e.g. decay disabled) — state it plainly rather than
            // leaving only the recent-foods line.
            src.sendSuccess(() -> Component.translatable("command.cultivation.diet.none"), false);
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
        // Push the freshly loaded rules to every connected client so their config-derived
        // surfaces (diet tooltips) reflect the change without a rejoin.
        ConfigNetworking.syncAll(src.getServer());
        src.sendSuccess(() -> Component.translatable("command.cultivation.reload"), true);
        return Command.SINGLE_SUCCESS;
    }
}
