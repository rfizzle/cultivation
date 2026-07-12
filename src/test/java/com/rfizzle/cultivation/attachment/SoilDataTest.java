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
        SoilData data = new SoilData(42.5F, Optional.of(WHEAT), 15, 7, 123456L, true);
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
        assertFalse(data.villagerFallow());
    }

    @Test
    void constructorClampsTamperedValues() {
        SoilData data = new SoilData(-10.0F, Optional.empty(), 500, -3, 0L, false);
        assertEquals(0.0F, data.fertility());
        assertEquals(100, data.enrichedChance());
        assertEquals(0, data.fertilizerRemaining());

        assertEquals(100.0F, new SoilData(Float.NaN, Optional.empty(), 0, 0, 0L, false).fertility());
        assertEquals(100.0F, new SoilData(9999.0F, Optional.empty(), 0, 0, 0L, false).fertility());
    }

    @Test
    void defaultDetectionIgnoresRecoveryBookkeeping() {
        assertTrue(SoilData.pristine(0L).isDefault());
        assertTrue(SoilData.pristine(987654L).isDefault());
        assertFalse(SoilData.pristine(0L).withFertility(99.0F).isDefault());
        assertFalse(SoilData.pristine(0L).withLastCrop(WHEAT).isDefault());
        assertFalse(new SoilData(100.0F, Optional.empty(), 10, 0, 0L, false).isDefault());
        assertFalse(new SoilData(100.0F, Optional.empty(), 0, 5, 0L, false).isDefault());
    }

    @Test
    void fallowLatchBlocksEvictionAndRoundTrips() {
        // A block whose only non-default state is the fallow latch must be kept.
        SoilData latched = SoilData.pristine(0L).withVillagerFallow(true);
        assertFalse(latched.isDefault());
        assertTrue(latched.villagerFallow());
        assertEquals(latched, roundTrip(SoilData.CODEC, latched));

        // Clearing it returns to all-default so the store evicts the entry.
        assertTrue(latched.withVillagerFallow(false).isDefault());
    }

    @Test
    void withersPreserveOtherFields() {
        SoilData base = new SoilData(60.0F, Optional.of(WHEAT), 10, 5, 77L, true);
        assertEquals(new SoilData(30.0F, Optional.of(WHEAT), 10, 5, 77L, true), base.withFertility(30.0F));
        assertEquals(new SoilData(60.0F, Optional.of(WHEAT), 10, 5, 99L, true), base.withRecoveryCheck(99L));
        assertEquals(new SoilData(60.0F, Optional.of(WHEAT), 15, 5, 77L, true), base.withEnrichedChance(15));
        assertEquals(new SoilData(60.0F, Optional.of(WHEAT), 10, 15, 77L, true), base.withFertilizerRemaining(15));
        assertEquals(new SoilData(60.0F, Optional.of(WHEAT), 10, 5, 77L, false), base.withVillagerFallow(false));
    }

    @Test
    void fertilizerRemainingWitherClampsNegativesToZero() {
        SoilData base = SoilData.pristine(0L);
        assertEquals(15, base.withFertilizerRemaining(15).fertilizerRemaining());
        assertEquals(0, base.withFertilizerRemaining(-3).fertilizerRemaining());
        // A block whose only non-default state was its dose returns to all-default when spent.
        assertTrue(base.withFertilizerRemaining(5).withFertilizerRemaining(0).isDefault());
    }

    @Test
    void enrichedChanceWitherClampsLikeTheConstructor() {
        SoilData base = SoilData.pristine(0L);
        assertEquals(100, base.withEnrichedChance(500).enrichedChance());
        assertEquals(0, base.withEnrichedChance(-5).enrichedChance());
    }

    @Test
    void investmentsClearingPreservesSoilMemory() {
        SoilData invested = new SoilData(60.0F, Optional.of(WHEAT), 15, 7, 77L, true);
        SoilData cleared = invested.withInvestmentsCleared();
        // Fertility, rotation memory, recovery bookkeeping, and the fallow latch survive; the dose and enrich chance clear.
        assertEquals(new SoilData(60.0F, Optional.of(WHEAT), 0, 0, 77L, true), cleared);

        // A block whose only non-default state was its investments returns to
        // all-default on clearing, so the store evicts it.
        SoilData investedOnly = SoilData.pristine(42L).withEnrichedChance(15);
        assertFalse(investedOnly.isDefault());
        assertTrue(investedOnly.withInvestmentsCleared().isDefault());
    }
}
