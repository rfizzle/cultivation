package com.rfizzle.cultivation.client;

import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.network.ConfigSyncS2CPayload;
import com.rfizzle.cultivation.network.DietSyncS2CPayload;
import com.rfizzle.cultivation.network.SoilBandDeltaS2CPayload;
import com.rfizzle.cultivation.network.SoilBandsS2CPayload;
import com.rfizzle.cultivation.network.SoilOverlayRequestC2SPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class CultivationClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncS2CPayload.TYPE,
                (payload, context) -> context.client().execute(() -> ClientCultivationConfig.accept(payload.config())));
        ClientPlayNetworking.registerGlobalReceiver(DietSyncS2CPayload.TYPE,
                (payload, context) -> context.client().execute(() -> ClientDietData.accept(payload)));
        ClientPlayNetworking.registerGlobalReceiver(SoilBandsS2CPayload.TYPE,
                (payload, context) -> context.client().execute(() -> ClientSoilOverlayData.acceptChunk(payload)));
        ClientPlayNetworking.registerGlobalReceiver(SoilBandDeltaS2CPayload.TYPE,
                (payload, context) -> context.client().execute(() -> ClientSoilOverlayData.acceptDelta(
                        payload.chunkPos(), payload.packedPos(), payload.present(), payload.flags())));

        // Pull a chunk's overlay set as it loads; honor the client's display toggle
        // so an opted-out player never asks. Prune the cache as chunks unload.
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            if (CultivationConfig.get().showSoilOverlays) {
                ClientPlayNetworking.send(
                        new SoilOverlayRequestC2SPayload(chunk.getPos().x, chunk.getPos().z));
            }
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) ->
                ClientSoilOverlayData.removeChunk(chunk.getPos().toLong()));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientCultivationConfig.clear();
            ClientDietData.clear();
            ClientSoilOverlayData.clear();
        });
        ItemTooltipCallback.EVENT.register(DietTooltip::append);

        SoilOverlayRenderer.register();
    }
}
