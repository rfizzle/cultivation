package com.rfizzle.cultivation.meal;

import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.effect.CultivationEffects;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The meal-buff grant choke point ({@code design/SPEC.md} §4). The five buffed
 * foods each replace the whole meal-buff trio: consuming any of them first
 * removes all three Cultivation effects, then applies the food's grant. The
 * grant is gated only by {@code enableMealBuffs} — never by the dietary-fatigue
 * toggle, whose seam ({@code DietHandler#consume}) early-returns when disabled.
 *
 * <p>The selection and scaling logic is a pure, {@code net.minecraft}-free core
 * ({@link #grants}, {@link #satedMultiplier}); {@link #grant} is the thin shell
 * that maps it onto real effect grants at the config's meal/cake duration.
 */
public final class MealBuffs {
    private MealBuffs() {
    }

    /** One of the three meal buffs, in the canonical order Suspicious Stew rolls against. */
    public enum Buff {
        NIMBLE,
        DILIGENT,
        SATED
    }

    /** A single effect grant: which buff, and its amplifier (0 = level I). */
    public record Grant(Buff buff, int amplifier) {
    }

    private static final ResourceLocation RABBIT_STEW = ResourceLocation.withDefaultNamespace("rabbit_stew");
    private static final ResourceLocation BEETROOT_SOUP = ResourceLocation.withDefaultNamespace("beetroot_soup");
    private static final ResourceLocation MUSHROOM_STEW = ResourceLocation.withDefaultNamespace("mushroom_stew");
    private static final ResourceLocation SUSPICIOUS_STEW = ResourceLocation.withDefaultNamespace("suspicious_stew");
    private static final ResourceLocation CAKE = ResourceLocation.withDefaultNamespace("cake");

    /**
     * The meal buffs {@code itemId} grants, or an empty list if it is not one of
     * the five buffed foods. Suspicious Stew picks one buff at level II keyed by
     * {@code suspiciousRoll} (any int; reduced mod 3), so this stays a pure
     * function of its inputs — the caller supplies the roll from the world's RNG.
     */
    public static List<Grant> grants(ResourceLocation itemId, int suspiciousRoll) {
        if (RABBIT_STEW.equals(itemId)) {
            return List.of(new Grant(Buff.NIMBLE, 0));
        }
        if (BEETROOT_SOUP.equals(itemId)) {
            return List.of(new Grant(Buff.DILIGENT, 0));
        }
        if (MUSHROOM_STEW.equals(itemId)) {
            return List.of(new Grant(Buff.SATED, 0));
        }
        if (CAKE.equals(itemId)) {
            return List.of(new Grant(Buff.NIMBLE, 0), new Grant(Buff.DILIGENT, 0), new Grant(Buff.SATED, 0));
        }
        if (SUSPICIOUS_STEW.equals(itemId)) {
            Buff picked = Buff.values()[Math.floorMod(suspiciousRoll, Buff.values().length)];
            return List.of(new Grant(picked, 1));
        }
        return List.of();
    }

    /**
     * The exhaustion multiplier for a Sated effect at {@code amplifier}: each
     * level cuts hunger drain 10%, floored at 0 so a hand-tuned high amplifier
     * can never turn drain negative.
     */
    public static double satedMultiplier(int amplifier) {
        return Math.max(0.0, 1.0 - 0.10 * (amplifier + 1));
    }

    /**
     * Grants {@code item}'s meal buffs to {@code player}, replacing the whole
     * trio. Does nothing when meal buffs are disabled or the item is not buffed.
     */
    public static void grant(ServerPlayer player, Item item) {
        CultivationConfig config = CultivationConfig.get();
        if (!config.enableMealBuffs) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        List<Grant> grants = grants(id, player.getRandom().nextInt(Buff.values().length));
        if (grants.isEmpty()) {
            return;
        }
        // One meal at a time: clear all three before applying, so buffs replace rather than stack.
        player.removeEffect(CultivationEffects.NIMBLE);
        player.removeEffect(CultivationEffects.DILIGENT);
        player.removeEffect(CultivationEffects.SATED);
        int duration = item == Items.CAKE ? config.cakeBuffDurationTicks : config.mealBuffDurationTicks;
        for (Grant g : grants) {
            player.addEffect(new MobEffectInstance(holderFor(g.buff()), duration, g.amplifier()));
        }
    }

    private static Holder<MobEffect> holderFor(Buff buff) {
        return switch (buff) {
            case NIMBLE -> CultivationEffects.NIMBLE;
            case DILIGENT -> CultivationEffects.DILIGENT;
            case SATED -> CultivationEffects.SATED;
        };
    }
}
