package com.brandon3055.draconicevolution.items.cosmetic;

import com.brandon3055.brandonscore.client.render.EquippedItemModel;
import com.brandon3055.brandonscore.items.EquippedModelItem;
import com.brandon3055.draconicevolution.client.model.cosmetic.CosmeticArmorClient;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

import static com.brandon3055.draconicevolution.DraconicEvolution.MODID;

/**
 * [1.19.2 port addition, not in upstream 1.20.1] Purely cosmetic helmet / leggings / boots that visually complete a set
 * with the modular chestpiece. No armor, toughness or knockback resistance, no attribute modifiers at all, not
 * damageable, not enchantable, not repairable, no special effects. It is a plain {@link ArmorItem}, so nothing in DE's
 * modular armor logic (which looks for {@code IModularItem} / {@code IModularArmor}) ever sees it.
 * <p>
 * Rendering (client, {@link CosmeticArmorClient}): with {@code DEConfig.fancyToolModels = true} the 1.12.2 3D OBJ parts
 * are drawn by BrandonsCore's {@code EquippedItemModelLayer} (the same path as the chestpiece) and the vanilla armor
 * layer draws nothing; with {@code fancyToolModels = false} the vanilla armor layer draws the 1.12.2 2D armor textures
 * returned by {@link #getArmorTexture}.
 */
public class CosmeticArmorItem extends ArmorItem implements EquippedModelItem {
    private final CosmeticArmorTier tier;

    public CosmeticArmorItem(CosmeticArmorTier tier, EquipmentSlot slot, Item.Properties props) {
        super(new CosmeticArmorMaterial(tier), slot, props);
        this.tier = tier;
    }

    public CosmeticArmorTier getTier() {
        return tier;
    }

    //region No properties

    @Override
    @SuppressWarnings("deprecation")
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return ImmutableMultimap.of();
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return false;
    }

    @Override
    public int getEnchantmentValue() {
        return 0;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairItem) {
        return false;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.draconicevolution.cosmetic_armor").withStyle(ChatFormatting.GRAY));
    }

    //endregion

    //region Rendering

    /** Non-fancy mode: the 1.12.2 2D armor layers (vanilla HumanoidArmorLayer; layer 2 = leggings). */
    @Nullable
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return MODID + ":textures/models/cosmetic_armor/" + tier.id() + "_armor_layer_" + (slot == EquipmentSlot.LEGS ? 2 : 1) + ".png";
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(CosmeticArmorClient.ITEM_EXTENSIONS);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public EquippedItemModel getExtendedModel(LivingEntity entity, ItemStack stack, @Nullable EquipmentSlot slot, HumanoidModel<?> parentModel, boolean slimModel) {
        return CosmeticArmorClient.getModel(tier, getSlot(), parentModel);
    }

    //endregion
}
