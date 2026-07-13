package com.rfizzle.cultivation.event;

import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.item.RakeItem;
import com.rfizzle.cultivation.soil.SupportedCrops;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
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
 * scythe sweep, gated behind the iron rake. Right-clicking farmland with a
 * {@link RakeItem} in the main hand and an in-scope crop seed in the off-hand
 * sows the 3×3 of farmland centered on the targeted block at age 0 — one seed
 * drawn from the off-hand and one rake durability spent per block planted,
 * occupied or unsuitable positions skipped. The scope is the six farmland
 * replant crops ({@link SupportedCrops#plantableCropForSeed}), so nether wart and
 * sweet berries are passed through untouched. Without the rake, planting stays
 * vanilla single-block.
 *
 * <p>A plain {@code UseBlockCallback} listener. The sow runs only on the server;
 * on the client a valid gesture returns {@code SUCCESS} so Fabric consumes the
 * interaction on the main-hand pass — suppressing the off-hand seed's predicted
 * single-block placement — and forwards it to the server, which owns the effect.
 * It stands down — returning {@code PASS} — when {@code enableBroadcastSowing} is
 * off, the main hand is not a rake, the off-hand is not an in-scope seed, or the
 * click does not resolve to a farmland block. Planting is not a harvest: it
 * touches no soil state, so a fresh sow never drains fertility or records
 * rotation memory. Each valid position is validated as vanilla would place a seed
 * ({@code canBeReplaced} for emptiness, {@code canSurvive} for farmland-below and
 * light), so the rake never sows where a single seed could not.
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
            return InteractionResult.PASS; // the rake is swung from the main hand; the off-hand pass is not ours
        }
        if (player.isSpectator() || !CultivationConfig.get().enableBroadcastSowing) {
            return InteractionResult.PASS; // disabled: the rake sows nothing, off-hand seeds plant one block as in vanilla
        }
        if (!(player.getMainHandItem().getItem() instanceof RakeItem)) {
            return InteractionResult.PASS; // no rake: not the sowing gesture
        }
        ItemStack seed = player.getOffhandItem();
        Block crop = SupportedCrops.plantableCropForSeed(seed);
        if (crop == null) {
            return InteractionResult.PASS; // the off-hand holds no in-scope crop seed
        }
        BlockPos center = farmlandAnchor(level, hit.getBlockPos());
        if (center == null) {
            return InteractionResult.PASS; // the click did not land on (or above) farmland
        }
        // The gesture applies. These checks run identically on the client, where
        // returning SUCCESS makes Fabric cancel the off-hand seed's predicted
        // single-block placement and forward the interaction to the server — the
        // only place the 3×3 is actually sown, so the effect and its one sound
        // happen exactly once.
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        int planted = sow(serverLevel, serverPlayer, player.getMainHandItem(), seed, crop, center);
        // When nothing could be sown (the whole 3×3 was occupied) fall back to
        // vanilla so a lone off-hand seed can still land on the clicked block.
        return planted > 0 ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /**
     * The farmland block the 3×3 centers on: the clicked block when it is farmland
     * (the standard plant-on-top gesture), else the farmland directly beneath a
     * clicked crop, else null.
     */
    @Nullable
    private static BlockPos farmlandAnchor(Level level, BlockPos hitPos) {
        if (level.getBlockState(hitPos).is(Blocks.FARMLAND)) {
            return hitPos;
        }
        if (level.getBlockState(hitPos.below()).is(Blocks.FARMLAND)) {
            return hitPos.below();
        }
        return null;
    }

    private static int sow(ServerLevel level, ServerPlayer player, ItemStack rake, ItemStack seed,
            Block crop, BlockPos center) {
        // Creative sows the whole 3×3 for free; survival spends one off-hand seed
        // and one rake durability per planted block, stopping when either runs out.
        boolean creative = player.getAbilities().instabuild;
        BlockState plantState = plantState(crop);
        int planted = 0;
        outer:
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!creative && (seed.isEmpty() || rake.isEmpty())) {
                    break outer; // out of seeds, or the rake broke mid-sow
                }
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
                if (!creative) {
                    seed.shrink(1);
                    rake.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                }
            }
        }
        if (planted > 0) {
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
