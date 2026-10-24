package com.brandon3055.draconicevolution.gametest;

// WP-31 - see DEGameTestUtil header. Spec: specs/WP-31-gametests.md, tests G1-G3.

import com.brandon3055.brandonscore.multiblock.MultiBlockManager;
import com.brandon3055.draconicevolution.api.DraconicAPI;
import com.brandon3055.draconicevolution.api.crafting.IngredientStack;
import com.brandon3055.draconicevolution.init.DEContent;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.brandon3055.draconicevolution.gametest.DEGameTestUtil.*;

@GameTestHolder(DEGameTestUtil.MODID)
@PrefixGameTestTemplate(false)
public class RegistryGameTests {
    static {
        DEGameTestUtil.installReporter();
    }

    /**
     * G1: every registry id of the 1.20.1 reference (minus allow-port.txt registry rules and client-only entries)
     * exists at runtime; also writes deport-out/registry-dump.json (refdiff-registry/1) for
     * `refdiff.py diff ref-manifests/de-1.20.registry.json run/gametest/deport-out/registry-dump.json --allow allow-port.txt`.
     */
    @GameTest(template = T_3, batch = BATCH_DATA)
    public static void registryIdsMatchReference(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        JsonObject expected = GsonHelper.getAsJsonObject(expectations(server), "registries");
        List<String> missing = new ArrayList<>();
        List<String> extra = new ArrayList<>();

        JsonObject dumpRegs = obj();
        for (Map.Entry<String, JsonElement> e : expected.entrySet()) {
            String regId = e.getKey();
            Set<String> actual = new TreeSet<>();
            if (regId.equals("forge:ingredient_serializer")) {
                // Not a registry in Forge 43: CraftingHelper keeps a BiMap [V CraftingHelper.java:51,61,71]
                ResourceLocation id = CraftingHelper.getID(IngredientStack.SERIALIZER);
                if (id != null) actual.add(id.toString());
            } else {
                ForgeRegistry<?> reg = RegistryManager.ACTIVE.getRegistry(new ResourceLocation(regId)); // [V RegistryManager.java:69]
                if (reg == null) {
                    missing.add(regId + " (whole registry absent)");
                    continue;
                }
                for (ResourceLocation key : reg.getKeys()) {                                              // [V IForgeRegistry.java:48]
                    if (key.getNamespace().equals(MODID)) actual.add(key.toString());
                }
            }
            Set<String> want = new TreeSet<>();
            for (JsonElement id : e.getValue().getAsJsonArray()) want.add(id.getAsString());
            for (String id : want) if (!actual.contains(id)) missing.add(regId + " " + id);
            for (String id : actual) if (!want.contains(id)) extra.add(regId + " " + id);

            JsonObject ids = obj();
            actual.forEach(id -> ids.add(id, obj()));
            JsonObject block = obj();
            block.addProperty("count", actual.size());
            block.add("ids", ids);
            dumpRegs.add(regId, block);
        }

        JsonObject source = obj();
        source.addProperty("kind", "runtime");
        source.addProperty("label", "WP-31 gametest server dump");
        JsonObject dump = obj();
        dump.addProperty("schema", "refdiff-registry/1");
        dump.add("source", source);
        dump.add("registries", dumpRegs);
        writeJson("registry-dump.json", dump);

        if (!extra.isEmpty()) {
            LOGGER.warn("DEPORT-GATE registry.extra={} ({})", extra.size(), head(extra)); // reviewed through refdiff + allow-port.txt
        }
        check(missing.isEmpty(), () -> missing.size() + " registry ids missing: " + head(missing));
        helper.succeed();
    }

    /** G2: BC MultiBlockManager loaded all 8 DE energy-core definitions from data/draconicevolution/multiblocks. */
    @GameTest(template = T_3, batch = BATCH_DATA)
    public static void multiblockDefinitionsLoaded(GameTestHelper helper) {
        JsonArray ids = GsonHelper.getAsJsonArray(expectations(helper.getLevel().getServer()), "multiblocks");
        List<String> missing = new ArrayList<>();
        for (JsonElement id : ids) {
            if (MultiBlockManager.getDefinition(new ResourceLocation(id.getAsString())) == null) missing.add(id.getAsString());
        }
        check(ids.size() == 8, () -> "expectations list " + ids.size() + " multiblocks, reference has 8");
        check(missing.isEmpty(), () -> "multiblock definitions not loaded: " + head(missing));
        helper.succeed();
    }

    /** G3: @ObjectHolder(registryName = "block", ...) in DraconicAPI is populated on Forge 43 [V ObjectHolder.java:30,38]. */
    @GameTest(template = T_3, batch = BATCH_DATA)
    public static void objectHoldersResolved(GameTestHelper helper) {
        check(DraconicAPI.CRAFTING_CORE != null, () -> "DraconicAPI.CRAFTING_CORE @ObjectHolder is null");
        check(DraconicAPI.CRAFTING_CORE == DEContent.CRAFTING_CORE.get(), () -> "DraconicAPI.CRAFTING_CORE != DEContent.CRAFTING_CORE");
        check(DraconicAPI.FUSION_RECIPE_TYPE != null && DraconicAPI.FUSION_RECIPE_TYPE.isPresent(), () -> "FUSION_RECIPE_TYPE not registered");
        check(DraconicAPI.FUSION_RECIPE_SERIALIZER != null && DraconicAPI.FUSION_RECIPE_SERIALIZER.isPresent(), () -> "FUSION_RECIPE_SERIALIZER not registered");
        helper.succeed();
    }
}
