package com.rfizzle.cultivation.compat.jei;

import com.rfizzle.cultivation.Cultivation;
import com.rfizzle.cultivation.item.CultivationItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI plugin (the {@code jei_mod_plugin} entrypoint; {@code @JeiPlugin} is kept for
 * cross-loader parity). The scythe recipes ride JEI's own vanilla plugin; this adds
 * only the Fertilizer info entry naming the composter as its source. JEI stays
 * modCompileOnly.
 */
@JeiPlugin
public final class CultivationJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = Cultivation.id("jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addIngredientInfo(CultivationItems.FERTILIZER,
                Component.translatable("info.cultivation.fertilizer"));
    }
}
