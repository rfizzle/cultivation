package com.rfizzle.cultivation.harvest;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Iterator;
import java.util.List;

/**
 * The exhausted yield clamp ({@code design/SPEC.md} §1): a harvest over
 * fertility-0 farmland keeps at most 1 of the crop's primary product and, where
 * the seed is a distinct item, at most 1 seed. When product and seed are the
 * same item (carrots, potatoes) the cap is 1 total. Stacks of any other item
 * pass through untouched — bonus drops are appended after the clamp and carry
 * their own exhausted suppression.
 */
public final class YieldClamp {
    private YieldClamp() {
    }

    public static void clampToBareMinimum(List<ItemStack> drops, Item product, Item seed) {
        int productLeft = 1;
        int seedLeft = product == seed ? 0 : 1;
        Iterator<ItemStack> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            if (stack.is(product)) {
                int keep = Math.min(stack.getCount(), productLeft);
                productLeft -= keep;
                if (keep == 0) {
                    iterator.remove();
                } else {
                    stack.setCount(keep);
                }
            } else if (stack.is(seed)) {
                int keep = Math.min(stack.getCount(), seedLeft);
                seedLeft -= keep;
                if (keep == 0) {
                    iterator.remove();
                } else {
                    stack.setCount(keep);
                }
            }
        }
    }
}
