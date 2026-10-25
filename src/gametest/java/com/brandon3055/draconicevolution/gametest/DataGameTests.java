package com.brandon3055.draconicevolution.gametest;

// WP-31 - see DEGameTestUtil header. Spec: specs/WP-31-gametests.md, tests G4-G6 and G13.

import com.brandon3055.draconicevolution.api.crafting.IFusionRecipe;
import com.brandon3055.draconicevolution.api.crafting.IngredientStack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootTables;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.brandon3055.draconicevolution.gametest.DEGameTestUtil.*;

@GameTestHolder(DEGameTestUtil.MODID)
@PrefixGameTestTemplate(false)
public class DataGameTests {
    static {
        DEGameTestUtil.installReporter();
    }

    /**
     * G4: all 179 DE recipes of the 1.20.1 reference load, keep their serializer, produce the same result item/count,
     * and every non-empty ingredient resolves to at least one item (catches missing/renamed tags on 1.19.2).
     */
    @GameTest(template = T_3, batch = BATCH_DATA)
    public static void recipesResolveWithResults(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        JsonObject rec = GsonHelper.getAsJsonObject(expectations(server), "recipes");
        JsonObject ids = GsonHelper.getAsJsonObject(rec, "ids");
        RecipeManager rm = server.getRecipeManager();                                   // [V MinecraftServer.java:1401]
        List<String> problems = new ArrayList<>();
        Map<String, Integer> byType = new TreeMap<>();
        Set<String> stackCatalysts = new TreeSet<>();
        int fusionChecked = 0;

        for (Map.Entry<String, JsonElement> e : ids.entrySet()) {
            JsonObject want = e.getValue().getAsJsonObject();
            Optional<? extends Recipe<?>> opt = rm.byKey(new ResourceLocation(e.getKey()));   // [V RecipeManager.java:145]
            if (opt.isEmpty()) {
                problems.add(e.getKey() + ": not loaded");
                continue;
            }
            Recipe<?> recipe = opt.get();
            ResourceLocation ser = ForgeRegistries.RECIPE_SERIALIZERS.getKey(recipe.getSerializer());
            String serId = String.valueOf(ser);
            byType.merge(serId, 1, Integer::sum);
            if (!serId.equals(GsonHelper.getAsString(want, "type", ""))) {
                problems.add(e.getKey() + ": serializer " + serId + " != " + GsonHelper.getAsString(want, "type", "?"));
            }
            ItemStack out = recipe.getResultItem();                                             // [V Recipe.java:18, 1.19.2 has no RegistryAccess arg]
            if (out.isEmpty()) {
                problems.add(e.getKey() + ": empty result");
            } else {
                String outId = String.valueOf(ForgeRegistries.ITEMS.getKey(out.getItem()));
                String wantId = want.has("result") && !want.get("result").isJsonNull() ? want.get("result").getAsString() : null;
                int wantCount = want.has("count") && !want.get("count").isJsonNull() ? want.get("count").getAsInt() : 1;
                if (wantId != null && !wantId.equals(outId)) problems.add(e.getKey() + ": result " + outId + " != " + wantId);
                if (out.getCount() != wantCount) problems.add(e.getKey() + ": count " + out.getCount() + " != " + wantCount);
            }
            int i = 0;
            for (Ingredient ing : recipe.getIngredients()) {
                // ShapedRecipe uses Ingredient.EMPTY for blank pattern cells [V Ingredient.java:38]
                if (ing != Ingredient.EMPTY && ing.getItems().length == 0) {                   // [V Ingredient.java:52]
                    problems.add(e.getKey() + ": ingredient #" + i + " matches no items");
                }
                i++;
            }
            // FusionRecipe.getIngredients() excludes the catalyst (spec G4 verifier gap): resolve it separately.
            if (recipe instanceof IFusionRecipe fusion) {
                fusionChecked++;
                Ingredient cat = fusion.getCatalyst();
                if (cat == null || cat.getItems().length == 0) {
                    problems.add(e.getKey() + ": fusion catalyst matches no items");
                }
                if (cat instanceof IngredientStack stack) {
                    stackCatalysts.add(e.getKey());
                    if (stack.getCount() != 4) problems.add(e.getKey() + ": ingredient_stack catalyst count " + stack.getCount() + " != 4");
                }
            }
        }
        // Reference: exactly 2 recipes use a draconicevolution:ingredient_stack catalyst (count 4 each).
        Set<String> wantStack = Set.of("draconicevolution:awakened_draconium_block", "draconicevolution:machines/draconic_relay_crystal");
        if (!stackCatalysts.equals(wantStack)) problems.add("ingredient_stack catalysts " + stackCatalysts + " != " + wantStack);
        int fusionN = fusionChecked;
        if (fusionN != 39) problems.add("fusion recipes with catalyst checked: " + fusionN + " != 39");

        List<ResourceLocation> deIds = rm.getRecipeIds()                                       // [V RecipeManager.java:155]
                .filter(id -> id.getNamespace().equals(MODID))
                .filter(id -> !CosmeticArmorGameTests.isCosmeticRecipe(id)) // [1.19.2 port addition], see CosmeticArmorGameTests
                .toList();
        for (ResourceLocation id : deIds) {
            if (!ids.has(id.toString())) problems.add(id + ": extra recipe not in the 1.20.1 reference");
        }
        int wantTotal = GsonHelper.getAsInt(rec, "count");
        int total = deIds.size();
        LOGGER.info("DEPORT-GATE recipes.draconicevolution={} by_serializer={}", total, byType);
        check(total == wantTotal, () -> "DE recipe count " + total + " != reference " + wantTotal);
        JsonObject wantByType = GsonHelper.getAsJsonObject(rec, "by_type");
        for (Map.Entry<String, JsonElement> e : wantByType.entrySet()) {
            int got = byType.getOrDefault(e.getKey(), 0);
            if (got != e.getValue().getAsInt()) problems.add("type " + e.getKey() + ": " + got + " != " + e.getValue().getAsInt());
        }
        check(problems.isEmpty(), () -> problems.size() + " recipe problems: " + head(problems));
        helper.succeed();
    }

    /**
     * G5: every DE block has a loot table that exists, and its drops are non-empty, except the 6 blocks whose table is
     * empty in the 1.20.1 reference as well (expectations.loot.expected_empty).
     */
    @GameTest(template = T_3, batch = BATCH_DATA)
    public static void blockLootTables(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        LootTables tables = server.getLootTables();                                             // [V MinecraftServer.java:1417]
        Set<ResourceLocation> known = tables.getIds();                                          // [V LootTables.java:68]
        Set<String> expectedEmpty = new HashSet<>();
        GsonHelper.getAsJsonArray(GsonHelper.getAsJsonObject(expectations(server), "loot"), "expected_empty")
                .forEach(e -> expectedEmpty.add(e.getAsString()));
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Block block : ForgeRegistries.BLOCKS.getValues()) {
            ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
            if (key == null || !key.getNamespace().equals(MODID)) continue;
            checked++;
            ResourceLocation lt = block.getLootTable();                                          // [V BlockBehaviour.java:396]
            LootTable table = tables.get(lt);                                                    // [V LootTables.java:27-28 -> LootTable.EMPTY if absent]
            if (!known.contains(lt) || table == LootTable.EMPTY) {
                problems.add(key + ": loot table " + lt + " missing");
                continue;
            }
            List<ItemStack> drops = Block.getDrops(block.defaultBlockState(), level, at, null, null, tool); // [V Block.java:260]
            boolean shouldBeEmpty = expectedEmpty.contains(key.getPath());
            if (shouldBeEmpty && !drops.isEmpty()) problems.add(key + ": drops " + drops + " but the 1.20.1 table is empty");
            if (!shouldBeEmpty && drops.isEmpty()) problems.add(key + ": no drops");
        }
        int blocks = GsonHelper.getAsInt(GsonHelper.getAsJsonObject(expectations(server), "loot"), "blocks_with_table");
        int n = checked;
        check(n == blocks, () -> "checked " + n + " DE blocks, reference has " + blocks);
        check(problems.isEmpty(), () -> problems.size() + " loot problems: " + head(problems));
        helper.succeed();
    }

    /**
     * G13: the 5 configured features, 5 placed features and 5 forge:add_features biome modifiers decoded into the
     * server's datapack registries (a decode error would normally abort world load; this pins the ids).
     */
    @GameTest(template = T_3, batch = BATCH_DATA)
    public static void worldgenDatapackEntriesLoaded(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        JsonObject regs = GsonHelper.getAsJsonObject(expectations(server), "datapack_registries");
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : regs.entrySet()) {
            ResourceKey<Registry<Object>> key = ResourceKey.createRegistryKey(new ResourceLocation(e.getKey()));
            Optional<? extends Registry<Object>> reg = server.registryAccess().registry(key);    // [V RegistryAccess.java:80]
            if (reg.isEmpty()) {
                problems.add(e.getKey() + ": datapack registry absent");
                continue;
            }
            for (JsonElement id : e.getValue().getAsJsonArray()) {
                if (!reg.get().containsKey(new ResourceLocation(id.getAsString()))) problems.add(e.getKey() + " " + id.getAsString());
            }
        }
        check(problems.isEmpty(), () -> problems.size() + " worldgen entries missing: " + head(problems));
        helper.succeed();
    }

    /** G6: every item/block tag DE ships (43 in the reference) is known and non-empty after the 1.19.2 tag load. */
    @GameTest(template = T_3, batch = BATCH_DATA)
    public static void shippedTagsResolve(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(expectations(helper.getLevel().getServer()), "tags")) {
            String[] parts = e.getAsString().split("\\|", 2);   // "<registry folder>|<ns:path>"
            ResourceLocation id = new ResourceLocation(parts[1]);
            boolean empty;
            switch (parts[0]) {
                case "items" -> empty = ForgeRegistries.ITEMS.tags().getTag(ItemTags.create(id)).isEmpty();    // [V IForgeRegistry.java:78, ITagManager.java:32, ITag.java:29, ItemTags.java:93]
                case "blocks" -> empty = ForgeRegistries.BLOCKS.tags().getTag(BlockTags.create(id)).isEmpty(); // [V BlockTags.java:168]
                default -> {
                    LOGGER.info("DEPORT-GATE tag.skipped={}", e.getAsString());
                    continue;
                }
            }
            if (empty) problems.add(e.getAsString() + " is empty/unknown");
        }
        check(problems.isEmpty(), () -> problems.size() + " tag problems: " + head(problems));
        helper.succeed();
    }
}
