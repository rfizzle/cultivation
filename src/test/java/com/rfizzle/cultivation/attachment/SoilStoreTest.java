package com.rfizzle.cultivation.attachment;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoilStoreTest {
    private static final ResourceLocation CARROTS = ResourceLocation.withDefaultNamespace("carrots");

    @Test
    void packRoundTripsInChunkCoordinates() {
        int[][] positions = {{0, 64, 0}, {15, -64, 15}, {7, 320, 3}, {12, -2032, 9}, {1, 2031, 14}};
        for (int[] p : positions) {
            int key = SoilStore.pack(new BlockPos(p[0], p[1], p[2]));
            assertEquals(p[0], SoilStore.unpackX(key));
            assertEquals(p[1], SoilStore.unpackY(key));
            assertEquals(p[2], SoilStore.unpackZ(key));
        }
    }

    @Test
    void packUsesInChunkXZ() {
        // Absolute coordinates from different chunks with the same in-chunk offset collide by design.
        assertEquals(
                SoilStore.pack(new BlockPos(3, 70, 5)),
                SoilStore.pack(new BlockPos(16 + 3, 70, -16 + 5)));
    }

    @Test
    void emptyStoreRoundTrips() {
        SoilStore store = roundTrip(new SoilStore());
        assertTrue(store.isEmpty());
    }

    @Test
    void denseStoreRoundTrips() {
        SoilStore store = new SoilStore();
        SoilData a = new SoilData(12.0F, Optional.of(CARROTS), 10, 3, 42L, true);
        SoilData b = new SoilData(97.5F, Optional.empty(), 15, 0, 7L, false);
        store.put(5, a);
        store.put(9000, b);

        SoilStore reloaded = roundTrip(store);
        assertEquals(2, reloaded.size());
        assertEquals(a, reloaded.get(5));
        assertEquals(b, reloaded.get(9000));
    }

    @Test
    void serializationIsDeterministicallyOrdered() {
        SoilStore forward = new SoilStore();
        SoilStore backward = new SoilStore();
        SoilData data = new SoilData(50.0F, Optional.of(CARROTS), 0, 0, 1L, false);
        for (int key : new int[]{300, 1, 77}) {
            forward.put(key, data);
        }
        for (int key : new int[]{77, 1, 300}) {
            backward.put(key, data);
        }
        assertEquals(encode(forward), encode(backward));
        ListTag list = (ListTag) encode(forward);
        assertEquals(1, list.getCompound(0).getInt("pos"));
        assertEquals(77, list.getCompound(1).getInt("pos"));
        assertEquals(300, list.getCompound(2).getInt("pos"));
    }

    @Test
    void putEvictsAllDefaultEntries() {
        SoilStore store = new SoilStore();
        store.put(5, new SoilData(40.0F, Optional.empty(), 0, 0, 3L, false));
        assertEquals(1, store.size());

        // Returning to all-default values removes the entry rather than storing it.
        store.put(5, SoilData.pristine(999L));
        assertNull(store.get(5));
        assertTrue(store.isEmpty());
    }

    @Test
    void loadEvictsHandEditedDefaultEntries() {
        SoilStore store = new SoilStore();
        store.put(1, new SoilData(30.0F, Optional.empty(), 0, 0, 0L, false));
        ListTag encoded = (ListTag) encode(store);

        // Rewrite the entry's payload to all-default values and reload.
        encoded.getCompound(0).getCompound("data").putFloat("fertility", 100.0F);

        SoilStore reloaded = SoilStore.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        assertTrue(reloaded.isEmpty());
    }

    private static Tag encode(SoilStore store) {
        return SoilStore.CODEC.encodeStart(NbtOps.INSTANCE, store).getOrThrow();
    }

    private static SoilStore roundTrip(SoilStore store) {
        return SoilStore.CODEC.parse(NbtOps.INSTANCE, encode(store)).getOrThrow();
    }
}
