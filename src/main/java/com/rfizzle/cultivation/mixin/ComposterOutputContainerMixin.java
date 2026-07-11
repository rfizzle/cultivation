package com.rfizzle.cultivation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rfizzle.cultivation.item.CultivationItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Opens the composter's hopper-extraction gate for Fertilizer. The vanilla
 * {@code ComposterBlock$OutputContainer#canTakeItemThroughFace} hardcodes an
 * {@code is(BONE_MEAL)} check; once {@link ComposterBlockMixin} places
 * Fertilizer in the output slot, a hopper could never pull it back out and the
 * composter would jam. Accepting Fertilizer here is unconditional — whatever the
 * slot legitimately holds may be extracted — so a live config flip mid-extract
 * never strands a stack.
 */
@Mixin(targets = "net.minecraft.world.level.block.ComposterBlock$OutputContainer")
abstract class ComposterOutputContainerMixin {
    @WrapOperation(
            method = "canTakeItemThroughFace",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z")
    )
    private boolean cultivation$acceptFertilizer(ItemStack stack, Item boneMeal, Operation<Boolean> original) {
        return original.call(stack, boneMeal) || stack.is(CultivationItems.FERTILIZER);
    }
}
