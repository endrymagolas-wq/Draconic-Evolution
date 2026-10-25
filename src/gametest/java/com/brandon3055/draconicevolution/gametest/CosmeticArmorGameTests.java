package com.brandon3055.draconicevolution.gametest;

// [1.19.2 port addition, not in upstream 1.20.1] GameTests of the cosmetic armor (DE PORT_NOTES.md, "Additions not in
// upstream 1.20.1: cosmetic armor"): registration, "no properties", recipes, creative-tab placement and that the pieces
// do not change anything about a modular-chestpiece wearer. Batch de_cosmetic, all required.

import com.brandon3055.draconicevolution.init.DEContent;
import com.brandon3055.draconicevolution.init.DECreativeTabs;
import com.brandon3055.draconicevolution.items.cosmetic.CosmeticArmor;
import com.brandon3055.draconicevolution.items.cosmetic.CosmeticArmorItem;
import com.brandon3055.draconicevolution.items.cosmetic.CosmeticArmorTier;
import com.brandon3055.draconicevolution.items.equipment.IModularArmor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.brandon3055.draconicevolution.gametest.DEGameTestUtil.*;

@GameTestHolder(DEGameTestUtil.MODID)
@PrefixGameTestTemplate(false)
public class CosmeticArmorGameTests {
    static {
        DEGameTestUtil.installReporter();
    }

    public static final String BATCH_COSMETIC = "de_cosmetic";
    public static final int COUNT = 9;
    public static final Set<String> IDS = Set.of(
            "wyvern_helmet", "wyvern_leggings", "wyvern_boots",
            "draconic_helmet", "draconic_leggings", "draconic_boots",
            "chaotic_helmet", "chaotic_leggings", "chaotic_boots");
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] SLOT_NAMES = {"helmet", "leggings", "boots"};
    private static final String[][] PATTERNS = {{"XXX", "X X"}, {"XXX", "X X", "X X"}, {"X X", "X X"}};

    /** Item path (without namespace) of a cosmetic armor item; used by the upstream-parity tests to skip them. */
    public static boolean isCosmeticId(String path) {
        return IDS.contains(path);
    }

    public static boolean isCosmeticRecipe(ResourceLocation id) {
        return id.getNamespace().equals(MODID) && id.getPath().startsWith("cosmetic/");
    }

    private static Item item(String path) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(MODID, path));
    }

    private static Item material(CosmeticArmorTier tier) {
        return switch (tier) {
            case WYVERN -> DEContent.INGOT_DRACONIUM.get();
            case DRACONIC -> DEContent.INGOT_DRACONIUM_AWAKENED.get();
            case CHAOTIC -> DEContent.CHAOS_FRAG_MEDIUM.get();
        };
    }

    private static void finish(GameTestHelper helper, String test, List<String> p) {
        LOGGER.info("DEPORT-GATE cosmetic.{} problems={}", test, p.size());
        check(p.isEmpty(), () -> p.size() + " " + test + " problems: " + head(p));
        helper.succeed();
    }

    /** The 9 ids are registered, are CosmeticArmorItems of the right tier and slot, and nothing else is one. */
    @GameTest(template = T_3, batch = BATCH_COSMETIC)
    public static void cosmeticArmorRegistered(GameTestHelper helper) {
        List<String> p = new ArrayList<>();
        for (CosmeticArmorTier tier : CosmeticArmorTier.values()) {
            for (int i = 0; i < SLOTS.length; i++) {
                String id = tier.id() + "_" + SLOT_NAMES[i];
                Item it = item(id);
                if (!(it instanceof CosmeticArmorItem c)) {
                    p.add(id + ": not registered as CosmeticArmorItem (" + it + ")");
                    continue;
                }
                if (c.getTier() != tier) p.add(id + ": tier " + c.getTier());
                if (c.getSlot() != SLOTS[i]) p.add(id + ": slot " + c.getSlot());
                if (c.getMaxStackSize(new ItemStack(c)) != 1) p.add(id + ": stack size " + c.getMaxStackSize(new ItemStack(c)));
                if (new ItemStack(c).getRarity() != tier.rarity) p.add(id + ": rarity " + new ItemStack(c).getRarity());
            }
        }
        int n = 0;
        for (ResourceLocation key : ForgeRegistries.ITEMS.getKeys()) {
            if (ForgeRegistries.ITEMS.getValue(key) instanceof CosmeticArmorItem) {
                n++;
                if (!key.getNamespace().equals(MODID) || !IDS.contains(key.getPath())) p.add("unexpected cosmetic item " + key);
            }
        }
        int count = n;
        if (count != COUNT) p.add(count + " cosmetic items, expected " + COUNT);
        if (CosmeticArmor.all().size() != COUNT) p.add("CosmeticArmor.all() size " + CosmeticArmor.all().size());
        finish(helper, "registered", p);
    }

    /** 0 armor / toughness / knockback resistance, no attribute modifiers in any slot, unbreakable, not enchantable or repairable. */
    @GameTest(template = T_3, batch = BATCH_COSMETIC)
    public static void cosmeticArmorHasNoProperties(GameTestHelper helper) {
        List<String> p = new ArrayList<>();
        for (Item it : CosmeticArmor.all()) {
            CosmeticArmorItem c = (CosmeticArmorItem) it;
            String id = String.valueOf(ForgeRegistries.ITEMS.getKey(c));
            ItemStack stack = new ItemStack(c);
            if (c.getDefense() != 0) p.add(id + ": defense " + c.getDefense());
            if (c.getToughness() != 0) p.add(id + ": toughness " + c.getToughness());
            if (c.getMaterial().getKnockbackResistance() != 0) p.add(id + ": knockback resistance " + c.getMaterial().getKnockbackResistance());
            for (EquipmentSlot s : EquipmentSlot.values()) {
                if (!c.getDefaultAttributeModifiers(s).isEmpty()) p.add(id + ": default modifiers in " + s + ": " + c.getDefaultAttributeModifiers(s));
                if (!stack.getAttributeModifiers(s).isEmpty()) p.add(id + ": stack modifiers in " + s + ": " + stack.getAttributeModifiers(s));
            }
            if (c.getMaxDamage(stack) != 0 || stack.isDamageableItem() || c.canBeDepleted()) p.add(id + ": damageable (max " + c.getMaxDamage(stack) + ")");
            stack.hurt(50, helper.getLevel().getRandom(), null);
            if (stack.getDamageValue() != 0 || stack.isEmpty()) p.add(id + ": took damage " + stack.getDamageValue());
            if (c.isEnchantable(stack) || stack.isEnchantable()) p.add(id + ": enchantable");
            if (c.getEnchantmentValue() != 0 || c.getMaterial().getEnchantmentValue() != 0) p.add(id + ": enchantment value " + c.getEnchantmentValue());
            if (c.canApplyAtEnchantingTable(stack, Enchantments.ALL_DAMAGE_PROTECTION)) p.add(id + ": enchanting table accepts protection");
            ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(Enchantments.ALL_DAMAGE_PROTECTION, 1));
            if (c.isBookEnchantable(stack, book)) p.add(id + ": book enchantable");
            if (!c.getMaterial().getRepairIngredient().isEmpty()) p.add(id + ": repair ingredient");
            if (c.isValidRepairItem(stack, new ItemStack(material(c.getTier())))) p.add(id + ": repairable with its material");
            if (c.isFoil(stack)) p.add(id + ": foil");
        }
        finish(helper, "noproperties", p);
    }

    /** cosmetic/<id> shaped recipes: vanilla armor shapes, result 1 item, ingredient = tier material; a real grid crafts it. */
    @GameTest(template = T_3, batch = BATCH_COSMETIC)
    public static void cosmeticArmorRecipesResolve(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<String> p = new ArrayList<>();
        int loaded = 0;
        for (CosmeticArmorTier tier : CosmeticArmorTier.values()) {
            Item mat = material(tier);
            for (int i = 0; i < SLOTS.length; i++) {
                String id = tier.id() + "_" + SLOT_NAMES[i];
                ResourceLocation rid = new ResourceLocation(MODID, "cosmetic/" + id);
                Optional<? extends Recipe<?>> opt = server.getRecipeManager().byKey(rid);
                if (opt.isEmpty() || !(opt.get() instanceof ShapedRecipe shaped)) {
                    p.add(rid + ": not loaded as shaped recipe");
                    continue;
                }
                loaded++;
                ItemStack out = shaped.getResultItem();
                if (out.getItem() != item(id) || out.getCount() != 1) p.add(rid + ": result " + out);
                String[] pattern = PATTERNS[i];
                if (shaped.getWidth() != 3 || shaped.getHeight() != pattern.length) p.add(rid + ": size " + shaped.getWidth() + "x" + shaped.getHeight());
                int filled = 0;
                for (Ingredient ing : shaped.getIngredients()) {
                    if (ing.isEmpty()) continue;
                    filled++;
                    if (!ing.test(new ItemStack(mat))) p.add(rid + ": ingredient does not accept " + ForgeRegistries.ITEMS.getKey(mat));
                    if (ing.getItems().length == 0) p.add(rid + ": ingredient matches no items");
                }
                int want = 0;
                for (String row : pattern) for (char ch : row.toCharArray()) if (ch == 'X') want++;
                if (filled != want) p.add(rid + ": " + filled + " ingredients, expected " + want);

                // real crafting grid with the materials laid out like the pattern
                CraftingContainer grid = new CraftingContainer(new AbstractContainerMenu(null, -1) {
                    @Override
                    public ItemStack quickMoveStack(Player player, int index) {
                        return ItemStack.EMPTY;
                    }

                    @Override
                    public boolean stillValid(Player player) {
                        return true;
                    }
                }, 3, 3);
                for (int r = 0; r < pattern.length; r++) {
                    for (int col = 0; col < 3; col++) {
                        if (pattern[r].charAt(col) == 'X') grid.setItem(r * 3 + col, new ItemStack(mat));
                    }
                }
                Optional<CraftingRecipe> crafted = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel());
                if (crafted.isEmpty() || crafted.get().assemble(grid).getItem() != item(id)) {
                    p.add(rid + ": crafting grid gives " + crafted.map(r -> r.getId() + " -> " + r.assemble(grid)).orElse("nothing"));
                }
                grid.setItem(0, new ItemStack(Items.IRON_INGOT)); // wrong material -> no cosmetic result
                Optional<CraftingRecipe> wrong = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel());
                if (wrong.isPresent() && wrong.get().assemble(grid).getItem() instanceof CosmeticArmorItem) p.add(rid + ": crafts with an iron ingot");
            }
        }
        long total = server.getRecipeManager().getRecipeIds().filter(CosmeticArmorGameTests::isCosmeticRecipe).count();
        if (total != COUNT) p.add(total + " cosmetic recipes, expected " + COUNT);
        int l = loaded;
        LOGGER.info("DEPORT-GATE cosmetic.recipes={}", l);
        finish(helper, "recipes", p);
    }

    /** DE items tab: chestpiece of each tier, then helmet, leggings, boots of the same tier; in no other DE tab. */
    @GameTest(template = T_3, batch = BATCH_COSMETIC)
    public static void cosmeticArmorTabPlacement(GameTestHelper helper) {
        List<String> p = new ArrayList<>();
        NonNullList<ItemStack> items = NonNullList.create();
        DECreativeTabs.ITEMS.fillItemList(items);
        List<Item> order = items.stream().map(ItemStack::getItem).toList();
        Map<CosmeticArmorTier, Item> chest = Map.of(
                CosmeticArmorTier.WYVERN, DEContent.CHESTPIECE_WYVERN.get(),
                CosmeticArmorTier.DRACONIC, DEContent.CHESTPIECE_DRACONIC.get(),
                CosmeticArmorTier.CHAOTIC, DEContent.CHESTPIECE_CHAOTIC.get());
        for (CosmeticArmorTier tier : CosmeticArmorTier.values()) {
            int at = order.indexOf(chest.get(tier));
            if (at < 0) {
                p.add(tier + ": chestpiece not in the items tab");
                continue;
            }
            for (int i = 0; i < SLOT_NAMES.length; i++) {
                Item want = item(tier.id() + "_" + SLOT_NAMES[i]);
                Item got = at + 1 + i < order.size() ? order.get(at + 1 + i) : null;
                if (got != want) p.add(tier + ": position chestpiece+" + (i + 1) + " is " + (got == null ? "end" : ForgeRegistries.ITEMS.getKey(got)) + ", expected " + ForgeRegistries.ITEMS.getKey(want));
            }
        }
        for (Item it : CosmeticArmor.all()) {
            long n = order.stream().filter(e -> e == it).count();
            if (n != 1) p.add(ForgeRegistries.ITEMS.getKey(it) + " appears " + n + " times in the items tab");
            if (it.getItemCategory() != DECreativeTabs.ITEMS) p.add(ForgeRegistries.ITEMS.getKey(it) + ": category " + it.getItemCategory());
        }
        for (CreativeModeTab other : List.of(DECreativeTabs.BLOCKS, DECreativeTabs.MODULES)) {
            NonNullList<ItemStack> list = NonNullList.create();
            other.fillItemList(list);
            list.stream().filter(s -> s.getItem() instanceof CosmeticArmorItem).forEach(s -> p.add(other.getRecipeFolderName() + " contains " + ForgeRegistries.ITEMS.getKey(s.getItem())));
        }
        LOGGER.info("DEPORT-GATE cosmetic.tab.items size={}", order.size());
        finish(helper, "tab", p);
    }

    /**
     * An armor stand wearing chestpiece + the 3 cosmetic pieces of each tier has exactly the attributes of one wearing
     * only the chestpiece, the modular-armor lookup still finds the chestpiece, and the pieces are not damaged by hits.
     */
    @GameTest(template = T_5, batch = BATCH_COSMETIC)
    public static void cosmeticArmorDoesNotChangeWearer(GameTestHelper helper) {
        List<ArmorStand[]> pairs = new ArrayList<>();
        int x = 1;
        for (CosmeticArmorTier tier : CosmeticArmorTier.values()) {
            Item chestpiece = switch (tier) {
                case WYVERN -> DEContent.CHESTPIECE_WYVERN.get();
                case DRACONIC -> DEContent.CHESTPIECE_DRACONIC.get();
                case CHAOTIC -> DEContent.CHESTPIECE_CHAOTIC.get();
            };
            ArmorStand only = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(x, 2, 1));
            ArmorStand full = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(x, 2, 3));
            only.setItemSlot(EquipmentSlot.CHEST, new ItemStack(chestpiece));
            full.setItemSlot(EquipmentSlot.CHEST, new ItemStack(chestpiece));
            for (Item it : CosmeticArmor.forTier(tier)) {
                full.setItemSlot(((CosmeticArmorItem) it).getSlot(), new ItemStack(it));
            }
            pairs.add(new ArmorStand[]{only, full});
            x++;
        }
        List<Attribute> attributes = List.of(Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.KNOCKBACK_RESISTANCE, Attributes.MOVEMENT_SPEED, Attributes.MAX_HEALTH);
        helper.runAfterDelay(3, safe(() -> {
            List<String> p = new ArrayList<>();
            for (ArmorStand[] pair : pairs) {
                ArmorStand only = pair[0], full = pair[1];
                String tier = String.valueOf(ForgeRegistries.ITEMS.getKey(full.getItemBySlot(EquipmentSlot.CHEST).getItem()));
                for (Attribute a : attributes) {
                    if (only.getAttributeValue(a) != full.getAttributeValue(a)) {
                        p.add(tier + ": " + ForgeRegistries.ATTRIBUTES.getKey(a) + " " + only.getAttributeValue(a) + " (chestpiece only) != " + full.getAttributeValue(a) + " (full set)");
                    }
                }
                if (only.getArmorValue() != full.getArmorValue()) p.add(tier + ": armor value " + only.getArmorValue() + " != " + full.getArmorValue());
                if (IModularArmor.getArmor(full).getItem() != full.getItemBySlot(EquipmentSlot.CHEST).getItem()) p.add(tier + ": IModularArmor.getArmor does not find the chestpiece");
                for (EquipmentSlot s : SLOTS) {
                    ItemStack st = full.getItemBySlot(s);
                    if (!(st.getItem() instanceof CosmeticArmorItem)) {
                        p.add(tier + ": " + s + " not worn (" + st + ")");
                        continue;
                    }
                    st.hurtAndBreak(100, full, e -> {});
                    if (st.isEmpty() || st.getDamageValue() != 0) p.add(tier + ": " + s + " piece damaged/broken");
                }
            }
            finish(helper, "wearer", p);
        }));
    }
}
