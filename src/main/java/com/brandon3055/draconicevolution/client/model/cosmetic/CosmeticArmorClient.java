package com.brandon3055.draconicevolution.client.model.cosmetic;

import com.brandon3055.brandonscore.client.model.DummyHumanoidModel;
import com.brandon3055.brandonscore.client.render.EquippedItemModel;
import com.brandon3055.draconicevolution.items.cosmetic.CosmeticArmorTier;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;

/**
 * [1.19.2 port addition, not in upstream 1.20.1] Client side of the cosmetic armor. Only referenced from
 * {@code @OnlyIn(Dist.CLIENT)} methods of {@code CosmeticArmorItem}.
 * The vanilla armor layer gets BrandonsCore's empty {@link DummyHumanoidModel} (draws nothing) and BC's
 * {@code EquippedItemModelLayer} always draws the {@link CosmeticArmorModel} (1.12.2 3D parts), independent of
 * {@code DEConfig.fancyToolModels}: the parts use plain vanilla entity render types (no DE custom shaders), so they
 * also render under Oculus shader packs, and the 1.12.2 2D armor layer textures look broken (they were unused in 1.12.2).
 */
public class CosmeticArmorClient {

    public static final IClientItemExtensions ITEM_EXTENSIONS = new IClientItemExtensions() {
        @Override
        public @NotNull HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
            return DummyHumanoidModel.INSTANCE;
        }
    };

    private static final Map<CosmeticArmorTier, Map<EquipmentSlot, CosmeticArmorModel<?>>> MODELS = new EnumMap<>(CosmeticArmorTier.class);

    public static EquippedItemModel getModel(CosmeticArmorTier tier, EquipmentSlot slot, HumanoidModel<?> parentModel) {
        CosmeticArmorModel<?> model = MODELS.computeIfAbsent(tier, t -> new EnumMap<>(EquipmentSlot.class))
                .computeIfAbsent(slot, s -> new CosmeticArmorModel<>(tier, s));
        //Same as the vanilla armor layer (parent.copyPropertiesTo + part poses): walking, sneaking, riding, swimming,
        //baby and armor stand poses all come from the wearer's model.
        ForgeHooksClient.copyModelProperties(parentModel, model);
        return model;
    }
}
