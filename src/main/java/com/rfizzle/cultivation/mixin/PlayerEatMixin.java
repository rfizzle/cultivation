package com.rfizzle.cultivation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.rfizzle.cultivation.diet.DietHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The generic dietary-fatigue seam (SPEC §3): every item with a food component —
 * carrots, steak, honey bottles, golden apples, suspicious stew — flows through
 * {@code Player#eat}, which applies nutrition/saturation via {@code
 * FoodData#eat(FoodProperties)} before {@code super.eat} rolls the food's
 * effects. Wrapping only the {@code FoodData#eat} call substitutes a scaled
 * copy for the hunger restore while the untouched original still feeds effect
 * application, so effects always apply at full strength.
 *
 * <p>Server-authoritative: the client runs {@code Player#eat} for eat prediction
 * with a non-{@link ServerPlayer}, which passes the food through unchanged.
 */
@Mixin(Player.class)
abstract class PlayerEatMixin {
    @WrapOperation(
            method = "eat",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V"
            )
    )
    private void cultivation$applyFatigue(FoodData foodData, FoodProperties food, Operation<Void> original,
                                          @Local(argsOnly = true) ItemStack stack) {
        if ((Object) this instanceof ServerPlayer player) {
            double effectiveness = DietHandler.consume(player, stack.getItem());
            original.call(foodData, DietHandler.scale(food, effectiveness));
        } else {
            original.call(foodData, food);
        }
    }
}
