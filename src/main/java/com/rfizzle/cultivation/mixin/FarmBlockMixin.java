package com.rfizzle.cultivation.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.rfizzle.cultivation.soil.SoilRecovery;
import com.rfizzle.cultivation.soil.TrampleResistance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two farmland seams:
 *
 * <ul>
 * <li><b>Live recovery</b> — fallow recovery rides vanilla farmland random
 * ticks, after the moisture handling. Vanilla can revert the block to dirt
 * inside the same call, so the recovery handler re-reads the world instead of
 * trusting the injected state.</li>
 * <li><b>Trample resistance</b> — {@code fallOn} reverts farmland to dirt on a
 * qualifying stomp via a single {@code turnToDirt} call; wrapping that call lets
 * enriched farmland shrug off a player's trample (SPEC §5) while leaving the
 * fall-damage {@code super.fallOn} intact. Skipping the revert never fires the
 * {@code onRemove} reversion seam, so no investment cleanup is owed.</li>
 * </ul>
 */
@Mixin(FarmBlock.class)
abstract class FarmBlockMixin {
    @Inject(method = "randomTick", at = @At("TAIL"))
    private void cultivation$fallowRecovery(
            BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        SoilRecovery.onFarmlandRandomTick(level, pos);
    }

    @WrapWithCondition(
            method = "fallOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/FarmBlock;turnToDirt(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"
            )
    )
    private boolean cultivation$resistTrample(Entity entity, BlockState state, Level level, BlockPos pos) {
        return !TrampleResistance.shouldResist(entity, level, pos);
    }
}
