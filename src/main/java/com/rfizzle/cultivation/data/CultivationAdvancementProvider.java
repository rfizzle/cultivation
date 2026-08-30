package com.rfizzle.cultivation.data;

import com.rfizzle.cultivation.Cultivation;
import com.rfizzle.cultivation.advancement.CultivationCriteria;
import com.rfizzle.cultivation.item.CultivationItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Cultivation's five advancements ({@code design/SPEC.md} §10). Every one hangs off
 * vanilla's Husbandry root rather than opening a tab of its own — farming already has a
 * home in the vanilla tree and this mod is an overhaul of it, not a parallel progression.
 *
 * <p>All five are plain "the player did the thing" milestones on Cultivation's own
 * {@link PlayerTrigger}s, so each is one criterion with no predicate: the condition is
 * decided at the fire site and the trigger simply grants when fired. The trigger ids come
 * from {@link CultivationCriteria}, whose {@code register()} is idempotent precisely so it
 * can be reached from the datagen server bootstrap as well as from {@code onInitialize}.
 */
public class CultivationAdvancementProvider extends FabricAdvancementProvider {

    /** Every Cultivation advancement hangs off vanilla's Husbandry root. */
    private static final ResourceLocation PARENT =
            ResourceLocation.withDefaultNamespace("husbandry/root");

    public CultivationAdvancementProvider(FabricDataOutput output,
                                          CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(output, registryLookup);
    }

    @Override
    public void generateAdvancement(HolderLookup.Provider registryLookup,
                                    Consumer<AdvancementHolder> consumer) {
        CultivationCriteria.register();

        one(consumer, "balanced_table", new ItemStack(Items.BREAD),
                AdvancementType.TASK,
                "reset", playerDid(CultivationCriteria.BALANCED_TABLE));

        one(consumer, "long_term_investment", new ItemStack(CultivationItems.FERTILIZER),
                AdvancementType.TASK,
                "dosed", playerDid(CultivationCriteria.LONG_TERM_INVESTMENT));

        one(consumer, "reap_what_you_sow", new ItemStack(CultivationItems.IRON_SCYTHE),
                AdvancementType.GOAL,
                "swept", playerDid(CultivationCriteria.REAP_WHAT_YOU_SOW));

        one(consumer, "full_broadcast", new ItemStack(CultivationItems.IRON_RAKE),
                AdvancementType.GOAL,
                "sown", playerDid(CultivationCriteria.FULL_BROADCAST));

        one(consumer, "old_growth", new ItemStack(Items.WHEAT),
                AdvancementType.CHALLENGE,
                "reaped", playerDid(CultivationCriteria.OLD_GROWTH));
    }

    /** One single-criterion advancement. */
    private static void one(Consumer<AdvancementHolder> consumer,
                            String name,
                            ItemStack icon,
                            AdvancementType type,
                            String criterionName,
                            Criterion<?> criterion) {
        builder(name, icon, type)
                .addCriterion(criterionName, criterion)
                .save(consumer, Cultivation.id(name).toString());
    }

    /**
     * A parented, displayed builder with telemetry left off.
     *
     * <p>{@link Advancement.Builder#advancement()} turns {@code sendsTelemetryEvent}
     * <em>on</em>; all five of these have always shipped it off. The flag is inert for
     * modded content — its only reader, {@code WorldSessionTelemetryManager}, gates on the
     * advancement id being in the {@code minecraft} namespace — so this is not a
     * correctness fix. It is that a conversion whose job is to reproduce what ships should
     * not flip a shipped field on the way past, and the bare constructor is the same
     * builder with the flag left alone.
     *
     * <p>None of the five announces to chat, and the {@code background} argument is null on
     * purpose: a background texture belongs to the root of a tab, and these are children of
     * a vanilla root.
     */
    // parent(ResourceLocation) is @Deprecated(forRemoval) in favour of parent(AdvancementHolder),
    // but the holder form can only name an advancement this provider itself built. These five hang
    // off a vanilla advancement, so the id form is the only way to say it — vanilla's own providers
    // reach their parents as holders because they generate the whole tree.
    @SuppressWarnings("removal")
    private static Advancement.Builder builder(String name, ItemStack icon, AdvancementType type) {
        return new Advancement.Builder()
                .parent(PARENT)
                .display(icon, title(name), description(name), null, type, true, false, false);
    }

    /** A bare "this player did the thing" criterion on one of Cultivation's own triggers. */
    private static Criterion<PlayerTrigger.TriggerInstance> playerDid(PlayerTrigger trigger) {
        return trigger.createCriterion(new PlayerTrigger.TriggerInstance(Optional.empty()));
    }

    private static Component title(String name) {
        return Component.translatable("advancements.cultivation." + name + ".title");
    }

    private static Component description(String name) {
        return Component.translatable("advancements.cultivation." + name + ".description");
    }
}
