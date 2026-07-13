package com.rfizzle.cultivation.event;

import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.harvest.CropReplanter;
import com.rfizzle.cultivation.harvest.HarvestHandler;
import com.rfizzle.cultivation.harvest.SeedWithdrawal;
import com.rfizzle.cultivation.soil.SupportedCrops;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * The bare-hand right-click harvest ({@code design/SPEC.md} §7): the single-block
 * sibling of the scythe sweep. Right-clicking a mature supported crop with an
 * empty main hand reaps that one block through the single harvest choke point
 * ({@link HarvestHandler}) — §1 drain, §5/§6 bonuses, and the harvest callback —
 * then withdraws one seed from the block's own drops to replant it at age 0 via
 * the shared {@link CropReplanter} (no seed → the block is left empty), spawns
 * the rest, and plays the crop's own break sound. There is no tool in hand, so
 * no Fortune applies — the natural yield gap that keeps the scythe's 3×3 sweep
 * (Fortune, one swing) the tool for harvesting at scale.
 *
 * <p>A plain {@code UseBlockCallback} listener. It stands down when
 * {@code enableRightClickHarvest} is off, when the main hand is not empty, or on
 * an immature crop — every one of those is left as vanilla right-click behavior
 * (which, on a crop block, is nothing).
 */
public final class RightClickHarvestHandler implements UseBlockCallback {
    private RightClickHarvestHandler() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register(new RightClickHarvestHandler());
    }

    @Override
    public InteractionResult interact(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS; // "bare-hand" is the main hand; never act off the off-hand
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)
                || player.isSpectator()) {
            return InteractionResult.PASS;
        }
        if (!CultivationConfig.get().enableRightClickHarvest) {
            return InteractionResult.PASS; // disabled: a bare-hand right-click does nothing, as in vanilla
        }
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS; // a held item (sword, block, food) takes its own use, never a harvest
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = serverLevel.getBlockState(pos);
        SupportedCrops.CropProfile profile = SupportedCrops.matureProfile(state);
        if (profile == null) {
            return InteractionResult.PASS; // immature crop, stem, or non-crop: vanilla right-click behavior
        }
        harvest(serverLevel, serverPlayer, pos, state, profile);
        return InteractionResult.SUCCESS; // swings the arm and consumes the interaction
    }

    private static void harvest(ServerLevel level, ServerPlayer player, BlockPos pos,
            BlockState state, SupportedCrops.CropProfile profile) {
        // Bare hand: an empty tool, so vanilla loot resolves without Fortune.
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, ItemStack.EMPTY);
        drops = HarvestHandler.onDropsResolved(state, level, pos, player, drops);

        boolean seedFound = SeedWithdrawal.withdrawOne(drops, profile.seed());
        CropReplanter.replant(level, pos, state, profile, seedFound);
        for (ItemStack stack : drops) {
            Block.popResource(level, pos, stack);
        }

        // The vanilla "block destroyed" client effect: the crop's break sound and
        // its destroy-dust particles, so a bare-hand harvest reads like the
        // hand-break it stands in for (the mature state drives the particle
        // texture even though the block is already replanted).
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        player.awardStat(Stats.BLOCK_MINED.get(state.getBlock()));
    }
}
