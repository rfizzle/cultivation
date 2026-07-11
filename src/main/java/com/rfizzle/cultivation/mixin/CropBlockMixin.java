package com.rfizzle.cultivation.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.rfizzle.cultivation.soil.SoilGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Applies the soil growth modifier to every CropBlock growth roll — wheat,
 * carrots, potatoes, and (via their super calls) beetroots and the torchflower
 * crop. Multiplying the computed growth speed by exactly 1.0 for healthy soil
 * leaves the vanilla roll bit-identical.
 */
@Mixin(CropBlock.class)
abstract class CropBlockMixin {
    @ModifyExpressionValue(
            method = "randomTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/CropBlock;getGrowthSpeed(Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"
            )
    )
    private float cultivation$soilGrowthSpeed(
            float original, @Local(argsOnly = true) BlockState state,
            @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) BlockPos pos) {
        return original * SoilGrowth.multiplierAt(level, pos, state);
    }
}
