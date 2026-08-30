package com.rfizzle.cultivation.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the four code-bound soil overlay textures the client renderer binds by path.
 * A missing one renders as the purple-black "missing texture" placeholder in-world and
 * never surfaces in a headless build.
 *
 * <p>The names are restated here rather than read from {@code SoilOverlayRenderTypes},
 * which owns them as private {@code ResourceLocation} constants. That class is not on
 * the test compile classpath, and putting it there would not help: its static
 * initializer builds {@code RenderType}s against blaze3d, so binding it from a Tier-1
 * test compiles and then dies on class-load under a `test` task that has no client
 * runtime. Duplicating four strings is the cheaper half of that trade — and this test
 * is precisely what fails when the two copies drift.
 */
class SoilOverlayResourceContractTest {
    private static boolean resourceExists(String path) {
        return SoilOverlayResourceContractTest.class.getClassLoader().getResource(path) != null;
    }

    @Test
    void overlayTexturesShip() {
        for (String name : new String[] {"soil_tired", "soil_exhausted", "soil_fertilized", "soil_enriched"}) {
            String path = "assets/cultivation/textures/overlay/" + name + ".png";
            assertTrue(resourceExists(path), "the overlay texture must ship: " + path);
        }
    }
}
