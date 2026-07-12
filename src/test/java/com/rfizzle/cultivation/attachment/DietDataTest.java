package com.rfizzle.cultivation.attachment;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DietDataTest {
    private static final double PER_REPEAT = 0.10;
    private static final double FLOOR = 0.5;
    private static final int RESET = 3;

    private static final ResourceLocation A = ResourceLocation.withDefaultNamespace("carrot");
    private static final ResourceLocation B = ResourceLocation.withDefaultNamespace("potato");
    private static final ResourceLocation C = ResourceLocation.withDefaultNamespace("bread");

    private static <T> T roundTrip(Codec<T> codec, T value) {
        Tag encoded = codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
        return codec.parse(NbtOps.INSTANCE, encoded).getOrThrow();
    }

    private static DietData eat(DietData data, ResourceLocation item) {
        return data.afterEat(item, PER_REPEAT, FLOOR, RESET);
    }

    // --- Effectiveness curve ---

    @Test
    void effectivenessStepsDownAndFloors() {
        assertEquals(1.0, DietData.effectiveness(0, PER_REPEAT, FLOOR), 1e-9);
        assertEquals(0.9, DietData.effectiveness(1, PER_REPEAT, FLOOR), 1e-9);
        assertEquals(0.8, DietData.effectiveness(2, PER_REPEAT, FLOOR), 1e-9);
        assertEquals(0.7, DietData.effectiveness(3, PER_REPEAT, FLOOR), 1e-9);
        assertEquals(0.6, DietData.effectiveness(4, PER_REPEAT, FLOOR), 1e-9);
        assertEquals(0.5, DietData.effectiveness(5, PER_REPEAT, FLOOR), 1e-9);
        // Beyond the floor-reaching count it never drops below the floor.
        assertEquals(0.5, DietData.effectiveness(6, PER_REPEAT, FLOOR), 1e-9);
        assertEquals(0.5, DietData.effectiveness(100, PER_REPEAT, FLOOR), 1e-9);
    }

    @Test
    void nutritionNeverDropsBelowOneForRealFood() {
        assertEquals(1, DietData.scaledNutrition(1, 0.5));
        assertEquals(1, DietData.scaledNutrition(6, 0.1)); // round(0.6) = 1, not 0
        assertEquals(3, DietData.scaledNutrition(6, 0.5));
        assertEquals(6, DietData.scaledNutrition(6, 1.0));
        // A zero-nutrition component is left untouched (nothing to floor).
        assertEquals(0, DietData.scaledNutrition(0, 0.5));
    }

    @Test
    void reductionPercentTracksTheEffectiveness() {
        assertEquals(0, DietData.reductionPercent(1.0));
        assertEquals(20, DietData.reductionPercent(0.8));
        assertEquals(50, DietData.reductionPercent(0.5));
    }

    @Test
    void stackCapIsWhereEffectivenessReachesTheFloor() {
        assertEquals(5, DietData.stackCap(0.10, 0.5));
        assertEquals(0, DietData.stackCap(0.0, 0.5));  // no decay
        assertEquals(0, DietData.stackCap(0.10, 1.0)); // floor is full effect
        assertTrue(DietData.atFloor(5, 0.10, 0.5));
        assertFalse(DietData.atFloor(4, 0.10, 0.5));
    }

    // --- Eat sequences (the SPEC §3 consequences) ---

    @Test
    void singleFoodDecaysToTheFloorWithoutResetting() {
        DietData data = DietData.EMPTY;
        double[] expected = {1.0, 0.9, 0.8, 0.7, 0.6, 0.5, 0.5};
        for (double eff : expected) {
            assertEquals(eff, data.effectiveness(A, PER_REPEAT, FLOOR), 1e-9);
            data = eat(data, A);
        }
        assertEquals(5, data.stackCount(A)); // capped
    }

    @Test
    void twoFoodAlternationGrindsBothToTheFloorWithNoReset() {
        DietData data = DietData.EMPTY;
        ResourceLocation[] order = {A, B, A, B, A, B, A, B, A, B, A, B};
        for (ResourceLocation item : order) {
            data = eat(data, item);
        }
        // Never reset (the last-3 window is never all-distinct), so both hit the floor.
        assertEquals(0.5, data.effectiveness(A, PER_REPEAT, FLOOR), 1e-9);
        assertEquals(0.5, data.effectiveness(B, PER_REPEAT, FLOOR), 1e-9);
        assertEquals(5, data.stackCount(A));
        assertEquals(5, data.stackCount(B));
    }

    @Test
    void threeDistinctEatsClearTheWholeStackMapAndHistory() {
        DietData data = eat(eat(EMPTYWith(A), B), C); // A already had a stack, then B, then C
        assertTrue(data.isDefault(), "three distinct foods in a row reset everything");
        assertEquals(0, data.stackCount(A));
        assertEquals(0, data.stackCount(B));
        assertEquals(1.0, data.effectiveness(A, PER_REPEAT, FLOOR), 1e-9);
    }

    @Test
    void threeFoodRotationAlwaysEatsAtFullStrength() {
        DietData data = DietData.EMPTY;
        ResourceLocation[] rotation = {A, B, C, A, B, C, A, B, C};
        for (ResourceLocation item : rotation) {
            assertEquals(1.0, data.effectiveness(item, PER_REPEAT, FLOOR), 1e-9);
            data = eat(data, item);
        }
    }

    private static DietData EMPTYWith(ResourceLocation item) {
        return eat(DietData.EMPTY, item);
    }

    // --- Codec + bounds ---

    @Test
    void codecRoundTripsAStackedState() {
        DietData data = eat(eat(eat(DietData.EMPTY, A), A), B);
        assertEquals(data, roundTrip(DietData.CODEC, data));
    }

    @Test
    void emptyRoundTrips() {
        assertEquals(DietData.EMPTY, roundTrip(DietData.CODEC, DietData.EMPTY));
    }

    @Test
    void missingFieldsDecodeToDefaults() {
        DietData data = DietData.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
        assertTrue(data.isDefault());
    }

    @Test
    void constructorDropsTamperedNonPositiveCountsAndClampsHistory() {
        DietData data = new DietData(
                Map.of(A, 3, B, 0, C, -2),
                List.of(A, B, C, A, B, C, A)); // 7 entries -> trimmed to MAX_HISTORY
        assertEquals(3, data.stackCount(A));
        assertEquals(0, data.stackCount(B));
        assertEquals(0, data.stackCount(C));
        assertEquals(1, data.stacks().size());
        assertEquals(DietData.MAX_HISTORY, data.history().size());
    }

    @Test
    void stackMapIsBounded() {
        java.util.LinkedHashMap<ResourceLocation, Integer> huge = new java.util.LinkedHashMap<>();
        for (int i = 0; i < DietData.MAX_STACK_ENTRIES + 50; i++) {
            huge.put(ResourceLocation.fromNamespaceAndPath("test", "food_" + i), 1);
        }
        DietData data = new DietData(huge, List.of());
        assertEquals(DietData.MAX_STACK_ENTRIES, data.stacks().size());
    }
}
