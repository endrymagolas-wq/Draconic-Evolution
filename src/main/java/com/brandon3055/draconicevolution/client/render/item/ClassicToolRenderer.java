package com.brandon3055.draconicevolution.client.render.item;

import codechicken.lib.model.PerspectiveModelState;
import codechicken.lib.render.CCModel;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.buffer.TransformingVertexConsumer;
import codechicken.lib.render.item.IItemRenderer;
import codechicken.lib.vec.Matrix4;
import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.client.model.classic.ClassicObj;
import com.brandon3055.draconicevolution.items.equipment.ModularBow;
import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms.TransformType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static codechicken.lib.util.TransformUtils.create;
import static codechicken.lib.util.TransformUtils.flipLeft;
import static com.brandon3055.draconicevolution.DraconicEvolution.MODID;

/**
 * [1.19.2 port addition, not in upstream 1.20.1] Classic (DE 1.12.2) look of the modular tools
 * ({@code DEConfig.classicModels}, with {@code fancyToolModels}).
 * <p>
 * Port of the 1.12.2 tool rendering ({@code client/model/tool/ToolBakedModel}, {@code ToolOverrideList},
 * {@code ToolTransforms}, the items' {@code getModels}):
 * <ul>
 *     <li>GUI: the flat 2D item sprite. The DE 1.20.1 sprites ({@code textures/item/tools/*}) are the 1.12.2 sprites, so
 *     the item's own JSON model is drawn (captured before this renderer replaces it, {@link #setGuiModel}), through the
 *     vanilla item quad path.</li>
 *     <li>Everywhere else: the 1.12.2 OBJ model ({@code models/item/classic/*.obj}, unchanged copies) with its 1.12.2
 *     palette texture ({@code textures/item/classic/*.png}), drawn every frame into the plain vanilla
 *     {@code entityCutoutNoCull} buffer (+ the vanilla glint if enchanted). No DE shader, no VBO.</li>
 *     <li>Transforms: 1.12.2 CCL {@code TransformUtils.DEFAULT_TOOL} (hands; GROUND from {@code DEFAULT_ITEM}; FIXED,
 *     HEAD and GUI identity), with the 1.12.2 FIXED/GROUND overrides of the sword ({@code DR_SWORD_STATE} /
 *     {@code WY_SWORD_STATE}) and the staff ({@code STAFF_STATE}). The 1.12.2 baked OBJ was drawn after the transform and
 *     the item {@code translate(-0.5, -0.5, -0.5)}, exactly as CCL's {@link IItemRenderer} path does here.</li>
 *     <li>Bow: four draw frames {@code bow00..03}, frame = min(floor(ticksUsing / drawTicks * 3), 3) as 1.12.2
 *     {@code ToolOverrideList}, with the modular bow's charge time ({@link ModularBow#getChargeTicks}) as draw time.</li>
 * </ul>
 * Tiers: Chaotic (no 1.12.2 tools) = Draconic geometry with the recoloured textures of {@code tools/cosmetic/recolor.py};
 * Wyvern hoe (no 1.12.2 Wyvern hoe) = Draconic hoe geometry with a Wyvern-palette texture made by the same script.
 */
public class ClassicToolRenderer implements IItemRenderer {

    public enum Kind {
        SWORD("sword"), PICKAXE("pickaxe"), AXE("axe"), SHOVEL("shovel"), HOE("hoe"), STAFF("staff_of_power"), BOW("bow");

        public final String model;

        Kind(String model) {
            this.model = model;
        }
    }

    private static final Transformation TP_RIGHT = create(0F, 4F, 0.5F, 0F, -90F, 55F, 0.85F);
    private static final Transformation FP_RIGHT = create(1.13F, 3.2F, 1.13F, 0F, -90F, 25F, 0.68F);

    private final Kind kind;
    private final CCModel[] frames;
    private final RenderType[] types;
    private final PerspectiveModelState state;
    @Nullable
    private BakedModel guiModel;
    @Nullable
    private LivingEntity entity;

    public ClassicToolRenderer(TechLevel techLevel, Kind kind) {
        this.kind = kind;
        String[] names = kind == Kind.BOW ? new String[]{"bow00", "bow01", "bow02", "bow03"} : new String[]{kind.model};
        frames = new CCModel[names.length];
        types = new RenderType[names.length];
        String geometry = geometry(techLevel, kind);
        String texture = texture(techLevel);
        for (int i = 0; i < names.length; i++) {
            frames[i] = ClassicObj.load(new ResourceLocation(MODID, "models/item/classic/" + geometry + "_" + names[i] + ".obj"));
            types[i] = RenderType.entityCutoutNoCull(new ResourceLocation(MODID, "textures/item/classic/" + texture + "_" + names[i] + ".png"));
        }
        state = modelState(kind, geometry);
    }

    /** 1.12.2 geometry: Wyvern tools exist for sword/pickaxe/axe/shovel/bow; everything else uses the Draconic models. */
    public static String geometry(TechLevel techLevel, Kind kind) {
        boolean wyvernModel = kind != Kind.HOE && kind != Kind.STAFF;
        return techLevel == TechLevel.WYVERN && wyvernModel ? "wyvern" : "draconic";
    }

    public static String texture(TechLevel techLevel) {
        return techLevel.name().toLowerCase(Locale.ROOT);
    }

    /** 1.12.2 ToolOverrideList: draw frame 0..3 from the ticks the bow has been drawn and its full draw time. */
    public static int bowFrame(int ticksUsing, int drawTicks) {
        if (ticksUsing <= 0) return 0;
        return Math.min((int) Math.floor((double) ticksUsing / Math.max(1, drawTicks) * 3D), 3);
    }

    /**
     * 1.12.2 transforms. Base = CCL 1.12.x {@code DEFAULT_TOOL} (+ GROUND of {@code DEFAULT_ITEM}, which 1.12.2
     * {@code ToolBakedModel} used for GROUND); missing types are identity. Sword / staff: FIXED and GROUND from
     * {@code ToolTransforms}.
     */
    public static PerspectiveModelState modelState(Kind kind, String geometry) {
        Transformation ground = create(0F, 2F, 0F, 0F, 0F, 0F, 0.5F);
        Transformation fixed = null;
        if (kind == Kind.STAFF) {
            ground = create(1F, 4F, 0F, 0F, 0F, 0F, 0.3F);
            fixed = create(2F, 2F, 0F, 0F, 0F, 0F, 0.55F);
        } else if (kind == Kind.SWORD && geometry.equals("draconic")) {
            ground = create(-2F, 0F, 0F, 0F, 0F, 0F, 0.4F);
            fixed = create(-3F, -3F, 0F, 0F, 0F, 0F, 0.65F);
        } else if (kind == Kind.SWORD) {
            ground = create(-2F, 1F, 0F, 0F, 0F, 0F, 0.4F);
            fixed = create(-2F, -2F, 0F, 0F, 0F, 0F, 0.8F);
        }
        ImmutableMap.Builder<TransformType, Transformation> map = ImmutableMap.builder();
        map.put(TransformType.GROUND, ground);
        if (fixed != null) map.put(TransformType.FIXED, fixed);
        map.put(TransformType.THIRD_PERSON_RIGHT_HAND, TP_RIGHT);
        map.put(TransformType.THIRD_PERSON_LEFT_HAND, flipLeft(TP_RIGHT));
        map.put(TransformType.FIRST_PERSON_RIGHT_HAND, FP_RIGHT);
        map.put(TransformType.FIRST_PERSON_LEFT_HAND, flipLeft(FP_RIGHT));
        return new PerspectiveModelState(map.build());
    }

    /** The item's JSON (2D sprite) model, captured during model baking before this renderer replaces it. */
    public void setGuiModel(@Nullable BakedModel guiModel) {
        this.guiModel = guiModel instanceof IItemRenderer ? null : guiModel;
    }

    private final ItemOverrides overrides = new ItemOverrides() {
        @Override
        public BakedModel resolve(BakedModel originalModel, ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
            ClassicToolRenderer.this.entity = entity;
            return originalModel;
        }
    };

    @Override
    public ItemOverrides getOverrides() {
        return kind == Kind.BOW ? overrides : ItemOverrides.EMPTY;
    }

    @Override
    public void renderItem(ItemStack stack, TransformType transformType, PoseStack mStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (transformType == TransformType.GUI && guiModel != null) {
            //Same as vanilla ItemRenderer.render for a non-custom model (pose already transformed and translated by -0.5)
            ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
            for (BakedModel pass : guiModel.getRenderPasses(stack, true)) {
                for (RenderType type : pass.getRenderTypes(stack, true)) {
                    itemRenderer.renderModelLists(pass, stack, packedLight, packedOverlay, mStack, ItemRenderer.getFoilBufferDirect(buffers, type, true, stack.hasFoil()));
                }
            }
            return;
        }

        int frame = kind == Kind.BOW ? currentBowFrame(stack) : 0;
        VertexConsumer consumer = ItemRenderer.getFoilBufferDirect(buffers, types[frame], false, stack.hasFoil());
        CCRenderState ccrs = CCRenderState.instance();
        ccrs.reset();
        ccrs.computeLighting = false; //vanilla entity shader does the lighting
        ccrs.bind(new TransformingVertexConsumer(consumer, new Matrix4(mStack)), DefaultVertexFormat.NEW_ENTITY);
        ccrs.brightness = packedLight;
        ccrs.overlay = packedOverlay;
        ccrs.baseColour = 0xFFFFFFFF;
        frames[frame].render(ccrs);
        ccrs.reset();
    }

    private int currentBowFrame(ItemStack stack) {
        LivingEntity user = entity;
        if (user == null || !user.isUsingItem() || user.getUseItem() != stack) return 0;
        int drawTicks = stack.getCapability(DECapabilities.MODULE_HOST_CAPABILITY).isPresent() ? ModularBow.getChargeTicks(stack) : 20;
        return bowFrame(user.getTicksUsingItem(), drawTicks);
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return guiModel != null ? guiModel.getParticleIcon() : IItemRenderer.super.getParticleIcon();
    }

    @Override
    public PerspectiveModelState getModelState() {
        return state;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean usesBlockLight() {
        return false;
    }
}
