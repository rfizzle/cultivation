package com.rfizzle.cultivation.network;

import com.rfizzle.cultivation.Cultivation;
import com.rfizzle.cultivation.config.CultivationConfig;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Ships the server's authoritative {@link CultivationConfig} to a joining client
 * so every client feature can honor the server's rules instead of the client's
 * own local file (SPEC §Configuration; the {@code mc-config} sync contract). The
 * whole config crosses as one JSON blob — a new config field reaches clients with
 * no codec change — and is re-clamped on decode so a malformed payload can never
 * seat an out-of-range rule.
 *
 * <p>The client stores this as the server-authoritative view; client-only
 * presentation keys ({@code showSoilOverlays}, {@code showFatigueTooltips}, the
 * overlay render distance) are read from the local file regardless, so a server
 * never dictates a purely visual client preference.
 */
public record ConfigSyncS2CPayload(CultivationConfig config) implements CustomPacketPayload {
    public static final Type<ConfigSyncS2CPayload> TYPE = new Type<>(Cultivation.id("config_sync"));

    // The pretty-printed config is a couple of KB; anything past this ceiling is not our config.
    private static final int MAX_JSON_BYTES = 65536;

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSyncS2CPayload> CODEC =
            StreamCodec.of(ConfigSyncS2CPayload::encode, ConfigSyncS2CPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, ConfigSyncS2CPayload payload) {
        buf.writeUtf(payload.config.toSyncJson(), MAX_JSON_BYTES);
    }

    private static ConfigSyncS2CPayload decode(RegistryFriendlyByteBuf buf) {
        String json = buf.readUtf(MAX_JSON_BYTES);
        if (json.isBlank()) {
            throw new DecoderException("Empty config sync payload");
        }
        return new ConfigSyncS2CPayload(CultivationConfig.fromSyncJson(json));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
