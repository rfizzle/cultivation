package com.rfizzle.cultivation.event;

import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.soil.SupportedCrops;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Broadcast sowing ({@code design/SPEC.md} §7): the planting mirror of the
 * scythe sweep. Sneak-right-clicking farmland with an in-scope crop seed in the
 * main hand sows the 3×3 of farmland centered on the targeted block at age 0 —
 * one seed consumed per block actually planted, occupied or unsuitable positions
 * skipped. A non-sneak right-click with a seed is left to vanilla single-block
 * planting; the scope is the six farmland replant crops
 * ({@link SupportedCrops#plantableCropForSeed}), so nether wart and sweet berries
 * are passed through untouched.
 *
 * <p>A plain {@code UseBlockCallback} listener, server-side only. It stands down
 * — returning {@code PASS}, which leaves vanilla's single-seed placement intact —
 * when {@code enableBroadcastSowing} is off, the player is not sneaking, the main
 * hand is not an in-scope seed, or the click does not resolve to a farmland
 * block. Planting is not a harvest: it touches no soil state, so a fresh sow
 * never drains fertility or records rotation memory. Each valid position is
 * validated exactly as vanilla would ({@code canBeReplaced} for emptiness,
 * {@code canSurvive} for farmland-below and light), so the gesture never plants
 * where a single seed could not.
 */
public final class BroadcastSowingHandler implements UseBlockCallback {
    // Match the shared replant seam: update clients, and keep the known shape so
    // the fresh crop does not trigger neighbor reactions on placement.
    private static final int PLANT_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private BroadcastSowingHandler() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register(new BroadcastSowingHandler());
    }

    @Override
    public InteractionResult interact(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS; // the seed is held in the main hand; never sow off the off-hand
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)
                || player.isSpectator()) {
            return InteractionResult.PASS;
        }
        if (!CultivationConfig.get().enableBroadcastSowing) {
            return InteractionResult.PASS; // disabled: a sneak-right-click plants one seed, as in vanilla
        }
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS; // no sneak: vanilla single-block planting
        }
        ItemStack seed = player.getMainHandItem();
        Block crop = SupportedCrops.plantableCropForSeed(seed);
        if (crop == null) {
            return InteractionResult.PASS; // not an in-scope crop seed: vanilla use
        }
        BlockPos center = farmlandAnchor(serverLevel, hit.getBlockPos());
        if (center == null) {
            return InteractionResult.PASS; // the click did not land on (or above) farmland
        }
        int planted = sow(serverLevel, serverPlayer, seed, crop, center);
        // SUCCESS consumes the interaction and suppresses the vanilla single plant;
        // when nothing could be sown (the whole 3×3 was occupied) fall back to
        // vanilla so a lone seed can still land on the clicked block.
        return planted > 0 ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /**
     * The farmland block the 3×3 centers on: the clicked block when it is farmland
     * (the standard plant-on-top gesture), else the farmland directly beneath a
     * clicked crop, else null.
     */
    @Nullable
    private static BlockPos farmlandAnchor(ServerLevel level, BlockPos hitPos) {
        if (level.getBlockState(hitPos).is(Blocks.FARMLAND)) {
            return hitPos;
        }
        if (level.getBlockState(hitPos.below()).is(Blocks.FARMLAND)) {
            return hitPos.below();
        }
        return null;
    }

    private static int sow(ServerLevel level, ServerPlayer player, ItemStack seed, Block crop, BlockPos center) {
        // Creative sows the whole 3×3 without spending seeds; survival is capped by
        // the stack, one seed per planted block.
        int budget = player.getAbilities().instabuild ? 9 : seed.getCount();
        BlockState plantState = plantState(crop);
        int planted = 0;
        for (int dx = -1; dx <= 1 && planted < budget; dx++) {
            for (int dz = -1; dz <= 1 && planted < budget; dz++) {
                BlockPos farmland = center.offset(dx, 0, dz);
                if (!level.getBlockState(farmland).is(Blocks.FARMLAND)) {
                    continue; // only farmland is sown
                }
                BlockPos cropPos = farmland.above();
                BlockState target = level.getBlockState(cropPos);
                if (!target.canBeReplaced() || !plantState.canSurvive(level, cropPos)) {
                    continue; // occupied, or the crop could not survive here (light)
                }
                level.setBlock(cropPos, plantState, PLANT_FLAGS);
                planted++;
            }
        }
        if (planted > 0) {
            seed.consume(planted, player); // a no-op in creative (instabuild)
            SoundType sound = plantState.getSoundType();
            level.playSound(null, center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                    sound.getPlaceSound(), SoundSource.BLOCKS,
                    (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        }
        return planted;
    }

    /** The age-0 state to sow: a pitcher's single-block pod, or any crop at age 0. */
    private static BlockState plantState(Block crop) {
        if (crop instanceof PitcherCropBlock) {
            return crop.defaultBlockState(); // age 0 is a single-block lower half
        }
        return ((CropBlock) crop).getStateForAge(0);
    }
}
