package com.rfizzle.cultivation.meal;

import com.rfizzle.cultivation.mixin.ItemComponentsAccessor;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Raises the crafted bowl foods to a real stack (SPEC §4). Vanilla registers the
 * four stews {@code stacksTo(1)}, which makes the meal-buff feature's "shelf of
 * stews for whatever the day demands" impossible to carry. Fabric offers no
 * runtime hook for an already-registered vanilla item's max stack size, so this
 * rebuilds each stew's default {@link DataComponentMap} — every other component
 * preserved (food, suspicious-stew effects) — with {@code MAX_STACK_SIZE = 16},
 * once at init via {@link ItemComponentsAccessor}.
 *
 * <p>Gated by {@code enableMealBuffs} at the call site ({@code
 * Cultivation#onInitialize}): stack size is baked into the item at startup and
 * cannot be hot-swapped, so the toggle is read once. Suspicious stew still stacks
 * only across identical rolled effects — that is inherent to component-equality
 * stacking and needs nothing here.
 */
public final class BowlFoodStacking {
    private BowlFoodStacking() {
    }

    /** Max stack for a crafted bowl food — one shelf slot's worth of meals. */
    public static final int STACK_SIZE = 16;

    /** The crafted bowl foods raised to {@link #STACK_SIZE}. Bowls themselves already stack. */
    private static final List<Item> BOWL_FOODS = List.of(
            Items.RABBIT_STEW,
            Items.BEETROOT_SOUP,
            Items.MUSHROOM_STEW,
            Items.SUSPICIOUS_STEW);

    /**
     * Raises the bowl foods only when meal buffs are enabled — the gate for the
     * whole feature (SPEC §4). Stack size is baked into the item at startup, so
     * {@code enabled} is the value read once at init; a later toggle takes effect
     * on restart. A no-op when disabled, leaving the four stews at their vanilla
     * stack of one.
     */
    public static void applyIfEnabled(boolean enabled) {
        if (enabled) {
            apply();
        }
    }

    /** Raises every bowl food's max stack size to {@link #STACK_SIZE}. Idempotent. */
    public static void apply() {
        for (Item food : BOWL_FOODS) {
            setMaxStackSize(food, STACK_SIZE);
        }
    }

    private static void setMaxStackSize(Item item, int size) {
        ItemComponentsAccessor accessor = (ItemComponentsAccessor) item;
        DataComponentMap patched = DataComponentMap.builder()
                .addAll(accessor.cultivation$getComponents())
                .set(DataComponents.MAX_STACK_SIZE, size)
                .build();
        accessor.cultivation$setComponents(patched);
    }
}
