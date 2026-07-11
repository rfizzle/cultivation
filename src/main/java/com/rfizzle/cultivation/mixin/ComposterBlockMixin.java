package com.rfizzle.cultivation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rfizzle.cultivation.item.CultivationItems;
import com.rfizzle.cultivation.soil.Fertilizer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.ComposterBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The composter Fertilizer seam ({@code design/SPEC.md} §6): a level-8 composter
 * emptied by a player ({@code extractProduce}) or drained by a hopper
 * ({@code getContainer}'s output slot) yields Fertilizer in place of bone meal
 * while {@link Fertilizer#composterProducesFertilizer()} — read live per call.
 * The hopper's extraction gate is opened separately in
 * {@link ComposterOutputContainerMixin}. Nothing else about the composter, or
 * the other sources of bone meal, changes.
 */
@Mixin(ComposterBlock.class)
abstract class ComposterBlockMixin {
    @WrapOperation(
            method = "extractProduce",
            at = @At(value = "NEW", target = "(Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/item/ItemStack;")
    )
    private static ItemStack cultivation$playerProduce(ItemLike boneMeal, Operation<ItemStack> original) {
        return Fertilizer.composterProducesFertilizer()
                ? new ItemStack(CultivationItems.FERTILIZER)
                : original.call(boneMeal);
    }

    @WrapOperation(
            method = "getContainer",
            at = @At(value = "NEW", target = "(Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/item/ItemStack;")
    )
    private ItemStack cultivation$hopperProduce(ItemLike boneMeal, Operation<ItemStack> original) {
        return Fertilizer.composterProducesFertilizer()
                ? new ItemStack(CultivationItems.FERTILIZER)
                : original.call(boneMeal);
    }
}
