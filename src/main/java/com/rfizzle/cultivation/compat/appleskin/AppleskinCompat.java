package com.rfizzle.cultivation.compat.appleskin;

import net.fabricmc.loader.api.FabricLoader;

/**
 * AppleSkin integration seam. AppleSkin already draws hunger and saturation shanks
 * under food tooltips, so when it is present Cultivation defers its own nutrition
 * line rather than double-printing the same numbers. The fatigue line has no
 * AppleSkin counterpart and is never suppressed.
 *
 * <p>Detection only — Cultivation references no AppleSkin type and declares no
 * dependency on it, so nothing here needs a compile surface. Should the
 * integration ever grow past presence (reading AppleSkin's display settings,
 * rendering into its tooltip surface), the foreign references belong in this
 * package behind {@code modCompileOnly} and the guard below, per the Concord API
 * Standard's soft-dependency rules.
 */
public final class AppleskinCompat {
    private static final String MOD_ID = "appleskin";

    private AppleskinCompat() {
    }

    /** Whether AppleSkin is loaded in this session. */
    public static boolean isPresent() {
        return Loaded.PRESENT;
    }

    /**
     * Whether the nutrition tooltip line should render, given the player's
     * {@code showNutritionTooltips} setting.
     */
    public static boolean showsNutritionLine(boolean configEnabled) {
        return showsNutritionLine(configEnabled, isPresent());
    }

    /**
     * The suppression rule itself, with presence supplied rather than resolved —
     * pure, so it carries the unit coverage for the decision.
     */
    static boolean showsNutritionLine(boolean configEnabled, boolean appleskinPresent) {
        return configEnabled && !appleskinPresent;
    }

    /**
     * Confines the loader lookup to its own class so the enclosing class stays
     * inert at init: the pure rule above is then callable outside a Fabric
     * environment, which is what keeps it unit-testable.
     *
     * <p>Resolved once — the loaded mod set is fixed for the session.
     */
    private static final class Loaded {
        static final boolean PRESENT = FabricLoader.getInstance().isModLoaded(MOD_ID);

        private Loaded() {
        }
    }
}
