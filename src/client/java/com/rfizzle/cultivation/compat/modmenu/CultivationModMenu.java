package com.rfizzle.cultivation.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenu entry point (the {@code modmenu} entrypoint) — hands ModMenu the Cloth
 * Config screen when both mods are present. Absent either, this class is never
 * loaded, so the integration can't gate the mod (mc-config).
 */
public final class CultivationModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return CultivationConfigScreen::create;
    }
}
