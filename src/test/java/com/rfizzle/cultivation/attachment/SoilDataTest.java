package com.rfizzle.cultivation.attachment;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoilDataTest {
    private static final ResourceLocation WHEAT = ResourceLocation.withDefaultNamespace("wheat");

    private static <T> T roundTrip(Codec<T> codec, T value) {
        Tag encoded = codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
        return codec.parse(NbtOps.INSTANCE, encoded).getOrThrow();
    }

    @Test
    void denseRecordRoundTrips() {
        SoilData data = new SoilData(42.5F, Optional.of(WHEAT), 15, 7, 123456L);
        assertEquals(data, roundTrip(SoilData.CODEC, data));
    }

    @Test
    void pristineRecordRoundTrips() {
        SoilData data = SoilData.pristine(99L);
        assertEquals(data, roundTrip(SoilData.CODEC, data));
    }

    @Test
    void missingFieldsFallBackToDefaults() {
        SoilData data = SoilData.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
        assertEquals(100.0F, data.fertility());
        assertEquals(Optional.empty(), data.lastCrop());
        assertEquals(0, data.enrichedChance());
        assertEquals(0, data.fertilizerRemaining());
        assertEquals(0L, data.lastRecoveryCheck());
    }

    @Test
    void constructorClampsTamperedValues() {
        SoilData data = new SoilData(-10.0F, Optional.empty(), 500, -3, 0L);
        assertEquals(0.0F, data.fertility());
        assertEquals(100, data.enrichedChance());
        assertEquals(0, data.fertilizerRemaining());

        assertEquals(100.0F, new SoilData(Float.NaN, Optional.empty(), 0, 0, 0L).fertility());
        assertEquals(100.0F, new SoilData(9999.0F, Optional.empty(), 0, 0, 0L).fertility());
    }

    @Test
    void defaultDetectionIgnoresRecoveryBookkeeping() {
        assertTrue(SoilData.pristine(0L).isDefault());
        assertTrue(SoilData.pristine(987654L).isDefault());
        assertFalse(SoilData.pristine(0L).withFertility(99.0F).isDefault());
        assertFalse(SoilData.pristine(0L).withLastCrop(WHEAT).isDefault());
        assertFalse(new SoilData(100.0F, Optional.empty(), 10, 0, 0L).isDefault());
        assertFalse(new SoilData(100.0F, Optional.empty(), 0, 5, 0L).isDefault());
    }

    @Test
    void withersPreserveOtherFields() {
        SoilData base = new SoilData(60.0F, Optional.of(WHEAT), 10, 5, 77L);
        assertEquals(new SoilData(30.0F, Optional.of(WHEAT), 10, 5, 77L), base.withFertility(30.0F));
        assertEquals(new SoilData(60.0F, Optional.of(WHEAT), 10, 5, 99L), base.withRecoveryCheck(99L));
        assertEquals(new SoilData(60.0F, Optional.of(WHEAT), 15, 5, 77L), base.withEnrichedChance(15));
    }

    @Test
    void enrichedChanceWitherClampsLikeTheConstructor() {
        SoilData base = SoilData.pristine(0L);
        assertEquals(100, base.withEnrichedChance(500).enrichedChance());
        assertEquals(0, base.withEnrichedChance(-5).enrichedChance());
    }

    @Test
    void investmentsClearingPreservesSoilMemory() {
        SoilData invested = new SoilData(60.0F, Optional.of(WHEAT), 15, 7, 77L);
        SoilData cleared = invested.withInvestmentsCleared();
        assertEquals(new SoilData(60.0F, Optional.of(WHEAT), 0, 0, 77L), cleared);

        // A block whose only non-default state was its investments returns to
        // all-default on clearing, so the store evicts it.
        SoilData investedOnly = SoilData.pristine(42L).withEnrichedChance(15);
        assertFalse(investedOnly.isDefault());
        assertTrue(investedOnly.withInvestmentsCleared().isDefault());
    }
}
