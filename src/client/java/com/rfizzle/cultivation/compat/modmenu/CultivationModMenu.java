package com.rfizzle.cultivation.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * ModMenu entry point (the {@code modmenu} entrypoint) — hands ModMenu the Cloth
 * Config screen when both mods are present. Only ModMenu itself loads this class,
 * and the Cloth-built screen lives in {@link CultivationConfigScreen} behind an
 * {@code isModLoaded} guard, so ModMenu-without-Cloth degrades to "no config screen"
 * instead of a {@code NoClassDefFoundError} (mc-config).
 */
public final class CultivationModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return parent -> null;
        }
        return CultivationConfigScreen::create;
    }
}
