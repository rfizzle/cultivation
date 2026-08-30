package com.rfizzle.cultivation.data;

import com.rfizzle.cultivation.Cultivation;
import com.rfizzle.cultivation.item.CultivationItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.concurrent.CompletableFuture;

/**
 * Cultivation's item tags: the two the mod defines and reads, and the two vanilla ones it
 * has to join so its tools can be enchanted.
 *
 * <p>{@code cultivation:scythes} and {@code cultivation:rakes} are the mod's own — the
 * harvest seam and the broadcast-sowing handler match on them rather than on item
 * identity, so a pack can add a third-party scythe without a code change.
 *
 * <p>The two {@code minecraft:enchantable/*} entries are the reason the tools take
 * enchantments at all: 1.21 moved enchantability from an item property to tag membership,
 * so an item outside these tags silently cannot be enchanted. The rake is deliberately in
 * {@code durability} but not {@code mining} — it breaks nothing, so Efficiency and
 * Fortune have nothing to act on.
 */
public class CultivationItemTagProvider extends FabricTagProvider.ItemTagProvider {

    public static final TagKey<Item> SCYTHES =
            TagKey.create(Registries.ITEM, Cultivation.id("scythes"));
    public static final TagKey<Item> RAKES =
            TagKey.create(Registries.ITEM, Cultivation.id("rakes"));

    public CultivationItemTagProvider(FabricDataOutput output,
                                      CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(output, registryLookup);
    }

    @Override
    protected void addTags(HolderLookup.Provider registryLookup) {
        getOrCreateTagBuilder(SCYTHES)
                .add(key(CultivationItems.IRON_SCYTHE))
                .add(key(CultivationItems.DIAMOND_SCYTHE))
                .add(key(CultivationItems.NETHERITE_SCYTHE));

        getOrCreateTagBuilder(RAKES)
                .add(key(CultivationItems.IRON_RAKE));

        getOrCreateTagBuilder(ItemTags.DURABILITY_ENCHANTABLE)
                .add(key(CultivationItems.IRON_SCYTHE))
                .add(key(CultivationItems.DIAMOND_SCYTHE))
                .add(key(CultivationItems.NETHERITE_SCYTHE))
                .add(key(CultivationItems.IRON_RAKE));

        getOrCreateTagBuilder(ItemTags.MINING_ENCHANTABLE)
                .add(key(CultivationItems.IRON_SCYTHE))
                .add(key(CultivationItems.DIAMOND_SCYTHE))
                .add(key(CultivationItems.NETHERITE_SCYTHE));
    }

    /**
     * The registry key for an item instance. The tag builder's {@code add(Item)} overload
     * resolves through {@code BuiltInRegistries.ITEM.getKey}, which is the same lookup —
     * spelling it out keeps the failure mode obvious if an item is ever tagged before it
     * is registered.
     */
    private static ResourceKey<Item> key(Item item) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getResourceKey(item)
                .orElseThrow(() -> new IllegalStateException(
                        "item is not registered, so it cannot be tagged: " + item));
    }
}
