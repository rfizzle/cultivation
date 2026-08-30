package com.rfizzle.cultivation.data;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * Cultivation's datagen entrypoint — the first of the four anchors {@code mc-datagen}
 * requires, and the one this repo was missing. The run config, the make target and
 * {@code verifyDatagenIdempotent} were all already wired, which is the state the skill
 * calls "the worst of the four: it looks the most wired and proves the least" —
 * {@code runDatagen} started a server, generated nothing, and exited 0, and the verify
 * task then found no changes in a directory that did not exist.
 *
 * <p>Everything Cultivation ships that a vanilla provider can express is generated
 * here: five item models, four recipes, five advancements, and four item tags. Nothing
 * is left hand-authored under {@code src/main/resources} in those four categories, so
 * a drift between the code and the data is a failing build rather than a silent
 * mismatch.
 *
 * <p>The mod registers no blocks at all — soil condition renders as a client-side
 * overlay over vanilla farmland — so there are no blockstates and no loot tables to
 * generate, and the {@code random_sequence} stamping the skill requires of a Fabric
 * loot provider does not arise here.
 */
public class CultivationDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        FabricDataGenerator.Pack pack = generator.createPack();
        pack.addProvider(CultivationModelProvider::new);
        pack.addProvider(CultivationRecipeProvider::new);
        pack.addProvider(CultivationAdvancementProvider::new);
        pack.addProvider(CultivationItemTagProvider::new);
    }
}
