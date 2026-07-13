package com.rfizzle.cultivation.meal;

import com.rfizzle.cultivation.mixin.ItemComponentsAccessor;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins the bowl-food stacking mutation (SPEC §4): the four crafted stews reach a
 * stack of 16 when enabled and stay vanilla stack-1 when the {@code
 * enableMealBuffs} gate is off, the rebuild keeps every other component, and no
 * other item's stack size moves. The end-to-end eat/bowl-return behavior is
 * covered at Tier 3 in {@code BowlFoodStackingGameTest}.
 *
 * <p>Each test mutates the shared vanilla {@link Item} instances, so {@link
 * #restore()} puts every touched item's component map back after each test —
 * both to isolate the tests from one another and to leave the global registry
 * pristine for other classes in this single-JVM test run.
 */
class BowlFoodStackingTest {

    private static final List<Item> STEWS = List.of(
            Items.RABBIT_STEW, Items.BEETROOT_SOUP, Items.MUSHROOM_STEW, Items.SUSPICIOUS_STEW);

    private static final Map<Item, DataComponentMap> PRISTINE = new HashMap<>();

    @BeforeAll
    static void bootstrapAndSnapshot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (Item stew : STEWS) {
            PRISTINE.put(stew, ((ItemComponentsAccessor) stew).cultivation$getComponents());
        }
    }

    @AfterEach
    void restore() {
        PRISTINE.forEach((stew, components) ->
                ((ItemComponentsAccessor) stew).cultivation$setComponents(components));
    }

    @Test
    void enabledStacksBowlFoodsToSixteen() {
        BowlFoodStacking.applyIfEnabled(true);
        assertEquals(BowlFoodStacking.STACK_SIZE, Items.RABBIT_STEW.getDefaultMaxStackSize());
        assertEquals(BowlFoodStacking.STACK_SIZE, Items.BEETROOT_SOUP.getDefaultMaxStackSize());
        assertEquals(BowlFoodStacking.STACK_SIZE, Items.MUSHROOM_STEW.getDefaultMaxStackSize());
        assertEquals(BowlFoodStacking.STACK_SIZE, Items.SUSPICIOUS_STEW.getDefaultMaxStackSize());
    }

    @Test
    void disabledLeavesBowlFoodsAtVanillaStackOfOne() {
        BowlFoodStacking.applyIfEnabled(false);
        assertEquals(1, Items.RABBIT_STEW.getDefaultMaxStackSize());
        assertEquals(1, Items.BEETROOT_SOUP.getDefaultMaxStackSize());
        assertEquals(1, Items.MUSHROOM_STEW.getDefaultMaxStackSize());
        assertEquals(1, Items.SUSPICIOUS_STEW.getDefaultMaxStackSize());
    }

    @Test
    void rebuildPreservesOtherComponents() {
        BowlFoodStacking.apply();
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
        BowlFoodStacking.apply();
        // A default-64 food and two vanilla stack-1 controls stay put — the change
        // is precisely the four crafted bowl foods, nothing else.
        assertEquals(64, Items.APPLE.getDefaultMaxStackSize());
        assertEquals(64, Items.BOWL.getDefaultMaxStackSize());
        assertEquals(1, Items.CAKE.getDefaultMaxStackSize());
    }
}
