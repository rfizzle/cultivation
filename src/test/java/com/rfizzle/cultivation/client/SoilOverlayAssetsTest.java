package com.rfizzle.cultivation.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the four code-bound soil overlay textures the client renderer binds by
 * path ({@link SoilOverlayRenderTypes}). A missing one renders as the purple-black
 * "missing texture" placeholder in-world and never surfaces in a headless build.
 */
class SoilOverlayAssetsTest {
    private static boolean resourceExists(String path) {
        return SoilOverlayAssetsTest.class.getClassLoader().getResource(path) != null;
    }

    @Test
    void overlayTexturesShip() {
        for (String name : new String[] {"soil_tired", "soil_exhausted", "soil_fertilized", "soil_enriched"}) {
            String path = "assets/cultivation/textures/overlay/" + name + ".png";
            assertTrue(resourceExists(path), "the overlay texture must ship: " + path);
        }
    }
}
