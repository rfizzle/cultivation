package com.rfizzle.cultivation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rfizzle.cultivation.effect.CultivationEffects;
import com.rfizzle.cultivation.meal.MealBuffs;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The Sated meal buff (SPEC §4): −10% hunger drain per level. Vanilla routes all
 * exhaustion through {@code Player#causeFoodExhaustion}, which — after its own
 * client-side and invulnerability guards — hands the amount to {@code
 * FoodData#addExhaustion}. Wrapping that call scales the amount by Sated's
 * multiplier; {@code FoodData} itself holds no player reference, so the effect
 * check has to live on {@code Player}, not on {@code FoodData}.
 */
@Mixin(Player.class)
abstract class PlayerExhaustionMixin {
    @WrapOperation(
            method = "causeFoodExhaustion",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/food/FoodData;addExhaustion(F)V"
            )
    )
    private void cultivation$scaleSatedExhaustion(FoodData foodData, float exhaustion, Operation<Void> original) {
        MobEffectInstance sated = ((Player) (Object) this).getEffect(CultivationEffects.SATED);
        float scaled = sated != null
                ? (float) (exhaustion * MealBuffs.satedMultiplier(sated.getAmplifier()))
                : exhaustion;
        original.call(foodData, scaled);
    }
}
