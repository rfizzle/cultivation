package com.rfizzle.cultivation.item;

import com.rfizzle.cultivation.Cultivation;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tiers;

/**
 * The mod's registered items ({@code mc-registration}). Fertilizer
 * ({@code design/SPEC.md} §6) is sourced from the composter, so it lands in the
 * vanilla Ingredients tab beside bone meal; the three scythes (§7) are farming
 * tools, so they join the Tools tab beside the hoes.
 *
 * <p>The scythe attack stats reuse {@link DiggerItem#createAttributes}: a
 * {@code +1.0} damage bonus over each tier's base lands the §7 totals (4/5/6),
 * and the {@code -2.4} speed modifier yields the shared 1.6 attack speed.
 */
public final class CultivationItems {
    public static final Item FERTILIZER = new FertilizerItem(new Item.Properties());
    public static final Item IRON_SCYTHE = scythe(Tiers.IRON);
    public static final Item DIAMOND_SCYTHE = scythe(Tiers.DIAMOND);
    public static final Item NETHERITE_SCYTHE = new ScytheItem(Tiers.NETHERITE,
            new Item.Properties().fireResistant().attributes(DiggerItem.createAttributes(Tiers.NETHERITE, 1.0F, -2.4F)));

    private CultivationItems() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, Cultivation.id("fertilizer"), FERTILIZER);
        Registry.register(BuiltInRegistries.ITEM, Cultivation.id("iron_scythe"), IRON_SCYTHE);
        Registry.register(BuiltInRegistries.ITEM, Cultivation.id("diamond_scythe"), DIAMOND_SCYTHE);
        Registry.register(BuiltInRegistries.ITEM, Cultivation.id("netherite_scythe"), NETHERITE_SCYTHE);

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS)
                .register(entries -> entries.addAfter(Items.BONE_MEAL, FERTILIZER));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> entries.addAfter(Items.NETHERITE_HOE, IRON_SCYTHE, DIAMOND_SCYTHE, NETHERITE_SCYTHE));
    }

    private static ScytheItem scythe(Tiers tier) {
        return new ScytheItem(tier, new Item.Properties().attributes(DiggerItem.createAttributes(tier, 1.0F, -2.4F)));
    }
}
