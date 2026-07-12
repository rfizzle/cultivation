package com.rfizzle.cultivation.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the config-screen contract: {@link ConfigFields#ALL} must cover every
 * tunable {@link CultivationConfig} field. A new config field added without a
 * matching {@link ConfigFields.Spec} fails here, before it can ship an option
 * the ModMenu screen silently omits.
 */
class ConfigFieldsTest {

    /** Every public, non-static config field except the non-tunable schema version. */
    private static Set<String> tunableConfigFields() {
        Set<String> names = new TreeSet<>();
        for (Field field : CultivationConfig.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (field.getName().equals("configVersion")) {
                continue;
            }
            names.add(field.getName());
        }
        return names;
    }

    @Test
    void everyTunableFieldHasExactlyOneSpec() {
        Set<String> specced = new TreeSet<>();
        for (ConfigFields.Spec spec : ConfigFields.ALL) {
            assertTrue(specced.add(spec.field()), "duplicate spec for field " + spec.field());
        }
        assertEquals(tunableConfigFields(), specced,
                "ConfigFields.ALL must cover exactly the tunable CultivationConfig fields");
    }

    @Test
    void everySpecCategoryIsDeclared() {
        Set<String> declared = new HashSet<>(ConfigFields.CATEGORIES);
        for (ConfigFields.Spec spec : ConfigFields.ALL) {
            assertTrue(declared.contains(spec.category()),
                    "spec " + spec.field() + " uses undeclared category " + spec.category());
        }
    }

    @Test
    void numericBoundsAreOrdered() {
        for (ConfigFields.Spec spec : ConfigFields.ALL) {
            if (spec.kind() != ConfigFields.Kind.BOOL) {
                assertTrue(spec.min() <= spec.max(),
                        "spec " + spec.field() + " has min > max");
            }
        }
    }

    @Test
    void snakeCaseMatchesLangConvention() {
        assertEquals("harvest_drain", ConfigFields.snakeCase("harvestDrain"));
        assertEquals("enable_soil_fertility", ConfigFields.snakeCase("enableSoilFertility"));
        // Every category actually carries at least one field, so no empty tab renders.
        Set<String> used = ConfigFields.ALL.stream()
                .map(ConfigFields.Spec::category).collect(Collectors.toCollection(TreeSet::new));
        assertEquals(new TreeSet<>(ConfigFields.CATEGORIES), used);
    }
}
