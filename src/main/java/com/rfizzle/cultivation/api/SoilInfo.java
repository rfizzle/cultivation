package com.rfizzle.cultivation.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * A read-only snapshot of one farmland block's soil state.
 *
 * @param fertility           0–100; 100 for untracked (pristine) farmland
 * @param enrichedChance      bonus-drop chance in percent from high-tier tilling (SPEC §5)
 * @param fertilizerRemaining harvests left on the current Fertilizer dose (SPEC §6)
 * @param lastCrop            the crop block id most recently harvested here (rotation memory)
 */
@Stable
public record SoilInfo(
        float fertility,
        int enrichedChance,
        int fertilizerRemaining,
        Optional<ResourceLocation> lastCrop
) {
}
