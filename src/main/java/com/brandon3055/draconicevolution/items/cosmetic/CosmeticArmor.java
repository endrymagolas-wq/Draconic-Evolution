package com.brandon3055.draconicevolution.items.cosmetic;

import com.brandon3055.draconicevolution.init.DEContent;
import com.brandon3055.draconicevolution.init.DECreativeTabs;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.brandon3055.draconicevolution.DraconicEvolution.MODID;

/**
 * [1.19.2 port addition, not in upstream 1.20.1] Registration of the 9 cosmetic armor items
 * ({wyvern,draconic,chaotic}_{helmet,leggings,boots}). See PORT_NOTES.md, "Additions not in upstream 1.20.1: cosmetic
 * armor". Own DeferredRegister so the upstream {@code DEContent} item list stays untouched.
 */
public class CosmeticArmor {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    /** Slots in creative-tab order (after the chestpiece of the same tier). */
    public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static final RegistryObject<CosmeticArmorItem> WYVERN_HELMET = register(CosmeticArmorTier.WYVERN, EquipmentSlot.HEAD);
    public static final RegistryObject<CosmeticArmorItem> WYVERN_LEGGINGS = register(CosmeticArmorTier.WYVERN, EquipmentSlot.LEGS);
    public static final RegistryObject<CosmeticArmorItem> WYVERN_BOOTS = register(CosmeticArmorTier.WYVERN, EquipmentSlot.FEET);
    public static final RegistryObject<CosmeticArmorItem> DRACONIC_HELMET = register(CosmeticArmorTier.DRACONIC, EquipmentSlot.HEAD);
    public static final RegistryObject<CosmeticArmorItem> DRACONIC_LEGGINGS = register(CosmeticArmorTier.DRACONIC, EquipmentSlot.LEGS);
    public static final RegistryObject<CosmeticArmorItem> DRACONIC_BOOTS = register(CosmeticArmorTier.DRACONIC, EquipmentSlot.FEET);
    public static final RegistryObject<CosmeticArmorItem> CHAOTIC_HELMET = register(CosmeticArmorTier.CHAOTIC, EquipmentSlot.HEAD);
    public static final RegistryObject<CosmeticArmorItem> CHAOTIC_LEGGINGS = register(CosmeticArmorTier.CHAOTIC, EquipmentSlot.LEGS);
    public static final RegistryObject<CosmeticArmorItem> CHAOTIC_BOOTS = register(CosmeticArmorTier.CHAOTIC, EquipmentSlot.FEET);

    public static void init() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    public static String itemName(CosmeticArmorTier tier, EquipmentSlot slot) {
        return tier.id() + "_" + switch (slot) {
            case HEAD -> "helmet";
            case LEGS -> "leggings";
            case FEET -> "boots";
            default -> throw new IllegalArgumentException("No cosmetic armor for slot " + slot);
        };
    }

    private static RegistryObject<CosmeticArmorItem> register(CosmeticArmorTier tier, EquipmentSlot slot) {
        return ITEMS.register(itemName(tier, slot), () -> new CosmeticArmorItem(tier, slot, new Item.Properties()
                .tab(DECreativeTabs.ITEMS)
                .stacksTo(1)
                .rarity(tier.rarity)));
    }

    /** All 9 items, tier by tier, each tier in {@link #SLOTS} order. */
    public static List<Item> all() {
        List<Item> list = new ArrayList<>();
        for (CosmeticArmorTier tier : CosmeticArmorTier.values()) list.addAll(forTier(tier));
        return list;
    }

    public static List<Item> forTier(CosmeticArmorTier tier) {
        List<Item> list = new ArrayList<>();
        for (RegistryObject<Item> obj : ITEMS.getEntries()) {
            if (obj.get() instanceof CosmeticArmorItem item && item.getTier() == tier) list.add(item);
        }
        list.sort((a, b) -> Integer.compare(slotOrder(((CosmeticArmorItem) a).getSlot()), slotOrder(((CosmeticArmorItem) b).getSlot())));
        return list;
    }

    /**
     * Creative tab hook (DECreativeTabs.itemContents): the cosmetic pieces that follow the given item in the DE items
     * tab, i.e. the tier's helmet, leggings and boots right after its modular chestpiece; empty for any other item.
     */
    public static List<Item> followingInTab(Item item) {
        if (item == DEContent.CHESTPIECE_WYVERN.get()) return forTier(CosmeticArmorTier.WYVERN);
        if (item == DEContent.CHESTPIECE_DRACONIC.get()) return forTier(CosmeticArmorTier.DRACONIC);
        if (item == DEContent.CHESTPIECE_CHAOTIC.get()) return forTier(CosmeticArmorTier.CHAOTIC);
        return Collections.emptyList();
    }

    private static int slotOrder(EquipmentSlot slot) {
        for (int i = 0; i < SLOTS.length; i++) if (SLOTS[i] == slot) return i;
        return SLOTS.length;
    }
}
