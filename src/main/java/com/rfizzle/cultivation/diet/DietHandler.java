package com.rfizzle.cultivation.diet;

import com.rfizzle.cultivation.api.CultivationFoodCallback;
import com.rfizzle.cultivation.attachment.DietData;
import com.rfizzle.cultivation.attachment.DietStore;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.advancement.CultivationCriteria;
import com.rfizzle.cultivation.network.DietNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;

/**
 * The single dietary-fatigue choke point ({@code design/SPEC.md} §3). Both
 * consumption seams — the generic {@code Player#eat} food path and the {@code
 * CakeBlock#eat} slice path — route here, so fatigue is computed, recorded,
 * synced, and announced in exactly one place.
 *
 * <p>Ordering per SPEC: effectiveness is read from the <i>current</i> stacks,
 * then the eat is recorded (increment, history append, variety reset), so the
 * multiplier this eat receives always reflects the state before this bite.
 */
public final class DietHandler {
    private DietHandler() {
    }

    /**
     * Records one eat of {@code item} for {@code player} and returns the fatigue
     * multiplier it should receive, in {@code [fatigueFloor, 1.0]}. Returns
     * {@code 1.0} and records nothing when dietary fatigue is disabled — the
     * stored stacks are retained but inert.
     */
    public static double consume(ServerPlayer player, Item item) {
        CultivationConfig config = CultivationConfig.get();
        if (!config.enableDietaryFatigue) {
            return 1.0;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        DietData before = DietStore.get(player);
        double effectiveness = before.effectiveness(id, config.fatiguePerRepeat, config.fatigueFloor);
        DietData after = before.afterEat(id, config.fatiguePerRepeat, config.fatigueFloor, config.fatigueResetDistinctFoods);
        DietStore.set(player, after);
        DietNetworking.sync(player);
        CultivationFoodCallback.EVENT.invoker().onFood(player, item, (float) effectiveness);
        // A variety reset clears a non-empty diet back to the pristine state; a
        // non-reset eat always leaves at least this bite in the history, so an
        // empty result after a non-empty start is exactly the reset edge (§10).
        if (after.isDefault() && !before.isDefault()) {
            CultivationCriteria.BALANCED_TABLE.trigger(player);
        }
        return effectiveness;
    }

    /**
     * A nutrition/saturation-scaled copy of {@code food} at the given
     * effectiveness, with the effects list — golden-apple absorption, stew rolls
     * — left byte-identical so food effects always apply at full strength.
     * Returns the original instance untouched at full effectiveness.
     */
    public static FoodProperties scale(FoodProperties food, double effectiveness) {
        if (effectiveness >= 1.0) {
            return food;
        }
        int nutrition = DietData.scaledNutrition(food.nutrition(), effectiveness);
        // FoodProperties#saturation is already the absolute restored saturation (built as
        // nutrition * modifier * 2), and Player#eat routes through FoodData#eat(FoodProperties)
        // -> add(nutrition, saturation), which adds it directly. So scaling it by effectiveness
        // once matches SPEC §3 exactly. (The cake seam is different: CakeBlock#eat calls the
        // FoodData#eat(int, float) overload with a raw modifier, so CakeBlockMixin has to rebase.)
        float saturation = (float) (food.saturation() * effectiveness);
        return new FoodProperties(
                nutrition, saturation, food.canAlwaysEat(), food.eatSeconds(), food.usingConvertsTo(), food.effects());
    }
}
