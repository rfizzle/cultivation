package com.rfizzle.cultivation.harvest;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Iterator;
import java.util.List;

/**
 * The scythe's replant seam ({@code design/SPEC.md} §7): a sweep withdraws
 * exactly one of a harvested crop's seed item from its own drops to replant the
 * block at age 0. Whatever remains spawns in the world. When no seed is present
 * — an unlucky wheat roll, or a crop whose mature drop is not its seed — the
 * block is left empty, so the caller keys its replant on the returned flag.
 *
 * <p>Pure list mutation with no world coupling (mirrors {@link YieldClamp}), so
 * it unit-tests at Tier 1.
 */
public final class SeedWithdrawal {
    private SeedWithdrawal() {
    }

    /**
     * Removes one unit of {@code seed} from {@code drops} in place — decrementing
     * a larger stack, or dropping an emptied stack from the list — and returns
     * whether a seed was found to withdraw.
     */
    public static boolean withdrawOne(List<ItemStack> drops, Item seed) {
        Iterator<ItemStack> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            if (stack.is(seed)) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    iterator.remove();
                }
                return true;
            }
        }
        return false;
    }
}
