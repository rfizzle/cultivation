package com.rfizzle.cultivation.client;

import com.rfizzle.cultivation.network.DietSyncS2CPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class CultivationClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(DietSyncS2CPayload.TYPE,
                (payload, context) -> context.client().execute(() -> ClientDietData.accept(payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientDietData.clear());
        ItemTooltipCallback.EVENT.register(DietTooltip::append);
    }
}
