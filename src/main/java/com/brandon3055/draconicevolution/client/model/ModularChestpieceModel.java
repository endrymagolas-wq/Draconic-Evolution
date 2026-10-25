package com.brandon3055.draconicevolution.client.model;

import codechicken.lib.render.CCModel;
import codechicken.lib.render.CCRenderState;
import com.brandon3055.brandonscore.mgui.render.BindingVBORenderType;
import codechicken.lib.render.model.OBJParser;
import codechicken.lib.util.ClientUtils;
import codechicken.lib.vec.Matrix4;
import codechicken.lib.vec.Translation;
import codechicken.lib.vec.Vertex5;
import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.brandonscore.client.model.ExtendedModelPart;
import com.brandon3055.brandonscore.client.render.EquippedItemModel;
import com.brandon3055.brandonscore.client.shader.BCShaders;
import com.brandon3055.brandonscore.handlers.contributor.ContributorHandler;
import com.brandon3055.brandonscore.handlers.contributor.ContributorProperties;
import com.brandon3055.draconicevolution.DraconicEvolution;
import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.brandon3055.draconicevolution.api.modules.ModuleTypes;
import com.brandon3055.draconicevolution.api.modules.entities.ShieldControlEntity;
import com.brandon3055.draconicevolution.client.DEShaders;
import com.brandon3055.draconicevolution.client.model.classic.ClassicObj;
import com.brandon3055.draconicevolution.client.model.cosmetic.CosmeticArmorModel;
import com.brandon3055.draconicevolution.client.render.ShaderPackFallback;
import com.brandon3055.draconicevolution.client.render.item.ToolRenderBase;
import com.brandon3055.brandonscore.client.shader.BCShader;
import com.brandon3055.draconicevolution.client.shader.ShieldShader;
import com.brandon3055.draconicevolution.client.shader.ToolShader;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;

import java.util.*;

import static com.brandon3055.draconicevolution.DraconicEvolution.MODID;

/**
 * Created by brandon3055 on 13/11/2022
 */
public class ModularChestpieceModel<T extends LivingEntity> extends HumanoidModel<T> implements EquippedItemModel {

    private final TechLevel techLevel;
    private int shieldColour;
    private float shieldState;
    //PORT(1.19.2) classic (DE 1.12.2) look, see classic(TechLevel)
    private final boolean classic;
    private boolean shieldPass;

    public ModularChestpieceModel(TechLevel techLevel, boolean isOnArmor) {
        this(techLevel, isOnArmor, false);
    }

    /**
     * PORT(1.19.2) port addition (DEConfig.classicModels): the worn chestpiece with the DE 1.12.2 look. The body and both
     * arms are the 1.12.2 OBJ parts ({@code models/cosmetic_armor/<wyvern|draconic>_{body,left_arm,right_arm}.obj},
     * Chaotic = Draconic geometry with recoloured textures) drawn like the cosmetic armor
     * ({@link CosmeticArmorModel.ObjPart}: 1.12.2 offsets/scales of ModelWyvernArmor / ModelDraconicArmor, vanilla
     * {@code entityCutoutNoCull}, pose copied from the wearer). No DE3 base model, chevrons, power crystals or core gem.
     * The DE3 shield parts (shield shader, or the shader-pack fallback) are kept on top, driven by the same shield state
     * and colour; they are drawn in a second pass after the armor parts were flushed, and without depth writes, so armor
     * drawn later (e.g. the cosmetic helmet) is not hidden by the translucent shell.
     */
    public static <T extends LivingEntity> ModularChestpieceModel<T> classic(TechLevel techLevel) {
        return new ModularChestpieceModel<>(techLevel, false, true);
    }

    private ModularChestpieceModel(TechLevel techLevel, boolean isOnArmor, boolean classic) {
        super(createMesh(new CubeDeformation(1), 0).getRoot().bake(64, 64));
        this.techLevel = techLevel;
        this.classic = classic;
        Map<String, CCModel> model = new OBJParser(new ResourceLocation(DraconicEvolution.MODID, "models/item/equipment/chestpeice.obj")).ignoreMtl().parse();
        CCModel baseModel = model.get("base_model").backfacedCopy();
        CCModel materialModel = model.get("chevrons").backfacedCopy();
        CCModel gemModel = model.get("power_crystals").backfacedCopy();
        CCModel coreGemModel = model.get("crystal_core").backfacedCopy();

        CCModel shieldHeadModel = model.get("shield_head").backfacedCopy();
        CCModel shieldBodyModel = model.get("shield_body").backfacedCopy();
        CCModel shieldRightArmModel = model.get("shield_right_arm").backfacedCopy();
        CCModel shieldLeftArmModel = model.get("shield_left_arm").backfacedCopy();
        CCModel shieldRightLegModel = model.get("shield_right_leg").backfacedCopy();
        CCModel shieldLeftLegModel = model.get("shield_left_leg").backfacedCopy();

        if (isOnArmor) {
            materialModel.apply(new Translation(0, 0, -0.0625));
            gemModel.apply(new Translation(0, 0, -0.0625 / 2));
            coreGemModel.apply(new Translation(0, 0, -0.0625 / 2));
        }

        String levelName = techLevel.name().toLowerCase(Locale.ROOT);
        RenderType baseType = RenderType.create(MODID + ":base", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 256, true, false, RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(DEShaders.TOOL_BASE_SHADER::getShaderInstance))
                .setTextureState(new RenderStateShard.TextureStateShard(new ResourceLocation(MODID, "textures/item/equipment/" + levelName + "_chestpeice.png"), false, false))
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .createCompositeState(true)
        );

        RenderType chaoticType = RenderType.create(MODID + ":tool_chaos", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 256, RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(BCShaders.CHAOS_ENTITY_SHADER::getShaderInstance))
                .setTextureState(new RenderStateShard.TextureStateShard(new ResourceLocation(MODID, "textures/item/equipment/chaos_shader.png"), true, false))
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .createCompositeState(false)
        );

        RenderType gemType = RenderType.create(MODID + ":tool_gem", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 256, RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(DEShaders.TOOL_GEM_SHADER::getShaderInstance))
                .setTextureState(new RenderStateShard.TextureStateShard(new ResourceLocation(MODID, "textures/item/equipment/shader_fallback_" + levelName + ".png"), false, false))
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .createCompositeState(false)
        );

        RenderType coreGemType = RenderType.create(MODID + ":core_gem", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 256, RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(DEShaders.CHESTPIECE_GEM_SHADER::getShaderInstance))
                .setTextureState(new RenderStateShard.TextureStateShard(new ResourceLocation(MODID, "textures/item/equipment/shader_fallback_" + levelName + ".png"), false, false))
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .createCompositeState(false)
        );

        RenderType shieldType = RenderType.create(MODID + ":armor_shield", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 256, RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(DEShaders.CHESTPIECE_SHIELD_SHADER::getShaderInstance))
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .createCompositeState(false)
        );

        //PORT(1.19.2) shader-pack fallback types (vanilla entity render types, see ShaderPackFallback)
        RenderType baseFallback = RenderType.entityCutout(new ResourceLocation(MODID, "textures/item/equipment/" + levelName + "_chestpeice.png"));
        RenderType chaoticFallback = RenderType.entityCutout(ShaderPackFallback.CHAOS);
        RenderType gemFallback = RenderType.entityTranslucentEmissive(ShaderPackFallback.energyTexture(techLevel));
        RenderType coreGemFallback = RenderType.entityTranslucentEmissive(ShaderPackFallback.WHITE);

        if (classic) {
            //Same shader as the DE3 shield, without depth writes (see classic(TechLevel))
            RenderType classicShieldType = RenderType.create(MODID + ":armor_shield_classic", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 256, RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(DEShaders.CHESTPIECE_SHIELD_SHADER::getShaderInstance))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .createCompositeState(false)
            );
            ExtendedModelPart classicBody = new ExtendedModelPart();
            ExtendedModelPart classicLeftArm = new ExtendedModelPart();
            ExtendedModelPart classicRightArm = new ExtendedModelPart();
            ClassicArmorPart classicBodyPart = classicPart("body");
            ClassicArmorPart classicLeftArmPart = classicPart("left_arm");
            ClassicArmorPart classicRightArmPart = classicPart("right_arm");
            classicBody.addChild(classicBodyPart);
            classicLeftArm.addChild(classicLeftArmPart);
            classicRightArm.addChild(classicRightArmPart);
            //The 1.12.2 parts are much bigger than the DE3 chestpiece: stretch the DE3 shield shells around them
            classicBody.addChild(new ShieldModelPart(fitShell(shieldBodyModel, classicBodyPart), classicShieldType, DEShaders.CHESTPIECE_SHIELD_SHADER));
            classicLeftArm.addChild(new ShieldModelPart(fitShell(shieldLeftArmModel, classicLeftArmPart), classicShieldType, DEShaders.CHESTPIECE_SHIELD_SHADER));
            classicRightArm.addChild(new ShieldModelPart(fitShell(shieldRightArmModel, classicRightArmPart), classicShieldType, DEShaders.CHESTPIECE_SHIELD_SHADER));
            this.body = classicBody;
            leftArm = classicLeftArm;
            rightArm = classicRightArm;
            head = new ShieldModelPart(shieldHeadModel, classicShieldType, DEShaders.CHESTPIECE_SHIELD_SHADER);
            leftLeg = new ShieldModelPart(shieldLeftLegModel, classicShieldType, DEShaders.CHESTPIECE_SHIELD_SHADER);
            rightLeg = new ShieldModelPart(shieldRightLegModel, classicShieldType, DEShaders.CHESTPIECE_SHIELD_SHADER);
            return;
        }

        ExtendedModelPart body = new ExtendedModelPart();
        if (!isOnArmor) {
            body.addChild(new ChestpieceModelPart(baseModel, baseType, DEShaders.TOOL_BASE_SHADER).fallback(baseFallback, 0xFFFFFFFF, false));
        }
        if (techLevel == TechLevel.CHAOTIC) {
            body.addChild(new ChestpieceModelPart(materialModel, chaoticType, BCShaders.CHAOS_ENTITY_SHADER).fallback(chaoticFallback, 0xFFFFFFFF, false));
        } else {
            body.addChild(new ChestpieceModelPart(materialModel, baseType, DEShaders.TOOL_BASE_SHADER).fallback(baseFallback, 0xFFFFFFFF, false));
        }
        body.addChild(new ChestpieceModelPart(gemModel, gemType, DEShaders.TOOL_GEM_SHADER).fallback(gemFallback, ShaderPackFallback.energyColour(techLevel), true));
        body.addChild(new CoreGemModelPart(coreGemModel, coreGemType, DEShaders.CHESTPIECE_GEM_SHADER).fallback(coreGemFallback, 0xFFFFFFFF, true));
        this.body = body;

        head = new ShieldModelPart(shieldHeadModel, shieldType, DEShaders.CHESTPIECE_SHIELD_SHADER);
        body.addChild(new ShieldModelPart(shieldBodyModel, shieldType, DEShaders.CHESTPIECE_SHIELD_SHADER));
        leftArm = new ShieldModelPart(shieldLeftArmModel, shieldType, DEShaders.CHESTPIECE_SHIELD_SHADER);
        rightArm = new ShieldModelPart(shieldRightArmModel, shieldType, DEShaders.CHESTPIECE_SHIELD_SHADER);
        leftLeg = new ShieldModelPart(shieldLeftLegModel, shieldType, DEShaders.CHESTPIECE_SHIELD_SHADER);
        rightLeg = new ShieldModelPart(shieldRightLegModel, shieldType, DEShaders.CHESTPIECE_SHIELD_SHADER);
    }

    /**
     * PORT(1.19.2) classic look: offset (blocks) and scale of the 1.12.2 part in its bone (ModelWyvernArmor /
     * ModelDraconicArmor; the body scale is unset in 1.12.2, i.e. the 1/16 model scale).
     */
    private static final Map<String, float[]> CLASSIC_TRANSFORMS = Map.of(
            "wyvern_body", new float[]{0F, 0.755F, -0.03F, 1F / 16F},
            "wyvern_right_arm", new float[]{-0.21F, 0.72F, 0F, 1F / 13.7F},
            "wyvern_left_arm", new float[]{0.21F, 0.72F, 0F, 1F / 13.7F},
            "draconic_body", new float[]{0F, 0.755F, -0.03F, 1F / 16F},
            "draconic_right_arm", new float[]{-0.205F, 0.72F, -0.05F, 1F / 13.9F},
            "draconic_left_arm", new float[]{0.21F, 0.72F, -0.06F, 1F / 13.9F}
    );

    /** 1.12.2 geometry of a tier: Wyvern, else Draconic (Chaotic had no 1.12.2 armor; it uses the Draconic parts). */
    public static String classicGeometry(TechLevel techLevel) {
        return techLevel == TechLevel.WYVERN || techLevel == TechLevel.DRACONIUM ? "wyvern" : "draconic";
    }

    private ClassicArmorPart classicPart(String name) {
        String geometry = classicGeometry(techLevel) + "_" + name;
        String texture = (techLevel == TechLevel.DRACONIUM ? "wyvern" : techLevel.name().toLowerCase(Locale.ROOT)) + "_" + name;
        float[] t = CLASSIC_TRANSFORMS.get(geometry);
        return new ClassicArmorPart(ClassicObj.load(new ResourceLocation(MODID, "models/cosmetic_armor/" + geometry + ".obj")),
                RenderType.entityCutoutNoCull(new ResourceLocation(MODID, "textures/models/cosmetic_armor/" + texture + ".png")), t[0], t[1], t[2], t[3]);
    }

    /**
     * Copy of a DE3 shield shell, scaled per axis so that its bounding box is the bounding box of the classic part (in
     * the bone frame, as drawn) plus {@link #SHELL_MARGIN} on every side. The shell keeps its shape and UVs (the shield
     * shader's pattern is UV based).
     */
    private CCModel fitShell(CCModel shell, ClassicArmorPart part) {
        double[] target = part.boundsInBone();
        double[] source = bounds(shell);
        CCModel out = shell.copy();
        for (Vertex5 v : out.verts) {
            v.vec.x = fit(v.vec.x, source[0], source[3], target[0] - SHELL_MARGIN, target[3] + SHELL_MARGIN);
            v.vec.y = fit(v.vec.y, source[1], source[4], target[1] - SHELL_MARGIN, target[4] + SHELL_MARGIN);
            v.vec.z = fit(v.vec.z, source[2], source[5], target[2] - SHELL_MARGIN, target[5] + SHELL_MARGIN);
        }
        return out;
    }

    private static final double SHELL_MARGIN = 1 / 32D;

    private static double fit(double value, double srcMin, double srcMax, double dstMin, double dstMax) {
        return srcMax - srcMin < 1e-6 ? (dstMin + dstMax) / 2 : dstMin + (value - srcMin) * (dstMax - dstMin) / (srcMax - srcMin);
    }

    /** minX, minY, minZ, maxX, maxY, maxZ of a model's vertices */
    private static double[] bounds(CCModel model) {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (Vertex5 v : model.verts) {
            b[0] = Math.min(b[0], v.vec.x);
            b[1] = Math.min(b[1], v.vec.y);
            b[2] = Math.min(b[2], v.vec.z);
            b[3] = Math.max(b[3], v.vec.x);
            b[4] = Math.max(b[4], v.vec.y);
            b[5] = Math.max(b[5], v.vec.z);
        }
        return b;
    }

    /** One 1.12.2 armor part; drawn in the first (armor) pass only. */
    public class ClassicArmorPart extends CosmeticArmorModel.ObjPart {
        private final CCModel model;
        private final float offX, offY, offZ, scale;

        public ClassicArmorPart(CCModel model, RenderType renderType, float offX, float offY, float offZ, float scale) {
            super(model, renderType, offX, offY, offZ, scale);
            this.model = model;
            this.offX = offX;
            this.offY = offY;
            this.offZ = offZ;
            this.scale = scale;
        }

        /**
         * Bounds of the part in its bone frame: offset + scale * rotate(180, -1, 0, 1) (which maps (x, y, z) to
         * (-z, -y, -x)), as {@link CosmeticArmorModel.ObjPart#render} draws it.
         */
        public double[] boundsInBone() {
            double[] b = bounds(model);
            return new double[]{offX - b[5] * scale, offY - b[4] * scale, offZ - b[3] * scale, offX - b[2] * scale, offY - b[1] * scale, offZ - b[0] * scale};
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay, float r, float g, float b, float a) {
            if (shieldPass) return;
            super.render(poseStack, buffers, packedLight, packedOverlay, r, g, b, a);
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay, float r, float g, float b, float a) {}

    @Override
    protected Iterable<ModelPart> headParts() {
        return ImmutableList.of(head);
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
        return ImmutableList.of(body, leftArm, rightArm, leftLeg, rightLeg);
    }

    @Override
    public void render(LivingEntity entity, PoseStack poseStack, MultiBufferSource buffers, ItemStack stack, int packedLight, int packedOverlay, float partialTicks) {
        shieldColour = 0xFFFFFFFF;
        shieldState = 0;
        LazyOptional<ModuleHost> optionalHost = stack.getCapability(DECapabilities.MODULE_HOST_CAPABILITY);
        if (!stack.isEmpty() && optionalHost.isPresent()) {
            ModuleHost host = optionalHost.orElseThrow(IllegalStateException::new);
            ShieldControlEntity shieldControl = host.getEntitiesByType(ModuleTypes.SHIELD_CONTROLLER).map(e -> (ShieldControlEntity) e).findAny().orElse(null);
            if (shieldControl != null) {
                shieldState = shieldControl.getShieldState();
                shieldColour = shieldControl.getShieldColour() | 0xFF000000;
                if (entity instanceof Player player) {
                    ContributorProperties props = ContributorHandler.getProps(player);
                    if (props.hasShieldRGB() && props.getConfig().overrideShield()) {
                        shieldColour = props.getConfig().getShieldColour(partialTicks);
                    }
                }
            }
        }

        if (classic) {
            //PORT(1.19.2) classic: armor parts first; then, if the shield is up, flush them (vanilla buffer source) so the
            //shield shader (drawn immediately) is blended over the armor, and draw the shield parts.
            shieldPass = false;
            renderParts(poseStack, buffers, packedLight, packedOverlay);
            if (shieldState > 0) {
                if (!ShaderPackFallback.isActive() && buffers instanceof MultiBufferSource.BufferSource bufferSource) {
                    bufferSource.endLastBatch();
                }
                shieldPass = true;
                renderParts(poseStack, buffers, packedLight, packedOverlay);
                shieldPass = false;
            }
            return;
        }
        renderParts(poseStack, buffers, packedLight, packedOverlay);
    }

    private void renderParts(PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
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

    public class ChestpieceModelPart extends ExtendedModelPart {
        protected final BindingVBORenderType renderType;
        protected final BCShader<?> shader;
        //PORT(1.19.2) shader-pack fallback (vanilla entity buffers), see ShaderPackFallback
        protected final CCModel fallbackModel;
        protected RenderType fallbackType;
        protected int fallbackColour = 0xFFFFFFFF;
        protected boolean fallbackEmissive;

        public ChestpieceModelPart(CCModel model, RenderType baseType, BCShader<?> shader) {
            this.shader = shader;
            this.fallbackModel = ShaderPackFallback.toQuads(model);
            renderType = new BindingVBORenderType(baseType, (format, builder) -> {
                CCRenderState ccrs = CCRenderState.instance();
                ccrs.reset();
                ccrs.bind(builder, format);
                model.render(ccrs);
            });
        }

        public ChestpieceModelPart fallback(RenderType type, int rgba, boolean emissive) {
            this.fallbackType = type;
            this.fallbackColour = rgba;
            this.fallbackEmissive = emissive;
            return this;
        }

        /**
         * Draws the vanilla fallback of this part (pose already applied) when a shader pack is active.
         *
         * @return true if drawn (the shader path must then be skipped)
         */
        protected boolean renderFallback(Matrix4 mat, MultiBufferSource buffers, int packedLight, int packedOverlay, int rgba) {
            if (fallbackType == null || !ShaderPackFallback.isActive()) return false;
            ShaderPackFallback.render(fallbackModel, fallbackType, buffers, mat,
                    fallbackEmissive ? ShaderPackFallback.FULL_BRIGHT : packedLight,
                    fallbackEmissive ? ShaderPackFallback.NO_OVERLAY : packedOverlay, rgba);
            return true;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay, float r, float g, float b, float a) {
            if (this.visible) {
                poseStack.pushPose();
                this.translateAndRotate(poseStack);
                Matrix4 mat = new Matrix4(poseStack);
                if (renderFallback(mat, buffers, packedLight, packedOverlay, fallbackColour)) {
                    poseStack.popPose();
                    return;
                }
                renderType.withCallback(() -> {
                    ToolRenderBase.glUniformBaseColor(shader, techLevel, 1F);
                    shader.getModelMatUniform().glUniformMatrix4f(mat);
                }).drawImmediate();
                poseStack.popPose();
            }
        }
    }

    public class CoreGemModelPart extends ChestpieceModelPart {
        private final ToolShader shader;

        public CoreGemModelPart(CCModel model, RenderType baseType, ToolShader shader) {
            super(model, baseType, shader);
            this.shader = shader;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay, float r, float g, float b, float a) {
            if (this.visible) {
                poseStack.pushPose();
                this.translateAndRotate(poseStack);
                Matrix4 mat = new Matrix4(poseStack);
                int color = shieldColour;
                //Fallback: white emissive gem tinted with the shield colour (the gem shader's BaseColor)
                if (renderFallback(mat, buffers, packedLight, packedOverlay, ShaderPackFallback.argbToRgba(color, 0xFF))) {
                    poseStack.popPose();
                    return;
                }
                renderType.withCallback(() -> {
                    shader.getBaseColorUniform().glUniform4f(((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F, ((color >> 24) & 0xFF) / 255F);
                    shader.getModelMatUniform().glUniformMatrix4f(mat);
                }).drawImmediate();
                poseStack.popPose();
            }
        }
    }

    public class ShieldModelPart extends ChestpieceModelPart {
        private final ShieldShader shader;

        public ShieldModelPart(CCModel model, RenderType baseType, ShieldShader shader) {
            super(model, baseType, shader);
            this.shader = shader;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay, float r, float g, float b, float a) {
            if (classic && !shieldPass) return; //PORT(1.19.2) classic: shield parts are drawn in the second pass
            if (shieldState > 0) {
                poseStack.pushPose();
                this.translateAndRotate(poseStack);
                Matrix4 mat = new Matrix4(poseStack);
                int color = shieldColour;
                float state = shieldState;
                if (ShaderPackFallback.isActive()) {
                    //Fallback: translucent shell + energy swirl in the shield colour, scaled by the activation state
                    ShaderPackFallback.renderShield(fallbackModel, buffers, mat, color, state, (float) ClientUtils.getRenderTime());
                    poseStack.popPose();
                    return;
                }
                renderType.withCallback(() -> {
                    shader.getBaseColourUniform().glUniform4f(((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F, ((color >> 24) & 0xFF) / 255F);
                    shader.getActivationUniform().glUniform1f(state);
                    shader.getModelMatUniform().glUniformMatrix4f(mat);
                }).drawImmediate();
                poseStack.popPose();
            }
        }
    }
}
