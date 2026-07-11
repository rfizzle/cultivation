package com.rfizzle.cultivation.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.rfizzle.cultivation.soil.SoilGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.PitcherCropBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The pitcher crop's growth roll rides the same soil modifier. Only the lower
 * half random-ticks, so the farmland lookup below the ticking position is
 * always correct.
 */
@Mixin(PitcherCropBlock.class)
abstract class PitcherCropBlockMixin {
    @ModifyExpressionValue(
            method = "randomTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/CropBlock;getGrowthSpeed(Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"
            )
    )
    private float cultivation$soilGrowthSpeed(
            float original, @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) BlockPos pos) {
        return original * SoilGrowth.multiplierAt(level, pos);
    }
}
