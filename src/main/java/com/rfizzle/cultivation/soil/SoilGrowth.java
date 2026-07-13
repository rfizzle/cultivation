package com.rfizzle.cultivation.soil;

import com.rfizzle.cultivation.attachment.SoilData;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.config.CultivationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The growth-speed modifier applied inside the crop/stem/pitcher randomTick
 * mixins: the fertility band multiplier ({@code design/SPEC.md} §1) combined
 * multiplicatively with the polyculture bonus and the bee-pollination bonus
 * (SPEC §2). Tired soil grows crops at {@code tiredGrowthMultiplier}, Exhausted
 * at {@code exhaustedGrowthMultiplier}, everything else at exactly 1.0 —
 * monoculture and healthy soil are never penalized. Each part is gated by its
 * own config toggle: disabling one leaves the others running.
 */
public final class SoilGrowth {
    private SoilGrowth() {
    }

    /** The combined growth multiplier for the crop {@code state} at {@code cropPos} (farmland below it). */
    public static float multiplierAt(ServerLevel level, BlockPos cropPos, BlockState state) {
        return fertilityMultiplierAt(level, cropPos)
                * Polyculture.multiplierAt(level, cropPos, state)
                * BeePollination.multiplierAt(level, cropPos);
    }

    /**
     * The growth-roll bound a second-wave crop (nether wart, sweet berries) should
     * use in place of its vanilla {@code nextInt(vanillaBound)}: the fertility band
     * alone scales it — no polyculture or bee bonus, which stay a farmland-row
     * mechanic (SPEC §1/§2). Returns {@code vanillaBound} unchanged (a bit-identical
     * roll) whenever soil fertility or the non-farmland-soil toggle is off, or the
     * ground below is untracked.
     */
    public static int secondWaveGrowthBound(int vanillaBound, ServerLevel level, BlockPos cropPos) {
        CultivationConfig config = CultivationConfig.get();
        if (!config.enableNonFarmlandSoil) {
            return vanillaBound;
        }
        return SoilMath.scaledGrowthBound(vanillaBound, fertilityMultiplierAt(level, cropPos));
    }

    /** The fertility band's multiplier alone (§1) — 1.0 while the soil system is disabled. */
    private static float fertilityMultiplierAt(ServerLevel level, BlockPos cropPos) {
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
