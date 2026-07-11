package com.rfizzle.cultivation.soil;

import com.rfizzle.cultivation.attachment.SoilData;
import com.rfizzle.cultivation.attachment.SoilStores;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Farmland reversion ({@code design/SPEC.md} §1 edge cases): when farmland
 * becomes any other block — trampling, drying out, shoveling, breaking,
 * explosion, piston, {@code /setblock} — the block-lifetime investments
 * (enriched chance, Fertilizer dose) clear immediately, while fertility,
 * rotation memory, and recovery bookkeeping persist at the position and apply
 * to whatever farmland is tilled there later. Wired in by the
 * {@code BlockBehaviour#onRemove} mixin; runs regardless of feature toggles
 * because a stale investment must never survive its block.
 */
public final class FarmlandReversion {
    private FarmlandReversion() {
    }

    public static void onFarmlandRemoved(ServerLevel level, BlockPos pos) {
        SoilData data = SoilStores.peek(level, pos);
        if (data == null || (data.enrichedChance() == 0 && data.fertilizerRemaining() == 0)) {
            return; // nothing invested — never create or rewrite an entry just to clear it
        }
        SoilStores.update(level, pos, true, SoilData::withInvestmentsCleared);
    }
}
