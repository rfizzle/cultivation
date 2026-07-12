package com.rfizzle.cultivation.compat.common;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 coverage of the crop tooltip formatter: growth number formatting and the
 * polyculture line's presence gate, from hand-built tags.
 */
class CropProbeTooltipTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static CompoundTag tag(float growth, boolean polyculture) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(CropProbeTooltip.KEY_PRESENT, true);
        tag.putFloat(CropProbeTooltip.KEY_GROWTH, growth);
        tag.putBoolean(CropProbeTooltip.KEY_POLYCULTURE, polyculture);
        return tag;
    }

    private static String key(Component line) {
        return ((TranslatableContents) line.getContents()).getKey();
    }

    @Test
    void noPresenceFlagYieldsNoLines() {
        assertTrue(CropProbeTooltip.buildLines(new CompoundTag()).isEmpty());
    }

    @Test
    void growthLineFormatsTwoDecimals() {
        List<Component> lines = CropProbeTooltip.buildLines(tag(1.2F, false));
        assertEquals(1, lines.size());
        assertEquals("tooltip.cultivation.crop.growth", key(lines.get(0)));
        assertEquals("1.20", ((TranslatableContents) lines.get(0).getContents()).getArgs()[0]);
    }

    @Test
    void polycultureLineOnlyWhenActive() {
        assertEquals(1, CropProbeTooltip.buildLines(tag(0.5F, false)).size());
        List<Component> active = CropProbeTooltip.buildLines(tag(1.2F, true));
        assertEquals(2, active.size());
        assertEquals("tooltip.cultivation.crop.polyculture", key(active.get(1)));
    }
}
