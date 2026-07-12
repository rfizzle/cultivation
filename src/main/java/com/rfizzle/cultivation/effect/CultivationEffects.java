package com.rfizzle.cultivation.effect;

import com.rfizzle.cultivation.Cultivation;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The three meal-buff status effects ({@code design/SPEC.md} §4), all beneficial.
 * Nimble and Diligent are attribute-backed — vanilla's {@code AttributeTemplate}
 * scales the stored amount by {@code amplifier + 1}, so a single registered
 * amount yields +5%/+10% at level I and double at level II with no per-grant
 * arithmetic. Sated carries no attribute: its −10%-per-level hunger drain lives
 * in {@code PlayerExhaustionMixin}, which reads this effect's amplifier.
 */
public final class CultivationEffects {
    /** +5% movement speed per level. */
    public static Holder<MobEffect> NIMBLE;
    /** +10% block-breaking speed per level. */
    public static Holder<MobEffect> DILIGENT;
    /** −10% hunger drain per level (applied by {@code PlayerExhaustionMixin}). */
    public static Holder<MobEffect> SATED;

    private CultivationEffects() {
    }

    public static void register() {
        NIMBLE = register("nimble", new MobEffect(MobEffectCategory.BENEFICIAL, 0x7BE0A4) {}
                .addAttributeModifier(Attributes.MOVEMENT_SPEED, Cultivation.id("nimble"),
                        0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        DILIGENT = register("diligent", new MobEffect(MobEffectCategory.BENEFICIAL, 0xE0A030) {}
                .addAttributeModifier(Attributes.BLOCK_BREAK_SPEED, Cultivation.id("diligent"),
                        0.10, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        SATED = register("sated", new MobEffect(MobEffectCategory.BENEFICIAL, 0xC98A4B) {});
    }

    private static Holder<MobEffect> register(String name, MobEffect effect) {
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Cultivation.id(name), effect);
    }
}
