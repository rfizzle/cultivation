package com.rfizzle.cultivation.data;

import com.rfizzle.cultivation.item.CultivationItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.model.ModelTemplates;

/**
 * Cultivation's five item models.
 *
 * <p>The split is by how the item is held, which is what the two templates encode:
 * Fertilizer is an ingredient and takes {@code item/generated}; the three scythes and
 * the rake are tools and take {@code item/handheld}, so they angle in the hand rather
 * than sitting flat like a sprite.
 */
public class CultivationModelProvider extends FabricModelProvider {

    public CultivationModelProvider(FabricDataOutput output) {
        super(output);
    }

    /**
     * Cultivation registers no blocks — soil condition is a client-side overlay drawn
     * over vanilla farmland, not a block of its own — so there is nothing to emit. The
     * override is required by {@link FabricModelProvider}; an empty body produces no
     * file rather than an empty one.
     */
    @Override
    public void generateBlockStateModels(BlockModelGenerators generators) {
    }

    @Override
    public void generateItemModels(ItemModelGenerators generators) {
        generators.generateFlatItem(CultivationItems.FERTILIZER, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(CultivationItems.IRON_SCYTHE, ModelTemplates.FLAT_HANDHELD_ITEM);
        generators.generateFlatItem(CultivationItems.DIAMOND_SCYTHE, ModelTemplates.FLAT_HANDHELD_ITEM);
        generators.generateFlatItem(CultivationItems.NETHERITE_SCYTHE, ModelTemplates.FLAT_HANDHELD_ITEM);
        generators.generateFlatItem(CultivationItems.IRON_RAKE, ModelTemplates.FLAT_HANDHELD_ITEM);
    }
}
