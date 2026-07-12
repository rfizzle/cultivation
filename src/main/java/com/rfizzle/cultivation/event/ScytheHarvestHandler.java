package com.rfizzle.cultivation.event;

import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.harvest.HarvestHandler;
import com.rfizzle.cultivation.harvest.SeedWithdrawal;
import com.rfizzle.cultivation.item.ScytheItem;
import com.rfizzle.cultivation.soil.SupportedCrops;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The scythe sweep ({@code design/SPEC.md} §7). Breaking a mature supported crop
 * with a scythe in the main hand cancels the vanilla single-block break and
 * reaps the surrounding 3×3 at the same Y instead: each mature supported crop
 * resolves its drops through the one harvest choke point
 * ({@link HarvestHandler}) — Fortune from the scythe, §1 drain, §5/§6 bonuses,
 * and the harvest callback — then one seed is withdrawn from that block's own
 * drops to replant it at age 0 (no seed → the block is left empty), the rest
 * spawns at the block, and the scythe loses one durability. The vanilla
 * sweep-attack sound plays once at the center.
 *
 * <p>This is a plain {@code PlayerBlockBreakEvents.BEFORE} listener, not a mixin.
 * The eight off-center positions are replayed through the same event so
 * per-block protection checks fire per block and a denied block is skipped; a
 * thread-local guard makes the handler inert during that replay so the sweep
 * never nests. When {@code enableScytheHarvest} is off, the handler stands down
 * and a scythe is an ordinary single-block tool.
 */
public final class ScytheHarvestHandler implements PlayerBlockBreakEvents.Before {
    // Server break events run on the server thread; the guard is thread-local so
    // the replayed off-center probes never re-enter the sweep on that thread.
    private static final ThreadLocal<Boolean> SWEEPING = ThreadLocal.withInitial(() -> false);

    // Clear/replant without letting the two pitcher halves react to each other or
    // to a stale neighbor shape — the sweep has already resolved every drop.
    private static final int REPLANT_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private ScytheHarvestHandler() {
    }

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register(new ScytheHarvestHandler());
    }

    @Override
    public boolean beforeBlockBreak(Level level, Player player, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity) {
        if (SWEEPING.get()) {
            return true; // re-entrant off-center protection probe — never nest a sweep
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        if (!CultivationConfig.get().enableScytheHarvest) {
            return true; // disabled: a scythe is an ordinary single-block tool
        }
        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof ScytheItem)) {
            return true;
        }
        if (SupportedCrops.matureProfile(state) == null) {
            return true; // immature crop, stem, or non-crop: a plain vanilla break
        }
        SWEEPING.set(true);
        try {
            sweep(serverLevel, serverPlayer, tool, pos);
        } finally {
            SWEEPING.set(false);
        }
        return false; // the sweep replaces the vanilla single-block break at the center
    }

    private static void sweep(ServerLevel level, ServerPlayer player, ItemStack tool, BlockPos center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = center.offset(dx, 0, dz);
                BlockState state = level.getBlockState(pos);
                SupportedCrops.CropProfile profile = SupportedCrops.matureProfile(state);
                if (profile == null) {
                    continue; // immature, stem, non-crop, or empty — untouched
                }
                boolean center0 = pos.equals(center);
                if (!center0 && !PlayerBlockBreakEvents.BEFORE.invoker()
                        .beforeBlockBreak(level, player, pos, state, level.getBlockEntity(pos))) {
                    continue; // a protection/claim mod denied this block
                }
                harvest(level, player, tool, pos, state, profile);
            }
        }
        level.playSound(null, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void harvest(ServerLevel level, ServerPlayer player, ItemStack tool, BlockPos pos,
            BlockState state, SupportedCrops.CropProfile profile) {
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool);
        drops = HarvestHandler.onDropsResolved(state, level, pos, player, drops);

        boolean seedFound = SeedWithdrawal.withdrawOne(drops, profile.seed());
        replant(level, pos, state, profile, seedFound);
        for (ItemStack stack : drops) {
            Block.popResource(level, pos, stack);
        }

        tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        player.awardStat(Stats.BLOCK_MINED.get(state.getBlock()));
    }

    private static void replant(ServerLevel level, BlockPos pos, BlockState state,
            SupportedCrops.CropProfile profile, boolean seedFound) {
        if (state.getBlock() instanceof PitcherCropBlock) {
            // §7: the pitcher harvests both halves and replants a pod at age 0.
            // Its mature drop is the plant, never a pod, so the replant is
            // unconditional; the orphaned upper half must be cleared explicitly.
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), REPLANT_FLAGS);
            level.setBlock(pos, Blocks.PITCHER_CROP.defaultBlockState(), REPLANT_FLAGS);
            return;
        }
        if (seedFound && BuiltInRegistries.BLOCK.get(profile.cropId()) instanceof CropBlock crop) {
            level.setBlock(pos, crop.getStateForAge(0), REPLANT_FLAGS);
        } else {
            // No seed to sow (an unlucky roll, or a crop whose mature drop is not
            // its seed): the block is left empty, farmland intact.
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), REPLANT_FLAGS);
        }
    }
}
