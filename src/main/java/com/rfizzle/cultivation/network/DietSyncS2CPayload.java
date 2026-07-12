package com.rfizzle.cultivation.network;

import com.rfizzle.cultivation.Cultivation;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pushes the owning player's dietary fatigue to their client for tooltip
 * feedback ({@code design/SPEC.md} §3). Carries the per-food stack map plus the
 * two config knobs the client needs so it recomputes effectiveness with the same
 * formula the server used — no restoration math ever runs on the client.
 *
 * <p>The stack map is empty when the server has dietary fatigue disabled, so the
 * client's tooltips go quiet without the client needing to know the server flag.
 */
public record DietSyncS2CPayload(Map<ResourceLocation, Integer> stacks, float fatiguePerRepeat, float fatigueFloor)
        implements CustomPacketPayload {
    public static final Type<DietSyncS2CPayload> TYPE = new Type<>(Cultivation.id("diet_sync"));

    // A player tracks at most DietData.MAX_STACK_ENTRIES foods; reject anything past a safe ceiling.
    private static final int MAX_ENTRIES = 4096;

    public static final StreamCodec<RegistryFriendlyByteBuf, DietSyncS2CPayload> CODEC =
            StreamCodec.of(DietSyncS2CPayload::encode, DietSyncS2CPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, DietSyncS2CPayload payload) {
        ByteBufCodecs.VAR_INT.encode(buf, payload.stacks.size());
        for (Map.Entry<ResourceLocation, Integer> entry : payload.stacks.entrySet()) {
            ResourceLocation.STREAM_CODEC.encode(buf, entry.getKey());
            ByteBufCodecs.VAR_INT.encode(buf, entry.getValue());
        }
        buf.writeFloat(payload.fatiguePerRepeat);
        buf.writeFloat(payload.fatigueFloor);
    }

    private static DietSyncS2CPayload decode(RegistryFriendlyByteBuf buf) {
        int size = ByteBufCodecs.VAR_INT.decode(buf);
        if (size < 0 || size > MAX_ENTRIES) {
            throw new DecoderException("Diet stack map too large: " + size);
        }
        Map<ResourceLocation, Integer> stacks = new LinkedHashMap<>(Math.max(1, size));
        for (int i = 0; i < size; i++) {
            ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buf);
            int count = ByteBufCodecs.VAR_INT.decode(buf);
            stacks.put(id, count);
        }
        float fatiguePerRepeat = buf.readFloat();
        float fatigueFloor = buf.readFloat();
        return new DietSyncS2CPayload(stacks, fatiguePerRepeat, fatigueFloor);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
