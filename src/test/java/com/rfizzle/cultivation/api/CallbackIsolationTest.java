package com.rfizzle.cultivation.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * Pins the Concord API Standard §3.1 listener-isolation contract on both
 * public callbacks: a throwing listener is caught and skipped, the listeners
 * registered after it still run, and a {@link VirtualMachineError} is rethrown
 * rather than absorbed.
 *
 * <p>Fabric {@code Event}s have no unregister, so registrations here are
 * permanent for the test JVM — one method per callback, asserting in order, so
 * the fatal-error listener cannot leak into the isolation assertion. No other
 * unit test invokes either callback.
 */
class CallbackIsolationTest {

    @Test
    void harvestCallbackIsolatesListenersButRethrowsFatalErrors() {
        List<String> ran = new ArrayList<>();

        CultivationHarvestCallback.EVENT.register((level, pos, crop, drops, harvester) -> ran.add("first"));
        CultivationHarvestCallback.EVENT.register((level, pos, crop, drops, harvester) -> {
            throw new IllegalStateException("guest is broken");
        });
        // AbstractMethodError is what a consumer compiled against an older signature
        // raises — an Exception catch would let it escape and kill the server tick.
        CultivationHarvestCallback.EVENT.register((level, pos, crop, drops, harvester) -> {
            throw new AbstractMethodError("stale consumer");
        });
        CultivationHarvestCallback.EVENT.register((level, pos, crop, drops, harvester) -> ran.add("last"));

        assertDoesNotThrow(() -> CultivationHarvestCallback.EVENT.invoker()
                .onHarvest(null, BlockPos.ZERO, null, new ArrayList<>(), null));
        assertEquals(List.of("first", "last"), ran);

        CultivationHarvestCallback.EVENT.register((level, pos, crop, drops, harvester) -> {
            throw new StackOverflowError("the JVM is gone, not the guest");
        });
        assertThrows(StackOverflowError.class, () -> CultivationHarvestCallback.EVENT.invoker()
                .onHarvest(null, BlockPos.ZERO, null, new ArrayList<>(), null));
    }

    @Test
    void foodCallbackIsolatesListenersButRethrowsFatalErrors() {
        List<String> ran = new ArrayList<>();

        CultivationFoodCallback.EVENT.register((player, food, effectiveness) -> ran.add("first"));
        CultivationFoodCallback.EVENT.register((player, food, effectiveness) -> {
            throw new IllegalStateException("guest is broken");
        });
        CultivationFoodCallback.EVENT.register((player, food, effectiveness) -> {
            throw new NoClassDefFoundError("stale consumer");
        });
        CultivationFoodCallback.EVENT.register((player, food, effectiveness) -> ran.add("last"));

        assertDoesNotThrow(() -> CultivationFoodCallback.EVENT.invoker().onFood(null, null, 1.0f));
        assertEquals(List.of("first", "last"), ran);

        CultivationFoodCallback.EVENT.register((player, food, effectiveness) -> {
            throw new StackOverflowError("the JVM is gone, not the guest");
        });
        assertThrows(StackOverflowError.class,
                () -> CultivationFoodCallback.EVENT.invoker().onFood(null, null, 0.5f));
    }
}
