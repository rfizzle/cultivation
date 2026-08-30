package com.rfizzle.cultivation.advancement;

import com.rfizzle.cultivation.Cultivation;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * The mod's advancement criterion triggers ({@code design/SPEC.md} §10,
 * {@code mc-advancements}). Each of the five Husbandry-tab advancements is a
 * plain "the player did the thing" milestone, so each is a vanilla
 * {@link PlayerTrigger} registered under its own {@code cultivation:} id — no
 * predicate to write, because the condition (a diet reset fired, a dose landed,
 * nine crops fell to one sweep, nine blocks were sown in one pass, a crop came
 * off enriched-and-dosed soil) is decided at the fire site and the trigger
 * simply grants when fired.
 *
 * <p>The triggers are registered in {@code onInitialize} <em>before</em> any
 * advancement JSON is deserialized, so advancement loading resolves each
 * criterion's {@code "trigger"} id against {@link BuiltInRegistries#TRIGGER_TYPES}.
 * {@link #register()} is idempotent because a second {@code Registry.register}
 * of the same id throws.
 */
public final class CultivationCriteria {
    /** SPEC §3: a dietary-fatigue reset fired from eating distinct foods. */
    public static final PlayerTrigger BALANCED_TABLE = new PlayerTrigger();
    /** SPEC §6: a player applied a Fertilizer dose to farmland. */
    public static final PlayerTrigger LONG_TERM_INVESTMENT = new PlayerTrigger();
    /** SPEC §7: a single scythe sweep reaped all nine mature crops. */
    public static final PlayerTrigger REAP_WHAT_YOU_SOW = new PlayerTrigger();
    /** SPEC §7: a single rake pass sowed all nine blocks of a 3×3. */
    public static final PlayerTrigger FULL_BROADCAST = new PlayerTrigger();
    /** SPEC §1: a crop harvested from soil that is both enriched and dosed. */
    public static final PlayerTrigger OLD_GROWTH = new PlayerTrigger();

    private static boolean registered = false;

    private CultivationCriteria() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Cultivation.id("balanced_table"), BALANCED_TABLE);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Cultivation.id("long_term_investment"), LONG_TERM_INVESTMENT);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Cultivation.id("reap_what_you_sow"), REAP_WHAT_YOU_SOW);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Cultivation.id("full_broadcast"), FULL_BROADCAST);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Cultivation.id("old_growth"), OLD_GROWTH);
    }
}
