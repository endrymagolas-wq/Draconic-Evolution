package com.brandon3055.draconicevolution.gametest;

// WP-31 extended GameTests (optional, batch de_ext): specs/WP-23-tabs-materials.md §4.2 T1-T7 (creative tabs and
// block properties are common code, so they run on the gametest server). T8/T9 are server-log / command checks
// (done by WP-30's boot gate, logs/WP-30.md 3b/3e). Expectations: data/draconicevolution/gametest/expectations-ext.json,
// generated from the spec by tools/refdiff/mkexpect_ext.py (Appendix A tab lists, §2.3 "1.19.2 props" column).

import com.brandon3055.draconicevolution.init.DECreativeTabs;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.brandon3055.draconicevolution.gametest.DEGameTestUtil.*;

@GameTestHolder(DEGameTestUtil.MODID)
@PrefixGameTestTemplate(false)
public class ContentGameTests {
    static {
        DEGameTestUtil.installReporter();
    }

    private static final ResourceLocation EXT = new ResourceLocation(MODID, "gametest/expectations-ext.json");
    private static JsonObject ext;

    private static synchronized JsonObject ext(MinecraftServer server) {
        if (ext == null) {
            Resource res = server.getResourceManager().getResource(EXT).orElseThrow(() -> new GameTestAssertException("missing " + EXT + " (python -I tools/refdiff/mkexpect_ext.py --stage)"));
            try (BufferedReader reader = res.openAsReader()) {
                ext = GsonHelper.parse(reader);
            } catch (IOException e) {
                throw new GameTestAssertException("cannot read " + EXT + ": " + e);
            }
            check("wp31-ext-expect/1".equals(GsonHelper.getAsString(ext, "schema", "")), () -> "unexpected schema in " + EXT);
        }
        return ext;
    }

    private static List<String> list(JsonObject tabs, String tab, String order) {
        List<String> out = new ArrayList<>();
        GsonHelper.getAsJsonArray(GsonHelper.getAsJsonObject(tabs, tab), order).forEach(e -> out.add(e.getAsString()));
        return out;
    }

    private static Map<String, CreativeModeTab> deTabs() {
        Map<String, CreativeModeTab> m = new LinkedHashMap<>();
        m.put("blocks", DECreativeTabs.BLOCKS);
        m.put("items", DECreativeTabs.ITEMS);
        m.put("modules", DECreativeTabs.MODULES);
        return m;
    }

    private static void finish(GameTestHelper helper, String test, List<String> problems) {
        check(problems.isEmpty(), () -> problems.size() + " " + test + " problems: " + head(problems));
        helper.succeed();
    }

    /**
     * T1-T3: each DE tab's fillItemList gives 45/62/71 plain single stacks (no NBT), the Appendix A id sets, in the
     * namespace-filtered registry order; logs whether that is the registration or the snapshot order.
     */
    @GameTest(template = T_3, batch = BATCH_EXT, required = false)
    public static void creativeTabContents(GameTestHelper helper) {
        JsonObject tabs = GsonHelper.getAsJsonObject(ext(helper.getLevel().getServer()), "tabs");
        List<String> p = new ArrayList<>();
        List<String> defaultTagged = new ArrayList<>();
        for (Map.Entry<String, CreativeModeTab> e : deTabs().entrySet()) {
            String tab = e.getKey();
            NonNullList<ItemStack> stacks = NonNullList.create();
            e.getValue().fillItemList(stacks);
            List<String> ids = new ArrayList<>();
            for (ItemStack s : stacks) {
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(s.getItem());
                String id = key == null ? "?" : key.getNamespace().equals(MODID) ? key.getPath() : key.toString();
                ids.add(id);
                if (s.isEmpty()) p.add(tab + ": empty stack");
                // Spec T1 says "no stack with NBT". The intent (D2) is one *default* stack per entry: a damageable item's
                // default stack carries {Damage:0}, written by the ItemStack constructor itself (1.19.2 [V ItemStack.java:149]
                // isDamageable -> setDamageValue -> IForgeItem.setDamage; long-standing vanilla code, same in 1.20.1 [I]).
                // So compare the tag with a freshly constructed stack's tag. Not ItemStack.matches/isSameItemSameTags: in
                // Forge 1.19.2 both also call areCapsCompatible, and the capability NBT of two fresh modular items differs
                // (run-06/07: 27 false positives with both tags null).
                ItemStack fresh = new ItemStack(s.getItem());
                if (!Objects.equals(s.getTag(), fresh.getTag())) p.add(tab + ": " + id + " is not a default stack: " + s.getTag() + " vs " + fresh.getTag());
                else if (s.hasTag()) defaultTagged.add(id + s.getTag());
                if (s.getCount() != 1) p.add(tab + ": " + id + " count " + s.getCount());
            }
            List<String> reg = list(tabs, tab, "registration");
            List<String> snap = list(tabs, tab, "snapshot");
            if (ids.size() != reg.size()) p.add(tab + ": " + ids.size() + " stacks, expected " + reg.size());
            Set<String> got = new HashSet<>(ids);
            if (!got.equals(new HashSet<>(reg))) {                                                       // T2
                Set<String> missing = new HashSet<>(reg);
                missing.removeAll(got);
                Set<String> extra = new HashSet<>(got);
                extra.removeAll(reg);
                p.add(tab + ": id set differs, missing=" + missing + " extra=" + extra);
            }
            // T3: registry iteration order at call time (blocks tab walks BLOCKS, the others walk ITEMS)
            List<String> registryOrder = new ArrayList<>();
            for (ResourceLocation key : tab.equals("blocks") ? ForgeRegistries.BLOCKS.getKeys() : ForgeRegistries.ITEMS.getKeys()) {
                if (key.getNamespace().equals(MODID) && got.contains(key.getPath())) registryOrder.add(key.getPath());
            }
            if (!ids.equals(registryOrder)) p.add(tab + ": order differs from the registry key order");
            String which = ids.equals(reg) ? "registration" : ids.equals(snap) ? "snapshot" : "NEITHER";
            LOGGER.info("DEPORT-GATE tab.{} size={} order={}", tab, ids.size(), which);
            if (which.equals("NEITHER")) p.add(tab + ": order is neither Appendix A registration nor snapshot order");
        }
        LOGGER.info("DEPORT-GATE tab.default_stacks_with_tag={} {}", defaultTagged.size(), defaultTagged);
        finish(helper, "T1-T3", p);
    }

    /** T4 + T5: getItemCategory of every DE item, and the creative search tab (178 stacks, no structure_block). */
    @GameTest(template = T_3, batch = BATCH_EXT, required = false)
    public static void itemCategoriesAndSearchTab(GameTestHelper helper) {
        JsonObject tabs = GsonHelper.getAsJsonObject(ext(helper.getLevel().getServer()), "tabs");
        Map<String, CreativeModeTab> want = new LinkedHashMap<>();
        for (Map.Entry<String, CreativeModeTab> e : deTabs().entrySet()) {
            for (String id : list(tabs, e.getKey(), "registration")) want.put(id, e.getValue());
        }
        List<String> p = new ArrayList<>();
        int items = 0;
        NonNullList<ItemStack> search = NonNullList.create();
        for (ResourceLocation key : ForgeRegistries.ITEMS.getKeys()) {
            if (!key.getNamespace().equals(MODID)) continue;
            items++;
            Item item = Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(key));
            CreativeModeTab cat = item.getItemCategory();
            String id = key.getPath();
            if (id.equals("structure_block")) {
                if (cat != null) p.add("structure_block has category " + cat.getRecipeFolderName());
            } else if (!want.containsKey(id)) {
                p.add(id + ": not in any Appendix A tab list");
            } else if (cat != want.get(id)) {
                p.add(id + ": category " + (cat == null ? "null" : cat.getRecipeFolderName()) + " != " + want.get(id).getRecipeFolderName());
            }
            item.fillItemCategory(CreativeModeTab.TAB_SEARCH, search);                                // T5
        }
        int n = items;
        if (n != 179) p.add("DE items " + n + " != 179");
        long structure = search.stream().filter(s -> "structure_block".equals(Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(s.getItem())).getPath())).count();
        if (search.size() != 178) p.add("search tab: " + search.size() + " DE stacks != 178");
        if (structure != 0) p.add("search tab contains structure_block");
        LOGGER.info("DEPORT-GATE tab.search.draconicevolution={}", search.size());
        finish(helper, "T4-T5", p);
    }

    /** T6: exactly 3 DE tabs in CreativeModeTab.TABS, ids blocks < items < modules, display keys itemGroup.draconicevolution.*. */
    @GameTest(template = T_3, batch = BATCH_EXT, required = false)
    public static void creativeTabRegistration(GameTestHelper helper) {
        List<String> p = new ArrayList<>();
        int de = 0;
        for (CreativeModeTab t : CreativeModeTab.TABS) {
            if (t != null && t.getRecipeFolderName().startsWith(MODID + ".")) de++;
        }
        int n = de;
        if (n != 3) p.add(n + " DE tabs in CreativeModeTab.TABS, expected 3");
        int last = -1;
        for (Map.Entry<String, CreativeModeTab> e : deTabs().entrySet()) {
            CreativeModeTab t = e.getValue();
            if (t.getId() <= last) p.add(e.getKey() + " id " + t.getId() + " not after " + last);
            last = t.getId();
            if (t.getId() < 0 || t.getId() >= CreativeModeTab.TABS.length || CreativeModeTab.TABS[t.getId()] != t) p.add(e.getKey() + ": TABS[" + t.getId() + "] is not this tab");
            String key = t.getDisplayName().getContents() instanceof TranslatableContents tc ? tc.getKey() : "?";
            if (!key.equals("itemGroup." + MODID + "." + e.getKey())) p.add(e.getKey() + ": display key " + key);
        }
        finish(helper, "T6", p);
    }

    private static final Map<String, Material> MATERIALS = Map.of("METAL", Material.METAL, "GLASS", Material.GLASS, "STONE", Material.STONE);
    private static final Map<String, MaterialColor> COLORS = Map.of("COLOR_GRAY", MaterialColor.COLOR_GRAY, "COLOR_BLACK", MaterialColor.COLOR_BLACK, "NONE", MaterialColor.NONE);
    private static final Map<String, SoundType> SOUNDS = Map.of("METAL", SoundType.METAL, "STONE", SoundType.STONE);

    private static void expect(List<String> p, String id, String what, Object got, Object want) {
        if (!Objects.equals(got, want)) p.add(id + "." + what + "=" + got + " (want " + want + ")");
    }

    /**
     * T7: block property parity with the 1.19.2 column of WP-23 §2.3 (material, map colour, sound, destroy time,
     * explosion resistance, occlusion, correct-tool requirement; collision only where noCollission() is expected).
     * ORE blocks are Properties.copy(Blocks.DIAMOND_ORE): compared with diamond ore itself.
     */
    @GameTest(template = T_3, batch = BATCH_EXT, required = false)
    public static void blockPropertyParity(GameTestHelper helper) {
        JsonObject props = GsonHelper.getAsJsonObject(ext(helper.getLevel().getServer()), "block_props");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        List<String> p = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<String, JsonElement> e : props.entrySet()) {
            String id = e.getKey();
            JsonObject w = e.getValue().getAsJsonObject();
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(MODID, id));
            if (block == null || !Objects.equals(ForgeRegistries.BLOCKS.getKey(block), new ResourceLocation(MODID, id))) {
                p.add(id + ": block not registered");
                continue;
            }
            checked++;
            BlockState s = block.defaultBlockState();
            if (w.has("copy")) {
                Block ref = Objects.requireNonNull(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(GsonHelper.getAsString(w, "copy"))));
                BlockState r = ref.defaultBlockState();
                expect(p, id, "material", s.getMaterial(), r.getMaterial());
                expect(p, id, "color", block.defaultMaterialColor(), ref.defaultMaterialColor());
                expect(p, id, "sound", s.getSoundType(), r.getSoundType());
                expect(p, id, "destroy", s.getDestroySpeed(helper.getLevel(), pos), r.getDestroySpeed(helper.getLevel(), pos));
                expect(p, id, "explosion", block.getExplosionResistance(), ref.getExplosionResistance());
                expect(p, id, "occlude", s.canOcclude(), r.canOcclude());
                expect(p, id, "requiresTool", s.requiresCorrectToolForDrops(), r.requiresCorrectToolForDrops());
                continue;
            }
            Material mat = MATERIALS.get(GsonHelper.getAsString(w, "material"));
            if (mat == null) {
                p.add(id + ": unmapped material " + GsonHelper.getAsString(w, "material"));
                continue;
            }
            expect(p, id, "material", s.getMaterial(), mat);
            MaterialColor color;
            if (w.has("dye")) color = DyeColor.valueOf(GsonHelper.getAsString(w, "dye")).getMaterialColor();
            else if (w.has("color") && !w.get("color").isJsonNull()) color = COLORS.get(GsonHelper.getAsString(w, "color"));
            else color = mat.getColor();
            expect(p, id, "color", block.defaultMaterialColor(), color);
            expect(p, id, "sound", s.getSoundType(), SOUNDS.get(GsonHelper.getAsString(w, "sound")));
            expect(p, id, "destroy", s.getDestroySpeed(helper.getLevel(), pos), GsonHelper.getAsFloat(w, "destroy"));
            expect(p, id, "explosion", block.getExplosionResistance(), GsonHelper.getAsFloat(w, "explosion"));
            expect(p, id, "occlude", s.canOcclude(), GsonHelper.getAsBoolean(w, "occlude"));
            expect(p, id, "requiresTool", s.requiresCorrectToolForDrops(), GsonHelper.getAsBoolean(w, "requires_tool"));
            expect(p, id, "isAir", s.isAir(), false);
            if (!GsonHelper.getAsBoolean(w, "collision")) {
                expect(p, id, "collisionShapeEmpty", s.getCollisionShape(helper.getLevel(), pos).isEmpty(), true);
            }
        }
        int n = checked;
        if (n != 51) p.add("checked " + n + " blocks, expected 51");
        finish(helper, "T7", p);
    }
}
