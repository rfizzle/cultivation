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
 * multiplicatively with the polyculture bonus (SPEC §2). Tired soil grows crops
 * at {@code tiredGrowthMultiplier}, Exhausted at
 * {@code exhaustedGrowthMultiplier}, everything else at exactly 1.0 —
 * monoculture and healthy soil are never penalized. The two parts are gated by
 * their own config toggles: disabling soil fertility leaves the polyculture
 * bonus running, and vice versa.
 */
public final class SoilGrowth {
    private SoilGrowth() {
    }

    /** The combined growth multiplier for the crop {@code state} at {@code cropPos} (farmland below it). */
    public static float multiplierAt(ServerLevel level, BlockPos cropPos, BlockState state) {
        return fertilityMultiplierAt(level, cropPos) * Polyculture.multiplierAt(level, cropPos, state);
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
