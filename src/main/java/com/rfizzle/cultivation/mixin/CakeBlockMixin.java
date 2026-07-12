package com.rfizzle.cultivation.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.rfizzle.cultivation.attachment.DietData;
import com.rfizzle.cultivation.diet.DietHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CakeBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * The cake-slice dietary-fatigue seam (SPEC §3). Cake is a {@link Items#CAKE}
 * block with no food component, so it never reaches {@code Player#eat}; each
 * slice feeds the player directly through {@code CakeBlock#eat}'s raw {@code
 * FoodData#eat(int, float)} call. Scaling that call and keying the fatigue stack
 * to {@code minecraft:cake} makes every slice — plain cake and every candle-cake
 * variant, which delegate to the same method — count as one item.
 *
 * <p>Targeting {@code CakeBlock#eat} rather than the shared {@code
 * FoodData#eat(int, float)} overload keeps the Saturation status effect, which
 * also calls that overload, out of the diet system.
 */
@Mixin(CakeBlock.class)
abstract class CakeBlockMixin {
    @ModifyArgs(
            method = "eat",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/food/FoodData;eat(IF)V"
            )
    )
    private static void cultivation$applyCakeFatigue(Args args, @Local(argsOnly = true) Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            double effectiveness = DietHandler.consume(serverPlayer, Items.CAKE);
            int nutrition = args.get(0);
            float saturation = args.get(1);
            args.set(0, DietData.scaledNutrition(nutrition, effectiveness));
            args.set(1, (float) (saturation * effectiveness));
        }
    }
}
