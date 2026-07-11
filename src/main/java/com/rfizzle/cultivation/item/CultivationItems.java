package com.rfizzle.cultivation.item;

import com.rfizzle.cultivation.Cultivation;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The mod's registered items ({@code mc-registration}). Fertilizer is the only
 * one — {@code design/SPEC.md} §6 — sourced from the composter, so it lands in
 * the vanilla Ingredients tab beside bone meal.
 */
public final class CultivationItems {
    public static final Item FERTILIZER = new FertilizerItem(new Item.Properties());

    private CultivationItems() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, Cultivation.id("fertilizer"), FERTILIZER);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS)
                .register(entries -> entries.addAfter(Items.BONE_MEAL, FERTILIZER));
    }
}
