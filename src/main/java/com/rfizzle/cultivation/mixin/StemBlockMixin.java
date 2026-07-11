package com.rfizzle.cultivation.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.rfizzle.cultivation.soil.SoilGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Melon and pumpkin stems receive the growth modifier (SPEC §1) even though
 * they never drain and never receive yield bonuses — the fruit grows beside the
 * farmland, not on it.
 */
@Mixin(StemBlock.class)
abstract class StemBlockMixin {
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
