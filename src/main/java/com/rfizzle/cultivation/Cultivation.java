package com.rfizzle.cultivation;

import com.rfizzle.cultivation.attachment.CultivationAttachments;
import com.rfizzle.cultivation.command.CultivationCommand;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.criteria.CultivationCriteria;
import com.rfizzle.cultivation.effect.CultivationEffects;
import com.rfizzle.cultivation.event.BroadcastSowingHandler;
import com.rfizzle.cultivation.event.RightClickHarvestHandler;
import com.rfizzle.cultivation.event.ScytheHarvestHandler;
import com.rfizzle.cultivation.event.SoilInteractionHandler;
import com.rfizzle.cultivation.item.CultivationItems;
import com.rfizzle.cultivation.meal.BowlFoodStacking;
import com.rfizzle.cultivation.network.ConfigNetworking;
import com.rfizzle.cultivation.network.DietNetworking;
import com.rfizzle.cultivation.network.SoilOverlayNetworking;
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
        // Materializes config/cultivation.json with defaults on first launch, and warms
        // the instance both sides read: SyncedConfig#effective falls back here on the
        // item-use and tooltip paths, which must not take a disk load.
        CultivationConfig.get();
        CultivationItems.register();
        // Bowl foods stack to a real shelf (SPEC §4). Part of the meal-buff feature, so it
        // rides enableMealBuffs; stack size is baked into the item at startup, read once here.
        BowlFoodStacking.applyIfEnabled(CultivationConfig.get().enableMealBuffs);
        CultivationEffects.register();
        CultivationCriteria.register();
        CultivationAttachments.init();
        SoilClockState.register();
        SoilInteractionHandler.register();
        ScytheHarvestHandler.register();
        RightClickHarvestHandler.register();
        BroadcastSowingHandler.register();
        ConfigNetworking.register();
        DietNetworking.register();
        SoilOverlayNetworking.register();
        CultivationCommand.register();
        LOGGER.info("Cultivation initialized");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
