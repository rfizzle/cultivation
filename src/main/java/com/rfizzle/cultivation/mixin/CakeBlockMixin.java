package com.rfizzle.cultivation.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.rfizzle.cultivation.attachment.DietData;
import com.rfizzle.cultivation.diet.DietHandler;
import com.rfizzle.cultivation.meal.MealBuffs;
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
            // Each slice grants the full meal-buff trio (SPEC §4), keyed to cake like the fatigue stack.
            MealBuffs.grant(serverPlayer, Items.CAKE);
            int nutrition = args.get(0);
            float saturationModifier = args.get(1);
            int scaledNutrition = DietData.scaledNutrition(nutrition, effectiveness);
            args.set(0, scaledNutrition);
            // FoodData.eat(int, float) treats the float as a saturation *modifier*: restored
            // saturation is nutrition * modifier * 2, so scaling both nutrition and the modifier
            // would apply fatigue twice. Rebase the modifier against the scaled nutrition so
            // restored saturation scales by effectiveness exactly once, matching the food path.
            args.set(1, scaledNutrition > 0
                    ? (float) ((double) nutrition * saturationModifier * effectiveness / scaledNutrition)
                    : 0.0F);
        }
    }
}
