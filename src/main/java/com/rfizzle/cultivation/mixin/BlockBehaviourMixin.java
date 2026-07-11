package com.rfizzle.cultivation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.rfizzle.cultivation.harvest.HarvestHandler;
import com.rfizzle.cultivation.soil.FarmlandReversion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Two seams on the narrowest class that declares each method:
 *
 * <ul>
 * <li><b>Explosion leg of the harvest choke point</b> — explosions never call
 * {@code Block#dropResources}; {@code onExplosionHit} builds its own loot
 * params and resolves {@code BlockState#getDrops} directly, so a crop blown up
 * by TNT or a creeper drains through the same handler as every other
 * destruction path. The handler's supported-mature-crop filter rejects
 * everything else immediately.</li>
 * <li><b>Farmland reversion</b> — {@code onRemove} fires on every server-side
 * block state change, so a farmland→anything transition (trample, dry-out,
 * break, explosion, piston, {@code /setblock}) is one seam. {@code FarmBlock}
 * never overrides {@code onRemove}, and the two block-reference checks reject
 * everything else — including farmland moisture changes — before any work.</li>
 * </ul>
 */
@Mixin(BlockBehaviour.class)
abstract class BlockBehaviourMixin {
    @Inject(method = "onRemove", at = @At("HEAD"))
    private void cultivation$farmlandReversion(
            BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston,
            CallbackInfo ci) {
        if (!state.is(Blocks.FARMLAND) || newState.is(Blocks.FARMLAND)) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            FarmlandReversion.onFarmlandRemoved(serverLevel, pos);
        }
    }

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
