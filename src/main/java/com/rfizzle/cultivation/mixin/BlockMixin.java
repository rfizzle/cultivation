package com.rfizzle.cultivation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rfizzle.cultivation.harvest.HarvestHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Routes every drop-resolving block destruction through the harvest choke
 * point. The three {@code dropResources} overloads cover survival player
 * breaks, pistons, water washing crops away, and {@code Level#destroyBlock};
 * explosions resolve drops elsewhere and are covered by
 * {@link BlockBehaviourMixin}. The handler filters to supported crops over
 * tracked soil (farmland, or a second-wave crop's ground) before doing any soil
 * work, so the wrap stays cheap for the overwhelmingly common non-crop break.
 */
@Mixin(Block.class)
abstract class BlockMixin {
    @WrapOperation(
            method = {
                    "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
                    "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/Block;getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)Ljava/util/List;"
            )
    )
    private static List<ItemStack> cultivation$harvestDrops(
            BlockState state, ServerLevel level, BlockPos pos, @Nullable BlockEntity blockEntity,
            Operation<List<ItemStack>> original) {
        return HarvestHandler.onDropsResolved(state, level, pos, null, original.call(state, level, pos, blockEntity));
    }

    @WrapOperation(
            method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/Block;getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;"
            )
    )
    private static List<ItemStack> cultivation$harvestDropsWithEntity(
            BlockState state, ServerLevel level, BlockPos pos, @Nullable BlockEntity blockEntity,
            @Nullable Entity entity, ItemStack tool, Operation<List<ItemStack>> original) {
        return HarvestHandler.onDropsResolved(
                state, level, pos, entity, original.call(state, level, pos, blockEntity, entity, tool));
    }
}
