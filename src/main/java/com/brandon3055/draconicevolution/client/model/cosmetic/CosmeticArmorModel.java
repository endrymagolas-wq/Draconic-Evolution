package com.brandon3055.draconicevolution.client.model.cosmetic;

import codechicken.lib.render.CCModel;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.model.OBJParser;
import codechicken.lib.vec.Vertex5;
import com.brandon3055.brandonscore.client.model.ExtendedModelPart;
import com.brandon3055.brandonscore.client.render.EquippedItemModel;
import com.brandon3055.draconicevolution.items.cosmetic.CosmeticArmorTier;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

import static com.brandon3055.draconicevolution.DraconicEvolution.MODID;

/**
 * [1.19.2 port addition, not in upstream 1.20.1] 3D model of one cosmetic armor piece (fancy mode).
 * <p>
 * Port of DE 1.12.2 {@code ModelWyvernArmor} / {@code ModelDraconicArmor} + {@code ModelRenderOBJ} to the 1.19.2
 * {@link HumanoidModel} / {@link ModelPart} system, built like {@code ModularChestpieceModel}: the OBJ parts are CCL
 * {@link CCModel}s inside BrandonsCore {@link ExtendedModelPart}s that replace the humanoid head / body / legs, and the
 * model is drawn by BC's {@code EquippedItemModelLayer} with a {@link MultiBufferSource}. Unlike the chestpiece there is no
 * DE shader and no VBO: every frame the triangles go through {@link CCRenderState} into the plain vanilla
 * {@link RenderType#entityCutoutNoCull} buffer of the part's texture (vanilla lighting, overlay and fog; shader mods
 * treat it as ordinary entity geometry).
 * <p>
 * Pose: {@code CosmeticArmorClient} copies the wearer's model pose ({@code ForgeHooksClient.copyModelProperties}) every
 * frame, exactly what the vanilla armor layer does, so the pieces follow walking, sneaking, riding, swimming and armor
 * stand poses. Each OBJ part sits in its bone's frame with the 1.12.2 offset (blocks) and scale, then the 1.12.2
 * {@code GlStateManager.rotate(180, -1, 0, 1)} of {@code ModelRenderOBJ.compileDisplayList}. 1.12.2 and 1.19.2 use the
 * same humanoid skeleton (pivots, part sizes, entity transform {@code scale(-1,-1,1)} + {@code translate(0,-1.501,0)}),
 * so these numbers place the parts as in 1.12.2. The 1.12.2 whole-model {@code translate(0, 0.2, 0)} for sneaking is
 * not ported: in 1.19.2 sneaking moves the bones instead, and the copied pose already contains that.
 */
public class CosmeticArmorModel<T extends LivingEntity> extends HumanoidModel<T> implements EquippedItemModel {

    /**
     * offsetX, offsetY, offsetZ (blocks), scale. From DE 1.12.2 ModelWyvernArmor / ModelDraconicArmor.
     * One deliberate deviation: the Wyvern helmet is scaled by 1.01. At the 1.12.2 scale 104 of its faces lie exactly in
     * the planes of the vanilla head cube (skin), which z-fights; +1 % moves them 0.04 px outwards (invisible otherwise).
     * All other parts have no face coplanar with a vanilla skin face (checked offline against the vanilla cubes).
     */
    private static final Map<String, float[]> TRANSFORMS = Map.of(
            "wyvern_helmet", new float[]{0F, -0.07F, 0F, 1.01F / 16F},     //1.12: scale unset -> 1/16; port: +1 % against z-fighting
            "draconic_helmet", new float[]{-0.033F, -0.1F, 0.1F, 1F / 13F},
            "belt", new float[]{0F, 0.756F, -0.04F, 1F / 16F},              //scale unset in 1.12 (both tiers)
            "right_leg", new float[]{-0.085F, 0.6F, 0F, 1F / 14F},
            "left_leg", new float[]{0.085F, 0.6F, 0F, 1F / 14F},
            "right_boot", new float[]{-0.03F, 0.76F, 0F, 1F / 14F},
            "left_boot", new float[]{0.03F, 0.76F, 0F, 1F / 14F}
    );

    /** 1.12.2 ModelRenderOBJ: GlStateManager.rotate(180, -1, 0, 1). */
    private static final Quaternion OBJ_ROTATION;

    static {
        Vector3f axis = new Vector3f(-1, 0, 1);
        axis.normalize();
        OBJ_ROTATION = axis.rotationDegrees(180);
    }

    private final CosmeticArmorTier tier;
    private final EquipmentSlot slot;

    public CosmeticArmorModel(CosmeticArmorTier tier, EquipmentSlot slot) {
        super(createMesh(CubeDeformation.NONE, 0).getRoot().bake(64, 64));
        this.tier = tier;
        this.slot = slot;
        ExtendedModelPart head = new ExtendedModelPart();
        ExtendedModelPart body = new ExtendedModelPart();
        ExtendedModelPart rightLeg = new ExtendedModelPart();
        ExtendedModelPart leftLeg = new ExtendedModelPart();
        switch (slot) {
            case HEAD -> head.addChild(part("helmet"));
            case LEGS -> {
                body.addChild(part("belt"));
                rightLeg.addChild(part("right_leg"));
                leftLeg.addChild(part("left_leg"));
            }
            case FEET -> {
                rightLeg.addChild(part("right_boot"));
                leftLeg.addChild(part("left_boot"));
            }
            default -> throw new IllegalArgumentException("No cosmetic armor for slot " + slot);
        }
        this.head = head;
        this.body = body;
        this.rightLeg = rightLeg;
        this.leftLeg = leftLeg;
        this.rightArm = new ExtendedModelPart();
        this.leftArm = new ExtendedModelPart();
    }

    private ObjPart part(String name) {
        String geometry = tier.geometry + "_" + name;
        float[] t = TRANSFORMS.containsKey(geometry) ? TRANSFORMS.get(geometry) : TRANSFORMS.get(name);
        ResourceLocation obj = new ResourceLocation(MODID, "models/cosmetic_armor/" + geometry + ".obj");
        ResourceLocation texture = new ResourceLocation(MODID, "textures/models/cosmetic_armor/" + tier.id() + "_" + name + ".png");
        return new ObjPart(loadModel(obj), RenderType.entityCutoutNoCull(texture), t[0], t[1], t[2], t[3]);
    }

    /**
     * QUADS (triangles become degenerate quads) so the parts can go into vanilla entity buffers. The 1.12.2 OBJs carry
     * no normals (flat normals are computed, as the 1.12.2 OBJ loader did) and expect the texture V axis top-down,
     * while CCL's parser flips V (OBJ convention), so V is flipped back here.
     */
    private static CCModel loadModel(ResourceLocation obj) {
        Map<String, CCModel> map = new OBJParser(obj).ignoreMtl().quads().parse();
        CCModel model = CCModel.combine(map.values());
        for (Vertex5 v : model.verts) {
            v.uv.v = 1 - v.uv.v;
        }
        return model.computeNormals();
    }

    public EquipmentSlot getSlot() {
        return slot;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay, float r, float g, float b, float a) {}

    @Override
    protected Iterable<ModelPart> headParts() {
        return ImmutableList.of(head);
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
        return ImmutableList.of(body, rightLeg, leftLeg);
    }

    @Override
    public void render(LivingEntity entity, PoseStack poseStack, MultiBufferSource buffers, ItemStack stack, int packedLight, int packedOverlay, float partialTicks) {
        //Same structure as HumanoidModel/AgeableListModel.renderToBuffer and ModularChestpieceModel.render
        if (this.young) {
            poseStack.pushPose();
            if (this.scaleHead) {
                float f = 1.5F / this.babyHeadScale;
                poseStack.scale(f, f, f);
            }
            poseStack.translate(0.0D, this.babyYHeadOffset / 16.0F, this.babyZHeadOffset / 16.0F);
            this.headParts().forEach(part -> ((ExtendedModelPart) part).render(poseStack, buffers, packedLight, packedOverlay));
            poseStack.popPose();
            poseStack.pushPose();
            float f1 = 1.0F / this.babyBodyScale;
            poseStack.scale(f1, f1, f1);
            poseStack.translate(0.0D, this.bodyYOffset / 16.0F, 0.0D);
            this.bodyParts().forEach(part -> ((ExtendedModelPart) part).render(poseStack, buffers, packedLight, packedOverlay));
            poseStack.popPose();
        } else {
            this.headParts().forEach(part -> ((ExtendedModelPart) part).render(poseStack, buffers, packedLight, packedOverlay));
            this.bodyParts().forEach(part -> ((ExtendedModelPart) part).render(poseStack, buffers, packedLight, packedOverlay));
        }
    }

    /** One OBJ part (1.12.2 ModelRenderOBJ) as a child of a bone. */
    public static class ObjPart extends ExtendedModelPart {
        private final CCModel model;
        private final RenderType renderType;
        private final float offX, offY, offZ, scale;

        public ObjPart(CCModel model, RenderType renderType, float offX, float offY, float offZ, float scale) {
            if (renderType.mode() != VertexFormat.Mode.QUADS) throw new IllegalArgumentException("QUADS render type expected");
            this.model = model;
            this.renderType = renderType;
            this.offX = offX;
            this.offY = offY;
            this.offZ = offZ;
            this.scale = scale;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay, float r, float g, float b, float a) {
            if (!this.visible) return;
            poseStack.pushPose();
            this.translateAndRotate(poseStack); //own pose is zero; kept for completeness
            poseStack.translate(offX, offY, offZ);
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(OBJ_ROTATION);
            CCRenderState ccrs = CCRenderState.instance();
            ccrs.reset();
            ccrs.computeLighting = false; //vanilla entity shader does the lighting
            ccrs.bind(renderType, buffers, poseStack);
            ccrs.brightness = packedLight;
            ccrs.overlay = packedOverlay;
            ccrs.baseColour = 0xFFFFFFFF;
            model.render(ccrs);
            poseStack.popPose();
        }
    }
}
