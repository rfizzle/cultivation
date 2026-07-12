package com.rfizzle.cultivation.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rfizzle.cultivation.soil.SoilMath;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * One farmland position's soil state ({@code design/SPEC.md} §1). An absent
 * entry means pristine defaults — a record that {@link #isDefault() returns to
 * all-default values} is evicted from its {@link SoilStore} rather than stored.
 *
 * <p>{@code lastRecoveryCheck} is a {@link com.rfizzle.cultivation.soil.SoilClockState
 * soil-clock} time, not game time: the clock only advances while soil fertility
 * is enabled, so disabled spans never accrue fallow recovery.
 *
 * <p>Every field is optional in the codec and clamped on construction — a
 * chunk's saved soil entry is untrusted input.
 */
public record SoilData(
        float fertility,
        Optional<ResourceLocation> lastCrop,
        int enrichedChance,
        int fertilizerRemaining,
        long lastRecoveryCheck,
        boolean villagerFallow
) {
    public static final Codec<SoilData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("fertility", SoilMath.MAX_FERTILITY).forGetter(SoilData::fertility),
            ResourceLocation.CODEC.optionalFieldOf("last_crop").forGetter(SoilData::lastCrop),
            Codec.INT.optionalFieldOf("enriched_chance", 0).forGetter(SoilData::enrichedChance),
            Codec.INT.optionalFieldOf("fertilizer_remaining", 0).forGetter(SoilData::fertilizerRemaining),
            Codec.LONG.optionalFieldOf("last_recovery_check", 0L).forGetter(SoilData::lastRecoveryCheck),
            Codec.BOOL.optionalFieldOf("villager_fallow", false).forGetter(SoilData::villagerFallow)
    ).apply(instance, SoilData::new));

    public SoilData {
        fertility = SoilMath.clampFertility(fertility);
        lastCrop = lastCrop == null ? Optional.empty() : lastCrop;
        enrichedChance = Math.clamp(enrichedChance, 0, 100);
        fertilizerRemaining = Math.max(0, fertilizerRemaining);
    }

    /** Pristine defaults with recovery bookkeeping anchored at {@code now}. */
    public static SoilData pristine(long now) {
        return new SoilData(SoilMath.MAX_FERTILITY, Optional.empty(), 0, 0, now, false);
    }

    /** All-default values; {@code lastRecoveryCheck} is pure bookkeeping and never blocks eviction. */
    public boolean isDefault() {
        return fertility >= SoilMath.MAX_FERTILITY
                && lastCrop.isEmpty()
                && enrichedChance == 0
                && fertilizerRemaining == 0
                && !villagerFallow;
    }

    public SoilData withFertility(float newFertility) {
        return new SoilData(newFertility, lastCrop, enrichedChance, fertilizerRemaining, lastRecoveryCheck, villagerFallow);
    }

    public SoilData withLastCrop(ResourceLocation crop) {
        return new SoilData(fertility, Optional.of(crop), enrichedChance, fertilizerRemaining, lastRecoveryCheck, villagerFallow);
    }

    public SoilData withRecoveryCheck(long now) {
        return new SoilData(fertility, lastCrop, enrichedChance, fertilizerRemaining, now, villagerFallow);
    }

    public SoilData withEnrichedChance(int chance) {
        return new SoilData(fertility, lastCrop, chance, fertilizerRemaining, lastRecoveryCheck, villagerFallow);
    }

    public SoilData withFertilizerRemaining(int remaining) {
        return new SoilData(fertility, lastCrop, enrichedChance, remaining, lastRecoveryCheck, villagerFallow);
    }

    /**
     * The villager-stewardship fallow latch ({@code design/SPEC.md} §8): true
     * while a farmer holds a position out of its replant targets. Set when a
     * farmer finds the block below {@code villagerFallowThreshold}, cleared once
     * it recovers to {@code villagerReplantThreshold} — the hysteresis that keeps
     * farmers from churning at the boundary.
     */
    public SoilData withVillagerFallow(boolean fallow) {
        return new SoilData(fertility, lastCrop, enrichedChance, fertilizerRemaining, lastRecoveryCheck, fallow);
    }

    /**
     * Farmland reversion ({@code design/SPEC.md} §1 edge cases): the
     * block-lifetime investments — enriched chance and the Fertilizer dose —
     * clear with the block, while fertility, rotation memory, recovery
     * bookkeeping, and the fallow latch persist at the position.
     */
    public SoilData withInvestmentsCleared() {
        return new SoilData(fertility, lastCrop, 0, 0, lastRecoveryCheck, villagerFallow);
    }
}
