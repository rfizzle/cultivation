package com.rfizzle.cultivation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.rfizzle.cultivation.soil.SoilGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.NetherWartBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Slows nether wart's growth on tired soul sand (SPEC §1). Nether wart grows when
 * its {@code randomTick} rolls {@code nextInt(10) == 0}; widening that bound by
 * the soil's fertility band is the second-wave equivalent of the farmland crops'
 * {@code getGrowthSpeed} scaling, since wart never routes through that call.
 * Healthy soil leaves the bound at 10, so the roll stays bit-identical to vanilla.
 */
@Mixin(NetherWartBlock.class)
abstract class NetherWartBlockMixin {
    @WrapOperation(
            method = "randomTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I")
    )
    private int cultivation$soilGrowthRoll(
            RandomSource random, int bound, Operation<Integer> original,
            @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) BlockPos pos) {
        return original.call(random, SoilGrowth.secondWaveGrowthBound(bound, level, pos));
    }
}
