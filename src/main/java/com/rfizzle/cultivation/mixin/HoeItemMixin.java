package com.rfizzle.cultivation.mixin;

import com.rfizzle.cultivation.soil.EnrichedTilling;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The till seam ({@code design/SPEC.md} §5): fires right after vanilla's
 * conversion consumer runs, which only happens on the server once the tillable
 * predicate has passed — so a failed use (clicked from below, block above
 * occupied, wrong ground) can never record anything. The injection sits before
 * the tool's durability hit, so a hoe that breaks on this very till still
 * records its tier. Farmland itself is never tillable, so a post-conversion
 * farmland check is exactly "this use created farmland" and filters out the
 * coarse/rooted dirt conversions that share the consumer.
 */
@Mixin(HoeItem.class)
abstract class HoeItemMixin {
    @Inject(
            method = "useOn",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void cultivation$recordEnrichedTill(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).is(Blocks.FARMLAND)) {
            return;
        }
        EnrichedTilling.onFarmlandTilled(level, pos, context.getItemInHand());
    }
}
