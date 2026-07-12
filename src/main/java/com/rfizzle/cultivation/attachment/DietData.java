package com.rfizzle.cultivation.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One player's dietary state ({@code design/SPEC.md} §3): a per-food fatigue
 * stack map keyed by item id and a short history of the most recent eats. An
 * absent attachment means pristine defaults — a record that {@link #isDefault()
 * returns to empty} is evicted from the player rather than stored.
 *
 * <p>Keyed by {@link ResourceLocation} item id (not {@code Item}) so the whole
 * record — codec, decay, and reset math — is a pure POJO with no bootstrapped
 * registry, mirroring {@link SoilData}'s {@code lastCrop}. The seams convert
 * {@code Item} to id at the edge.
 *
 * <p>Both collections are bounded and defensively copied on construction, and a
 * player's saved diet entry is untrusted input: counts below 1 are dropped, the
 * history is trimmed to the last {@link #MAX_HISTORY}, and the stack map is
 * capped at {@link #MAX_STACK_ENTRIES} (footprint at the cap: a few KB of item
 * ids and ints). Stacks serialize sorted by id so saves diff cleanly.
 */
public record DietData(Map<ResourceLocation, Integer> stacks, List<ResourceLocation> history) {
    /**
     * Defensive cap on distinct tracked foods. Normal play keeps this tiny — a
     * variety reset clears the map — but an adversarial pair-alternation pattern
     * (A A B B C C …) never trips the reset window, so the map is bounded and
     * FIFO-by-fatigue evicted rather than left to grow without limit.
     */
    static final int MAX_STACK_ENTRIES = 256;

    /** History depth — the max of {@code fatigueResetDistinctFoods}'s config range. */
    static final int MAX_HISTORY = 5;

    /** The pristine, untracked state. */
    public static final DietData EMPTY = new DietData(Map.of(), List.of());

    private record StackEntry(ResourceLocation item, int count) {
        static final Codec<StackEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(StackEntry::item),
                Codec.INT.fieldOf("count").forGetter(StackEntry::count)
        ).apply(instance, StackEntry::new));
    }

    public static final Codec<DietData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            StackEntry.CODEC.listOf().optionalFieldOf("stacks", List.of()).forGetter(DietData::stackEntries),
            ResourceLocation.CODEC.listOf().optionalFieldOf("history", List.of()).forGetter(DietData::history)
    ).apply(instance, DietData::fromCodec));

    public DietData {
        stacks = normalizeStacks(stacks);
        history = normalizeHistory(history);
    }

    private static DietData fromCodec(List<StackEntry> entries, List<ResourceLocation> history) {
        LinkedHashMap<ResourceLocation, Integer> map = new LinkedHashMap<>();
        for (StackEntry entry : entries) {
            map.put(entry.item(), entry.count());
        }
        return new DietData(map, history);
    }

    private List<StackEntry> stackEntries() {
        List<StackEntry> list = new ArrayList<>(stacks.size());
        stacks.forEach((item, count) -> list.add(new StackEntry(item, count)));
        return list;
    }

    /** Nothing tracked — the store evicts this so the player stays byte-identical to vanilla. */
    public boolean isDefault() {
        return stacks.isEmpty() && history.isEmpty();
    }

    public int stackCount(ResourceLocation item) {
        return stacks.getOrDefault(item, 0);
    }

    /** The multiplier this player's next eat of {@code item} would receive. */
    public double effectiveness(ResourceLocation item, double fatiguePerRepeat, double fatigueFloor) {
        return effectiveness(stackCount(item), fatiguePerRepeat, fatigueFloor);
    }

    /**
     * Records one eat of {@code item} and returns the resulting state: the item's
     * stack is incremented (capped at the count that first reaches the floor), the
     * item is appended to the history, and — if the last {@code resetDistinct}
     * eats are all distinct — the entire map and history are cleared.
     */
    public DietData afterEat(ResourceLocation item, double fatiguePerRepeat, double fatigueFloor, int resetDistinct) {
        int cap = stackCap(fatiguePerRepeat, fatigueFloor);
        LinkedHashMap<ResourceLocation, Integer> nextStacks = new LinkedHashMap<>(stacks);
        if (cap > 0) {
            nextStacks.merge(item, 1, (current, one) -> Math.min(current + one, cap));
        }
        List<ResourceLocation> nextHistory = new ArrayList<>(history);
        nextHistory.add(item);
        if (isResetTriggered(nextHistory, resetDistinct)) {
            return EMPTY;
        }
        return new DietData(nextStacks, nextHistory);
    }

    // --- Pure fatigue math (unit-tested at Tier 1) ---

    /** {@code max(floor, 1 - perRepeat * stacks)}, clamped into {@code [floor, 1]}. */
    public static double effectiveness(int stacks, double fatiguePerRepeat, double fatigueFloor) {
        double eff = 1.0 - fatiguePerRepeat * stacks;
        return Math.max(fatigueFloor, Math.min(1.0, eff));
    }

    /** The stack count at which effectiveness first reaches the floor; 0 when there is no decay. */
    public static int stackCap(double fatiguePerRepeat, double fatigueFloor) {
        if (fatiguePerRepeat <= 0.0 || fatigueFloor >= 1.0) {
            return 0;
        }
        return (int) Math.ceil((1.0 - fatigueFloor) / fatiguePerRepeat);
    }

    /** Nutrition after fatigue: a food restoring at least 1 hunger never drops to 0. */
    public static int scaledNutrition(int nutrition, double effectiveness) {
        if (nutrition <= 0) {
            return nutrition;
        }
        return Math.max(1, (int) Math.round(nutrition * effectiveness));
    }

    /** The whole-percent reduction shown on the tooltip, e.g. {@code 0.8 -> 20}. */
    public static int reductionPercent(double effectiveness) {
        return (int) Math.round((1.0 - effectiveness) * 100.0);
    }

    /** True once the item is fatigued as far as it can go — the "thoroughly tired" state. */
    public static boolean atFloor(int stacks, double fatiguePerRepeat, double fatigueFloor) {
        int cap = stackCap(fatiguePerRepeat, fatigueFloor);
        return cap > 0 && stacks >= cap;
    }

    static boolean isResetTriggered(List<ResourceLocation> history, int resetDistinct) {
        if (resetDistinct <= 0 || history.size() < resetDistinct) {
            return false;
        }
        List<ResourceLocation> window = history.subList(history.size() - resetDistinct, history.size());
        return new HashSet<>(window).size() == resetDistinct;
    }

    private static Map<ResourceLocation, Integer> normalizeStacks(Map<ResourceLocation, Integer> in) {
        List<StackEntry> entries = new ArrayList<>(in.size());
        for (Map.Entry<ResourceLocation, Integer> e : in.entrySet()) {
            if (e.getKey() != null && e.getValue() != null && e.getValue() > 0) {
                entries.add(new StackEntry(e.getKey(), e.getValue()));
            }
        }
        if (entries.size() > MAX_STACK_ENTRIES) {
            // Keep the most-fatigued foods; drop the least. Ties break by id so eviction is deterministic.
            entries.sort(Comparator.comparingInt(StackEntry::count).thenComparing(StackEntry::item));
            entries = new ArrayList<>(entries.subList(entries.size() - MAX_STACK_ENTRIES, entries.size()));
        }
        entries.sort(Comparator.comparing(StackEntry::item));
        LinkedHashMap<ResourceLocation, Integer> out = new LinkedHashMap<>();
        for (StackEntry entry : entries) {
            out.put(entry.item(), entry.count());
        }
        return Collections.unmodifiableMap(out);
    }

    private static List<ResourceLocation> normalizeHistory(List<ResourceLocation> in) {
        List<ResourceLocation> copy = new ArrayList<>(in.size());
        for (ResourceLocation item : in) {
            if (item != null) {
                copy.add(item);
            }
        }
        if (copy.size() > MAX_HISTORY) {
            copy = new ArrayList<>(copy.subList(copy.size() - MAX_HISTORY, copy.size()));
        }
        return List.copyOf(copy);
    }
}
