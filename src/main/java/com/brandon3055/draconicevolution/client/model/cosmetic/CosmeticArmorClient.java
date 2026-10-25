package com.brandon3055.draconicevolution.client.model.cosmetic;

import com.brandon3055.brandonscore.client.model.DummyHumanoidModel;
import com.brandon3055.brandonscore.client.render.EquippedItemModel;
import com.brandon3055.draconicevolution.DEConfig;
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
 * <ul>
 *     <li>{@code DEConfig.fancyToolModels = true}: the vanilla armor layer gets BrandonsCore's empty
 *     {@link DummyHumanoidModel} (draws nothing) and BC's {@code EquippedItemModelLayer} draws the
 *     {@link CosmeticArmorModel} (1.12.2 3D parts). This is the chestpiece's path.</li>
 *     <li>{@code fancyToolModels = false} (recommended with Oculus shader packs): the vanilla armor layer draws its own
 *     model with the 1.12.2 2D armor textures ({@code CosmeticArmorItem.getArmorTexture}); the BC layer draws nothing.</li>
 * </ul>
 * The flag is read every frame (DE documents it as "requires restart"; switching it at runtime works too).
 */
public class CosmeticArmorClient {

    public static final IClientItemExtensions ITEM_EXTENSIONS = new IClientItemExtensions() {
        @Override
        public @NotNull HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
            return DEConfig.fancyToolModels ? DummyHumanoidModel.INSTANCE : original;
        }
    };

    private static final EquippedItemModel NO_MODEL = (entity, poseStack, buffers, stack, packedLight, packedOverlay, partialTicks) -> {};

    private static final Map<CosmeticArmorTier, Map<EquipmentSlot, CosmeticArmorModel<?>>> MODELS = new EnumMap<>(CosmeticArmorTier.class);

    public static EquippedItemModel getModel(CosmeticArmorTier tier, EquipmentSlot slot, HumanoidModel<?> parentModel) {
        if (!DEConfig.fancyToolModels) {
            return NO_MODEL;
        }
        CosmeticArmorModel<?> model = MODELS.computeIfAbsent(tier, t -> new EnumMap<>(EquipmentSlot.class))
                .computeIfAbsent(slot, s -> new CosmeticArmorModel<>(tier, s));
        //Same as the vanilla armor layer (parent.copyPropertiesTo + part poses): walking, sneaking, riding, swimming,
        //baby and armor stand poses all come from the wearer's model.
        ForgeHooksClient.copyModelProperties(parentModel, model);
        return model;
    }
}
