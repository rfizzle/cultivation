package com.rfizzle.cultivation.soil;

import com.rfizzle.cultivation.attachment.SoilData;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.item.CultivationItems;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Villager field stewardship ({@code design/SPEC.md} §8): the farmer's fallow
 * hysteresis, rotation preference, and Fertilizer upkeep. The pure decisions
 * ({@link #evaluateReplant}, {@link #acceptSeed}) sit apart from the
 * store-touching orchestration the {@code HarvestFarmland} mixin calls, so the
 * threshold and rotation logic is testable without a level, mirroring
 * {@link Fertilizer} and {@link EnrichedTilling}.
 */
public final class VillagerStewardship {
    private VillagerStewardship() {
    }

    /** A replant decision paired with the (possibly flipped) fallow latch. */
    public record ReplantDecision(boolean replant, boolean fallowLatch) {
    }

    /**
     * The fallow/replant hysteresis: a block below {@code fallowThreshold} is
     * excluded and latched fallow; at or above {@code replantThreshold} it is
     * eligible and the latch clears; between the two thresholds it follows the
     * latch it already carries — the gap that keeps farmers from churning at the
     * boundary.
     */
    public static ReplantDecision evaluateReplant(
            float fertility, boolean fallowLatch, double fallowThreshold, double replantThreshold) {
        if (fertility >= replantThreshold) {
            return new ReplantDecision(true, false);
        }
        if (fertility < fallowThreshold) {
            return new ReplantDecision(false, true);
        }
        return new ReplantDecision(!fallowLatch, fallowLatch);
    }

    /**
     * Whether the farmer may replant the farmland at {@code soilPos}, settling
     * the fallow latch as a side effect. Reads live fertility and the stored
     * latch, and writes the latch back through the store only when it flips.
     * Fallow village blocks stay farmland and recover on the live random-tick
     * path, so the peeked fertility is current without a settle.
     *
     * <p>Called from the replant gate, which only fires when the farmer holds a
     * seed, so the latch settles on the farmer's next seed-in-hand visit — a
     * recovered plot's stale latch is inert until then (no seed, no replant).
     */
    public static boolean canReplant(ServerLevel level, BlockPos soilPos, CultivationConfig config) {
        SoilData data = SoilStores.peek(level, soilPos);
        float fertility = data == null ? SoilMath.MAX_FERTILITY : data.fertility();
        boolean latch = data != null && data.villagerFallow();
        ReplantDecision decision = evaluateReplant(
                fertility, latch, config.villagerFallowThreshold, config.villagerReplantThreshold);
        if (decision.fallowLatch() != latch) {
            SoilStores.update(level, soilPos, false, d -> d.withVillagerFallow(decision.fallowLatch()));
        }
        return decision.replant();
    }

    /**
     * Rotation preference: reject a seed whose crop matches the block's
     * {@code lastCrop} only when the inventory holds a differing plantable seed,
     * so the farmer plants the rotation; accept it when it is the only option. A
     * seed with no crop identity, or a block with no rotation memory, always
     * passes.
     */
    public static boolean acceptSeed(ItemStack seed, @Nullable ResourceLocation lastCrop, boolean hasDifferingSeed) {
        ResourceLocation cropId = SupportedCrops.cropIdForSeed(seed);
        if (cropId == null || lastCrop == null) {
            return true;
        }
        return !(cropId.equals(lastCrop) && hasDifferingSeed);
    }

    /** Whether {@code inventory} holds a plantable seed whose crop differs from {@code lastCrop}. */
    public static boolean hasDifferingSeed(SimpleContainer inventory, @Nullable ResourceLocation lastCrop) {
        if (lastCrop == null) {
            return false;
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)) {
                continue;
            }
            ResourceLocation cropId = SupportedCrops.cropIdForSeed(stack);
            if (cropId != null && !cropId.equals(lastCrop)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The rotation gate the {@code HarvestFarmland} mixin runs per candidate
     * seed: resolves the block's rotation memory and defers to {@link #acceptSeed}.
     */
    public static boolean acceptSeedForRotation(
            ServerLevel level, BlockPos soilPos, ItemStack seed, SimpleContainer inventory) {
        SoilData data = SoilStores.peek(level, soilPos);
        ResourceLocation lastCrop = data == null ? null : data.lastCrop().orElse(null);
        return acceptSeed(seed, lastCrop, hasDifferingSeed(inventory, lastCrop));
    }

    /**
     * Fertilizer upkeep: a farmer holding Fertilizer doses the farmland at
     * {@code soilPos} <em>only when its counter is fully spent</em> — unlike a
     * player's use (SPEC §6), a farmer never tops up a partial dose (SPEC §8.4).
     * The dose itself, particles, sound, and the {@code enableFertilizer} gate
     * belong to {@link Fertilizer#applyDose}. Consumes one Fertilizer and returns
     * whether a dose was applied.
     */
    public static boolean tryDose(ServerLevel level, BlockPos soilPos, SimpleContainer inventory) {
        int slot = fertilizerSlot(inventory);
        if (slot < 0) {
            return false;
        }
        SoilData data = SoilStores.peek(level, soilPos);
        if (data != null && data.fertilizerRemaining() != 0) {
            return false; // a partial dose is left alone
        }
        if (!Fertilizer.applyDose(level, soilPos, null)) {
            return false;
        }
        inventory.removeItem(slot, 1);
        return true;
    }

    /** The first inventory slot holding Fertilizer, or {@code -1} when the farmer carries none. */
    public static int fertilizerSlot(SimpleContainer inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(CultivationItems.FERTILIZER)) {
                return i;
            }
        }
        return -1;
    }
}
