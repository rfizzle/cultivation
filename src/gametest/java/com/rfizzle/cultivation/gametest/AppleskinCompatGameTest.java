package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.compat.appleskin.AppleskinCompat;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The AppleSkin compat entry point with the target mod absent — AppleSkin is not on
 * the gametest classpath, so this is the un-integrated default path every player
 * without it takes. Covers the real seam {@code DietTooltip} calls, including the
 * loader lookup the pure unit tests deliberately never reach.
 */
public class AppleskinCompatGameTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void absentAppleskinLeavesTheNutritionLineToUs(GameTestHelper helper) {
        if (AppleskinCompat.isPresent()) {
            helper.fail("AppleSkin is not a gametest dependency, so it must not resolve as present");
        }
        if (!AppleskinCompat.showsNutritionLine(true)) {
            helper.fail("with AppleSkin absent and the setting on, the nutrition line must render");
        }
        if (AppleskinCompat.showsNutritionLine(false)) {
            helper.fail("the showNutritionTooltips opt-out must still suppress the line");
        }
        helper.succeed();
    }
}
