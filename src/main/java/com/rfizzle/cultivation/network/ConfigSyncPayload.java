package com.rfizzle.cultivation.network;

import com.rfizzle.cultivation.Cultivation;
import com.rfizzle.cultivation.config.CultivationConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Ships the server's authoritative {@link CultivationConfig} to a joining client
 * so every client feature can honor the server's rules instead of the client's
 * own local file (SPEC §Configuration; the {@code mc-config} sync contract).
 *
 * <p>The whole config crosses as one length-bounded JSON string, which is the
 * suite-wide shape for this payload — class {@code ConfigSyncPayload}, id
 * {@code <mod>:config_sync}. A new config field reaches clients with no codec
 * change at all.
 *
 * <p><strong>The payload carries the blob, not a config.</strong> Decoding is
 * confined to <em>reading</em> a bounded string; the JSON is parsed and clamped
 * by the handler, on the client thread. Interpreting the body inside
 * {@code decode()} put a Gson parse and a {@code clamp()} — which logs one
 * synchronous line per correction — on the netty event loop, where a remote peer
 * controls both how many corrections there are and how often they arrive. It also
 * meant a malformed blob threw out of {@code decode()}, and a throw there
 * disconnects the player rather than letting the handler decide.
 *
 * <p>The client stores the result as the server-authoritative view; client-only
 * presentation keys ({@code showSoilOverlays}, {@code showFatigueTooltips}, the
 * overlay render distance) are read from the local file regardless, so a server
 * never dictates a purely visual client preference.
 */
public record ConfigSyncPayload(String json) implements CustomPacketPayload {
    public static final Type<ConfigSyncPayload> TYPE = new Type<>(Cultivation.id("config_sync"));

    /**
     * The hard wire limit, and the one thing {@code decode} is still allowed to
     * reject. Note the unit: {@code stringUtf8(n)} bounds <em>characters</em>, so
     * the wire allowance is up to {@code 3n} bytes. The compact config is a couple
     * of KB, well inside the suite's "4x the serialized size" rule of thumb;
     * anything near this ceiling is not our config.
     */
    public static final int MAX_JSON_CHARS = 65536;

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSyncPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_JSON_CHARS), ConfigSyncPayload::json,
                    ConfigSyncPayload::new);

    /** The payload carrying the server's current config. */
    public static ConfigSyncPayload of(CultivationConfig config) {
        return new ConfigSyncPayload(config.toSyncJson());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
