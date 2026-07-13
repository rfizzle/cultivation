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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 coverage of the farmland tooltip formatter — hand-built tags in, exact
 * lines out. No Jade/WTHIT jars, no server: the core takes a {@link CompoundTag}
 * so every branch (presence gate, band, optional lines, network-garbage
 * fallbacks) pins here.
 */
class FarmlandProbeTooltipTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static CompoundTag base(String band, float fertility) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(FarmlandProbeTooltip.KEY_PRESENT, true);
        tag.putFloat(FarmlandProbeTooltip.KEY_FERTILITY, fertility);
        tag.putString(FarmlandProbeTooltip.KEY_BAND, band);
        return tag;
    }

    private static String key(Component line) {
        return ((TranslatableContents) line.getContents()).getKey();
    }

    private static Object[] args(Component line) {
        return ((TranslatableContents) line.getContents()).getArgs();
    }

    @Test
    void noPresenceFlagYieldsNoLines() {
        assertTrue(FarmlandProbeTooltip.buildLines(new CompoundTag()).isEmpty());
    }

    @Test
    void bareTagShowsOnlyTheFertilityLine() {
        List<Component> lines = FarmlandProbeTooltip.buildLines(base("FAIR", 62.4F));
        assertEquals(1, lines.size());
        assertEquals("tooltip.cultivation.soil.fertility", key(lines.get(0)));
        assertEquals(62, args(lines.get(0))[0]); // whole percent, matching /cultivation soil
        assertEquals("tooltip.cultivation.soil.band.fair",
                key((Component) args(lines.get(0))[1]));
    }

    @Test
    void fullTagShowsEveryLineInOrder() {
        CompoundTag tag = base("RICH", 90.0F);
        tag.putInt(FarmlandProbeTooltip.KEY_ENRICHED, 10);
        tag.putInt(FarmlandProbeTooltip.KEY_FERTILIZER, 5);
        tag.putInt(FarmlandProbeTooltip.KEY_DOSE, 15);
        tag.putString(FarmlandProbeTooltip.KEY_LAST_CROP, "minecraft:wheat");

        List<Component> lines = FarmlandProbeTooltip.buildLines(tag);
        assertEquals(4, lines.size());
        assertEquals("tooltip.cultivation.soil.enriched", key(lines.get(1)));
        assertEquals(10, args(lines.get(1))[0]);
        assertEquals("tooltip.cultivation.soil.fertilizer", key(lines.get(2)));
        assertEquals(5, args(lines.get(2))[0]);
        assertEquals(15, args(lines.get(2))[1]);
        assertEquals("tooltip.cultivation.soil.crop", key(lines.get(3)));
    }

    @Test
    void zeroInvestmentLinesAreOmitted() {
        CompoundTag tag = base("TIRED", 10.0F);
        tag.putInt(FarmlandProbeTooltip.KEY_ENRICHED, 0);
        tag.putInt(FarmlandProbeTooltip.KEY_FERTILIZER, 0);
        assertEquals(1, FarmlandProbeTooltip.buildLines(tag).size());
    }

    @Test
    void unknownBandFallsBackToRawToken() {
        List<Component> lines = FarmlandProbeTooltip.buildLines(base("BOGUS", 50.0F));
        assertEquals("BOGUS", ((Component) args(lines.get(0))[1]).getString());
    }

    @Test
    void unresolvableLastCropFallsBackToRawId() {
        CompoundTag tag = base("RICH", 90.0F);
        tag.putString(FarmlandProbeTooltip.KEY_LAST_CROP, "bad id with spaces");
        List<Component> lines = FarmlandProbeTooltip.buildLines(tag);
        Component crop = (Component) args(lines.get(lines.size() - 1))[0];
        assertEquals("bad id with spaces", crop.getString());
        assertFalse(lines.isEmpty());
    }
}
