package com.rfizzle.cultivation.soil;

import com.rfizzle.cultivation.attachment.SoilData;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.config.CultivationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The growth-speed modifier applied inside the crop/stem/pitcher randomTick
 * mixins ({@code design/SPEC.md} §1): Tired soil grows crops at
 * {@code tiredGrowthMultiplier}, Exhausted at {@code exhaustedGrowthMultiplier},
 * everything else at exactly 1.0 — monoculture and healthy soil are never
 * penalized. The polyculture bonus (SPEC §2) multiplies in here when it lands.
 */
public final class SoilGrowth {
    private SoilGrowth() {
    }

    /** The combined soil multiplier for the crop at {@code cropPos} (farmland below it). */
    public static float multiplierAt(ServerLevel level, BlockPos cropPos) {
        CultivationConfig config = CultivationConfig.get();
        if (!config.enableSoilFertility) {
            return 1.0F;
        }
        SoilData data = SoilStores.peek(level, cropPos.below());
        if (data == null) {
            return 1.0F;
        }
        SoilBand band = SoilMath.band(data.fertility(), config.tiredThreshold);
        return SoilMath.growthMultiplier(band, config.tiredGrowthMultiplier, config.exhaustedGrowthMultiplier);
    }
}
