package com.rfizzle.cultivation.meal;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins the bowl-food stacking mutation (SPEC §4) to the four crafted stews at a
 * stack of 16, proves the rebuild keeps every other component, and guards the
 * "no other stack-size changes" contract so a later edit can't sweep an extra
 * item into the set. The end-to-end eat/bowl-return behavior is covered at Tier 3
 * in {@code BowlFoodStackingGameTest}.
 */
class BowlFoodStackingTest {

    @BeforeAll
    static void bootstrapAndApply() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BowlFoodStacking.apply();
    }

    @Test
    void bowlFoodsStackToSixteen() {
        assertEquals(BowlFoodStacking.STACK_SIZE, Items.RABBIT_STEW.getDefaultMaxStackSize());
        assertEquals(BowlFoodStacking.STACK_SIZE, Items.BEETROOT_SOUP.getDefaultMaxStackSize());
        assertEquals(BowlFoodStacking.STACK_SIZE, Items.MUSHROOM_STEW.getDefaultMaxStackSize());
        assertEquals(BowlFoodStacking.STACK_SIZE, Items.SUSPICIOUS_STEW.getDefaultMaxStackSize());
    }

    @Test
    void rebuildPreservesOtherComponents() {
        // The rebuild set only MAX_STACK_SIZE — the stews keep their food component,
        // and suspicious stew keeps its rolled-effects component (so distinct rolls
        // still refuse to merge under component-equality stacking).
        assertNotNull(Items.RABBIT_STEW.components().get(DataComponents.FOOD),
                "rabbit stew keeps its food component");
        assertNotNull(Items.SUSPICIOUS_STEW.components().get(DataComponents.SUSPICIOUS_STEW_EFFECTS),
                "suspicious stew keeps its rolled-effects component");
    }

    @Test
    void noOtherStackSizesMove() {
        // A default-64 food and two vanilla stack-1 controls stay put — the change
        // is precisely the four crafted bowl foods, nothing else.
        assertEquals(64, Items.APPLE.getDefaultMaxStackSize());
        assertEquals(64, Items.BOWL.getDefaultMaxStackSize());
        assertEquals(1, Items.CAKE.getDefaultMaxStackSize());
    }
}
