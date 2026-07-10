package com.rfizzle.cultivation;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CultivationTest {

    @Test
    void idUsesModNamespace() {
        ResourceLocation id = Cultivation.id("soil");
        assertEquals(Cultivation.MOD_ID, id.getNamespace());
        assertEquals("soil", id.getPath());
    }
}
