package com.brandon3055.draconicevolution.items.cosmetic;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

import static com.brandon3055.draconicevolution.DraconicEvolution.MODID;

/**
 * [1.19.2 port addition] Armor material of the cosmetic armor: no protection, no durability (the item is not
 * damageable), not enchantable, no repair ingredient. Only the equip sound is kept.
 */
public class CosmeticArmorMaterial implements ArmorMaterial {
    private final String name;

    public CosmeticArmorMaterial(CosmeticArmorTier tier) {
        this.name = MODID + ":cosmetic_" + tier.id();
    }

    @Override
    public int getDurabilityForSlot(EquipmentSlot slot) {
        return 0; //Item.Properties.defaultDurability(0) -> maxDamage 0 -> not damageable
    }

    @Override
    public int getDefenseForSlot(EquipmentSlot slot) {
        return 0;
    }

    @Override
    public int getEnchantmentValue() {
        return 0;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_DIAMOND;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.EMPTY;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public float getToughness() {
        return 0;
    }

    @Override
    public float getKnockbackResistance() {
        return 0;
    }
}
