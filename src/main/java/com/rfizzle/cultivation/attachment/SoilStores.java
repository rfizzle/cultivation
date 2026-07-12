package com.rfizzle.cultivation.attachment;

import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.network.SoilOverlayServer;
import com.rfizzle.cultivation.soil.SoilClockState;
import com.rfizzle.cultivation.soil.SoilMath;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

/**
 * The single mediator for every soil read and write ({@code AGENTS.md}, SPEC §1
 * implementation notes). Writes settle lazy fallow recovery first, clamp on
 * construction (SoilData's own contract), evict entries that return to
 * all-default values, and dirty the chunk — Fabric's {@code setAttached}
 * auto-dirties, but in-place mutation of the attached store does not, so a bare
 * mutation outside this class silently fails to persist.
 *
 * <p>Settling only accrues over non-farmland spans: while a position is
 * farmland, the live random-tick path ({@link com.rfizzle.cultivation.soil.SoilRecovery})
 * owns recovery and every tracked random tick advances the clock, which also
 * keeps crop-occupied farmland spans out of the lazy accrual.
 */
public final class SoilStores {
    private SoilStores() {
    }

    /** Read-only view; null when the position is untracked (pristine defaults). */
    @Nullable
    public static SoilData peek(ServerLevel level, BlockPos pos) {
        SoilStore store = level.getChunkAt(pos).getAttached(CultivationAttachments.SOIL);
        return store == null ? null : store.get(SoilStore.pack(pos));
    }

    /** Stored fertility, with untracked positions reading as pristine 100. */
    public static float fertilityAt(ServerLevel level, BlockPos pos) {
        SoilData data = peek(level, pos);
        return data == null ? SoilMath.MAX_FERTILITY : data.fertility();
    }

    /**
     * Lazy-path touch: settles accrued fallow recovery and advances the
     * bookkeeping clock. No-op for untracked positions. Runs regardless of
     * {@code enableSoilFertility} — it only applies recovery owed from spans
     * the soil clock actually advanced over, i.e. enabled time.
     */
    public static void settle(ServerLevel level, BlockPos pos) {
        if (peek(level, pos) == null) {
            return;
        }
        update(level, pos, true, UnaryOperator.identity());
    }

    /**
     * The write choke point. Creates the entry on first write (anchored at the
     * current soil-clock time), optionally settles first, applies {@code op},
     * evicts if the result is all-default, and marks the chunk unsaved.
     *
     * <p>{@code settleFirst} is false only for the live random-tick path, which
     * manages the recovery clock itself and must not have its farmland span
     * re-attributed as dirt time when vanilla reverts the block mid-tick.
     */
    public static void update(ServerLevel level, BlockPos pos, boolean settleFirst, UnaryOperator<SoilData> op) {
        LevelChunk chunk = level.getChunkAt(pos);
        SoilStore store = chunk.getAttached(CultivationAttachments.SOIL);
        boolean created = store == null;
        if (created) {
            store = new SoilStore();
        }
        int key = SoilStore.pack(pos);
        long now = SoilClockState.get(level).time();
        SoilData data = store.get(key);
        if (data == null) {
            data = SoilData.pristine(now);
        } else if (settleFirst) {
            data = settled(level, pos, data, now);
        }
        SoilData before = data;
        SoilData after = op.apply(before);
        store.put(key, after);
        if (store.isEmpty()) {
            if (!created) {
                chunk.removeAttached(CultivationAttachments.SOIL);
                chunk.setUnsaved(true);
            }
        } else if (created) {
            chunk.setAttached(CultivationAttachments.SOIL, store);
        } else {
            chunk.setUnsaved(true);
        }
        // Overlay sync (SPEC §1): every write is the single seam where a position's
        // visible band/dose/enrichment can change, so the delta push rides here.
        SoilOverlayServer.notifyFlagChange(level, pos, before, after);
    }

    private static SoilData settled(ServerLevel level, BlockPos pos, SoilData data, long now) {
        if (now <= data.lastRecoveryCheck()) {
            // A clock behind the bookmark means a reset or tampered save; re-anchor without accrual.
            return data.withRecoveryCheck(now);
        }
        if (!level.getBlockState(pos).is(Blocks.FARMLAND)) {
            CultivationConfig config = CultivationConfig.get();
            int randomTickSpeed = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
            float gain = SoilMath.lazyRecovery(
                    now - data.lastRecoveryCheck(), config.fallowRecoveryPerRandomTick, randomTickSpeed);
            data = data.withFertility(data.fertility() + gain);
        }
        return data.withRecoveryCheck(now);
    }
}
