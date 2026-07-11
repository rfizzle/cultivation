package com.rfizzle.cultivation.mixin;

import com.rfizzle.cultivation.soil.SoilRecovery;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The live recovery seam: fallow recovery rides vanilla farmland random ticks,
 * after the moisture handling. Vanilla can revert the block to dirt inside the
 * same call, so the recovery handler re-reads the world instead of trusting the
 * injected state.
 */
@Mixin(FarmBlock.class)
abstract class FarmBlockMixin {
    @Inject(method = "randomTick", at = @At("TAIL"))
    private void cultivation$fallowRecovery(
            BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        SoilRecovery.onFarmlandRandomTick(level, pos);
    }
}
