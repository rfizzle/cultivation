package com.rfizzle.cultivation.soil;

import com.rfizzle.cultivation.attachment.SoilData;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.config.CultivationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Compost Fertilizer ({@code design/SPEC.md} §6): the composter's high-tier
 * output, applied to farmland to buy a run of guaranteed bonus harvests. The
 * dose counter lives on {@link SoilData#fertilizerRemaining()}; application
 * fills it and each mature harvest at the choke point spends one.
 *
 * <p>Pure decisions ({@link #canApplyDose}, {@link #grantsHarvestBonus}) sit
 * apart from the world-mutating {@link #applyDose} so the bookkeeping is
 * testable without a level, mirroring {@link EnrichedTilling}.
 */
public final class Fertilizer {
    /** Bone meal on a block: green sparkle particles plus the use sound, client-side. */
    private static final int LEVEL_EVENT_BONE_MEAL = 1505;

    private Fertilizer() {
    }

    /**
     * Whether a use would set (or top up) the dose. A partial or empty dose
     * accepts a fill; an already-full dose ({@code current >= dose}) fails
     * silently so the item is not consumed for nothing.
     */
    public static boolean canApplyDose(int current, int dose) {
        return current < dose;
    }

    /** Pure harvest-side decision: a block with a live dose pays the bonus. */
    public static boolean grantsHarvestBonus(int remaining) {
        return remaining > 0;
    }

    /**
     * Whether the composter should yield Fertilizer instead of bone meal — the
     * live predicate both composter seams read. {@code enableFertilizer=false}
     * reverts to bone meal regardless of {@code composterProducesFertilizer}.
     */
    public static boolean composterProducesFertilizer() {
        CultivationConfig config = CultivationConfig.get();
        return config.enableFertilizer && config.composterProducesFertilizer;
    }

    /**
     * Sets a full dose on the farmland at {@code soilPos}, playing the vanilla
     * bone-meal effect. Returns whether the dose was applied — {@code false}
     * when the feature is disabled or the dose is already full, in which case
     * the caller must not consume the item. Never advances crop age.
     */
    public static boolean applyDose(ServerLevel level, BlockPos soilPos, @Nullable Player player) {
        CultivationConfig config = CultivationConfig.get();
        if (!config.enableFertilizer) {
            return false;
        }
        int dose = config.fertilizerDoseHarvests;
        SoilData data = SoilStores.peek(level, soilPos);
        int current = data == null ? 0 : data.fertilizerRemaining();
        if (!canApplyDose(current, dose)) {
            return false; // already full: no effect, item not consumed
        }
        SoilStores.update(level, soilPos, true, current2 -> current2.withFertilizerRemaining(dose));
        level.levelEvent(LEVEL_EVENT_BONE_MEAL, soilPos, 15);
        if (player != null) {
            player.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
        }
        return true;
    }
}
