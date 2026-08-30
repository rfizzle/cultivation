package com.rfizzle.cultivation.data;

import com.rfizzle.cultivation.Cultivation;
import com.rfizzle.cultivation.item.CultivationItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Cultivation's four recipes ({@code design/SPEC.md} §7): the iron and diamond scythes,
 * the iron rake, and the netherite scythe's smithing upgrade. Fertilizer has no recipe —
 * it comes out of a composter (§6), not a crafting grid.
 *
 * <p>Written through {@link RecipeOutput#accept} with a {@code null} advancement rather
 * than through {@code ShapedRecipeBuilder}, because the builder's {@code save} also emits
 * a recipe-unlock advancement and Cultivation has never shipped one. Adding those would
 * be a live gameplay change — four recipe-book unlocks and their toasts where there were
 * none — smuggled in under a datagen conversion. The builder cannot be used without it:
 * {@code ensureValid} rejects a recipe with no unlock criterion outright.
 *
 * <p>Each ingredient key is a {@link LinkedHashMap} rather than {@link Map#of}, which
 * randomizes its iteration order per JVM. That would make the emitted key block differ
 * between two runs and fail {@code verifyDatagenIdempotent} at random rather than
 * reproducibly, which is the worst way for a check like that to fail.
 */
public class CultivationRecipeProvider extends FabricRecipeProvider {

    public CultivationRecipeProvider(FabricDataOutput output,
                                     CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(output, registryLookup);
    }

    @Override
    public void buildRecipes(RecipeOutput exporter) {
        scythe(exporter, "iron_scythe", Items.IRON_INGOT, CultivationItems.IRON_SCYTHE);
        scythe(exporter, "diamond_scythe", Items.DIAMOND, CultivationItems.DIAMOND_SCYTHE);

        Map<Character, Ingredient> rakeKey = new LinkedHashMap<>();
        rakeKey.put('I', Ingredient.of(Items.IRON_INGOT));
        rakeKey.put('S', Ingredient.of(Items.STICK));
        exporter.accept(
                Cultivation.id("iron_rake"),
                new ShapedRecipe("", CraftingBookCategory.EQUIPMENT,
                        ShapedRecipePattern.of(rakeKey, List.of(
                                "III",
                                " S ",
                                " S ")),
                        new ItemStack(CultivationItems.IRON_RAKE)),
                null);

        // The netherite scythe upgrades from the diamond one, exactly as vanilla's netherite
        // tools do — so it inherits the diamond scythe's enchantments and damage rather than
        // being crafted fresh.
        exporter.accept(
                Cultivation.id("netherite_scythe"),
                new SmithingTransformRecipe(
                        Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                        Ingredient.of(CultivationItems.DIAMOND_SCYTHE),
                        Ingredient.of(Items.NETHERITE_INGOT),
                        new ItemStack(CultivationItems.NETHERITE_SCYTHE)),
                null);
    }

    /** The shared scythe shape: two ingots over a hafted blade, one tier's ingot per scythe. */
    private static void scythe(RecipeOutput exporter, String name, Item ingot, Item result) {
        Map<Character, Ingredient> key = new LinkedHashMap<>();
        key.put('I', Ingredient.of(ingot));
        key.put('S', Ingredient.of(Items.STICK));
        exporter.accept(
                Cultivation.id(name),
                new ShapedRecipe("", CraftingBookCategory.EQUIPMENT,
                        ShapedRecipePattern.of(key, List.of(
                                " II",
                                "IS ",
                                " S ")),
                        new ItemStack(result)),
                null);
    }

    @Override
    public String getName() {
        return "Cultivation Recipes";
    }
}
