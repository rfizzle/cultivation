package com.rfizzle.cultivation.compat.emi;

import com.rfizzle.cultivation.Cultivation;
import com.rfizzle.cultivation.item.CultivationItems;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * EMI plugin (the {@code emi} entrypoint). The scythe recipes are vanilla-typed,
 * so EMI's own default plugin already browses them; the only custom entry is an
 * info panel naming the composter as Fertilizer's source. EMI stays modCompileOnly.
 */
public final class CultivationEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.addRecipe(new EmiInfoRecipe(
                List.<EmiIngredient>of(EmiStack.of(CultivationItems.FERTILIZER)),
                List.of(Component.translatable("info.cultivation.fertilizer")),
                Cultivation.id("info/fertilizer")));
    }
}
