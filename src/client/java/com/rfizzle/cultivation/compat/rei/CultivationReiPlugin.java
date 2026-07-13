package com.rfizzle.cultivation.compat.rei;

import com.rfizzle.cultivation.item.CultivationItems;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.plugin.common.displays.DefaultInformationDisplay;
import net.minecraft.network.chat.Component;

/**
 * REI plugin (the {@code rei_client} entrypoint). The scythe recipes ride REI's own
 * default plugin (which fills displays from the vanilla recipe manager); this adds
 * only the Fertilizer info entry naming the composter as its source. REI stays
 * modCompileOnly.
 */
public final class CultivationReiPlugin implements REIClientPlugin {
    @Override
    public void registerDisplays(DisplayRegistry registry) {
        registry.add(DefaultInformationDisplay
                .createFromEntry(EntryStacks.of(CultivationItems.FERTILIZER),
                        Component.translatable("item.cultivation.fertilizer"))
                .line(Component.translatable("info.cultivation.fertilizer")));
    }
}
