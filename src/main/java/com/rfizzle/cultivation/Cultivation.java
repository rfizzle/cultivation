package com.rfizzle.cultivation;

import com.rfizzle.cultivation.attachment.CultivationAttachments;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.event.SoilInteractionHandler;
import com.rfizzle.cultivation.item.CultivationItems;
import com.rfizzle.cultivation.soil.SoilClockState;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cultivation implements ModInitializer {
    public static final String MOD_ID = "cultivation";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Materializes config/cultivation.json with defaults on first launch.
        CultivationConfig.get();
        CultivationItems.register();
        CultivationAttachments.init();
        SoilClockState.register();
        SoilInteractionHandler.register();
        LOGGER.info("Cultivation initialized");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
