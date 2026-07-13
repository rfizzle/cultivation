package com.rfizzle.cultivation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.rfizzle.cultivation.harvest.HarvestHandler;
import com.rfizzle.cultivation.soil.SoilGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/**
 * Brings the sweet berry bush into living soil (SPEC §1). Two seams:
 *
 * <ul>
 *   <li><b>Growth</b> — the bush grows when its {@code randomTick} rolls
 *       {@code nextInt(5) == 0}; the fertility band widens that bound so a bush on
 *       tired dirt ripens slower. Healthy soil keeps the vanilla bound.</li>
 *   <li><b>Pick drain</b> — picking berries is a {@code useWithoutItem} that never
 *       destroys the block, so it never reaches the drop-resolution seam the other
 *       harvests use. Wrapping its single {@code popResource} routes the picked
 *       berries through the one harvest choke point ({@link HarvestHandler}) —
 *       §1 drain and the exhausted yield clamp — before they drop, without adding
 *       a second drop path. The bush still resets to age 1 and persists as vanilla
 *       does; only the server side drains.</li>
 * </ul>
 */
@Mixin(SweetBerryBushBlock.class)
abstract class SweetBerryBushBlockMixin {
    @WrapOperation(
            method = "randomTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I")
    )
    private int cultivation$soilGrowthRoll(
            RandomSource random, int bound, Operation<Integer> original,
            @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) BlockPos pos) {
        return original.call(random, SoilGrowth.secondWaveGrowthBound(bound, level, pos));
    }

    @WrapOperation(
            method = "useWithoutItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/SweetBerryBushBlock;popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V"
            )
    )
    private void cultivation$drainOnPick(
            Level level, BlockPos pos, ItemStack picked, Operation<Void> original,
            @Local(argsOnly = true) BlockState state, @Local(argsOnly = true) Player player) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            List<ItemStack> drops = new ArrayList<>();
            drops.add(picked);
            HarvestHandler.onDropsResolved(state, serverLevel, pos, serverPlayer, drops);
            for (ItemStack drop : drops) {
                original.call(level, pos, drop);
            }
        } else {
            original.call(level, pos, picked);
        }
    }
}
