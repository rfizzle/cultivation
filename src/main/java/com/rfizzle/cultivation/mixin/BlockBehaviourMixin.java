package com.rfizzle.cultivation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.rfizzle.cultivation.harvest.HarvestHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * The explosion leg of the harvest choke point: explosions never call
 * {@code Block#dropResources} — {@code onExplosionHit} builds its own loot
 * params and resolves {@code BlockState#getDrops} directly, so a crop blown up
 * by TNT or a creeper drains through the same handler as every other
 * destruction path. {@code BlockBehaviour} is the narrowest class that declares
 * the method; the handler's supported-mature-crop filter rejects everything
 * else immediately.
 */
@Mixin(BlockBehaviour.class)
abstract class BlockBehaviourMixin {
    @WrapOperation(
            method = "onExplosionHit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getDrops(Lnet/minecraft/world/level/storage/loot/LootParams$Builder;)Ljava/util/List;"
            )
    )
    private List<ItemStack> cultivation$explosionHarvestDrops(
            BlockState state, LootParams.Builder builder, Operation<List<ItemStack>> original,
            @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos,
            @Local(argsOnly = true) Explosion explosion) {
        List<ItemStack> drops = original.call(state, builder);
        if (level instanceof ServerLevel serverLevel) {
            drops = HarvestHandler.onDropsResolved(state, serverLevel, pos, explosion.getDirectSourceEntity(), drops);
        }
        return drops;
    }
}
