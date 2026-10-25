package com.brandon3055.draconicevolution.client.render;

import codechicken.lib.render.CCModel;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.buffer.TransformingVertexConsumer;
import codechicken.lib.render.pipeline.attribute.NormalAttribute;
import codechicken.lib.vec.Transformation;
import codechicken.lib.vec.Vector3;
import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.brandonscore.client.render.ShaderModCompat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

import static com.brandon3055.draconicevolution.DraconicEvolution.MODID;

/**
 * PORT(1.19.2) shader-pack fallback (port-only, not upstream).
 * <p>
 * While an Oculus (Iris) shader pack is active, Oculus does not draw custom core shaders in the world pass, so the DE
 * chestpiece and the fancy 3D tool models (DE {@code TOOL_*} / {@code CHESTPIECE_*} shaders, BC {@code CHAOS_ENTITY})
 * are invisible. Vanilla entity render types are supported by shader packs, so in that case the same geometry is drawn
 * through vanilla entity buffers instead (same transforms, vanilla lighting/overlay, colours approximating the shaders).
 * GUI rendering keeps the shader path (shader packs do not affect GUIs). Without a shader pack nothing changes.
 * <p>
 * Shader-pack detection: BrandonsCore {@link ShaderModCompat#isShaderPackInUse()} (reflection on
 * {@code net.irisshaders.iris.api.v0.IrisApi}; always false without Oculus).
 */
public final class ShaderPackFallback {

    public static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    public static final ResourceLocation CHAOS = new ResourceLocation(MODID, "textures/item/equipment/chaos_shader.png");
    public static final ResourceLocation SWIRL = new ResourceLocation("minecraft", "textures/entity/creeper/creeper_armor.png");
    public static final int FULL_BRIGHT = 0xF000F0;
    public static final int NO_OVERLAY = OverlayTexture.NO_OVERLAY;

    private ShaderPackFallback() {}

    /**
     * @return true while an Oculus/Iris shader pack is in use (world rendering needs the vanilla fallback).
     */
    public static boolean isActive() {
        return ShaderModCompat.isShaderPackInUse();
    }

    /**
     * @return true when an item model drawn with this transform needs the fallback (shader pack active and not a GUI draw).
     */
    public static boolean isActive(ItemTransforms.TransformType transform) {
        return transform != ItemTransforms.TransformType.GUI && isActive();
    }

    /**
     * Texture for the "energy" parts (gems, traces, blades, power crystals). DE's {@code shader_fallback_<level>} textures
     * already carry the tech-level colour for wyvern/draconic; the chaotic one is almost black, so chaotic uses white
     * tinted with {@link #energyColour(TechLevel)}.
     */
    public static ResourceLocation energyTexture(TechLevel techLevel) {
        if (techLevel == TechLevel.CHAOTIC) return WHITE;
        return new ResourceLocation(MODID, "textures/item/equipment/shader_fallback_" + techLevel.name().toLowerCase(Locale.ROOT) + ".png");
    }

    /**
     * @return RGBA (CCL colour order) tint for {@link #energyTexture(TechLevel)}; chaotic uses the shaders' red base colour.
     */
    public static int energyColour(TechLevel techLevel) {
        return techLevel == TechLevel.CHAOTIC ? 0xE62E2EFF : 0xFFFFFFFF;
    }

    /**
     * @param argb colour in Minecraft ARGB order
     * @return the same colour in CCL RGBA order with the given alpha (0-255)
     */
    public static int argbToRgba(int argb, int alpha) {
        return (argb & 0xFFFFFF) << 8 | (alpha & 0xFF);
    }

    /**
     * Converts a (back-faced) triangle model as used by the VBO shader path into a quad model for vanilla entity buffers
     * (vanilla entity render types are {@link VertexFormat.Mode#QUADS}; each triangle becomes a degenerate quad, as CCL's
     * own {@code OBJParser.quads()} does). Normals: {@code backfacedCopy()}/{@code twoFacedCopy()} negate the OBJ normals
     * of the reversed faces; negating them back makes every face's normal point to the side it is visible from, which is
     * what vanilla entity lighting expects.
     */
    public static CCModel toQuads(CCModel src) {
        return toQuads(src, true);
    }

    /**
     * @param negateNormals true for models made with {@code backfacedCopy()}/{@code twoFacedCopy()} from OBJ normals,
     *                      false when the normals were computed after the copy ({@code computeNormals()})
     */
    public static CCModel toQuads(CCModel src, boolean negateNormals) {
        int vp = src.vp;
        int faces = src.verts.length / vp;
        CCModel dst = CCModel.quadModel(faces * 4);
        Vector3[] srcNormals = src.normals();
        Vector3[] dstNormals = srcNormals == null ? null : dst.getOrAllocate(NormalAttribute.attributeKey);
        for (int f = 0; f < faces; f++) {
            for (int i = 0; i < 4; i++) {
                int si = f * vp + Math.min(i, vp - 1);
                int di = f * 4 + i;
                dst.verts[di] = src.verts[si].copy();
                if (dstNormals != null && srcNormals[si] != null) {
                    dstNormals[di] = negateNormals ? srcNormals[si].copy().negate() : srcNormals[si].copy();
                }
            }
        }
        if (dstNormals == null) {
            dst.computeNormals();
        }
        return dst;
    }

    /**
     * Draws a quad model (see {@link #toQuads(CCModel)}) into a vanilla buffer.
     *
     * @param transform model transform (pose stack / item matrix), applied to positions and normals
     * @param rgba      vertex colour, CCL RGBA order
     */
    public static void render(CCModel quads, RenderType type, MultiBufferSource buffers, Transformation transform, int packedLight, int packedOverlay, int rgba) {
        CCRenderState ccrs = CCRenderState.instance();
        //Callers (e.g. RenderModularBow) keep using the shared render state after their parts were drawn.
        int brightness = ccrs.brightness;
        int overlay = ccrs.overlay;
        ccrs.reset();
        ccrs.computeLighting = false; //vanilla entity shader does the lighting
        ccrs.bind(new TransformingVertexConsumer(buffers.getBuffer(type), transform), type.format());
        ccrs.brightness = packedLight;
        ccrs.overlay = packedOverlay;
        ccrs.baseColour = rgba;
        quads.render(ccrs);
        ccrs.reset();
        ccrs.brightness = brightness;
        ccrs.overlay = overlay;
    }

    /**
     * Shield bubble: a faint translucent colour shell plus an animated vanilla energy swirl (charged-creeper style),
     * both scaled by the shield's activation state.
     *
     * @param argb  shield colour (ARGB)
     * @param state shield activation 0..1 ({@code ShieldControlEntity.getShieldState()})
     */
    public static void renderShield(CCModel quads, MultiBufferSource buffers, Transformation transform, int argb, float state, float time) {
        float s = Math.max(0F, Math.min(1F, state));
        if (s <= 0) return;
        int shellAlpha = (int) (255 * s * (0.22F + 0.06F * (float) Math.sin(time * 0.1F)));
        render(quads, RenderType.entityTranslucentEmissive(WHITE), buffers, transform, FULL_BRIGHT, NO_OVERLAY, argbToRgba(argb, shellAlpha));
        //Additive: brightness = colour * state
        int r = (int) (((argb >> 16) & 0xFF) * s * 0.8F);
        int g = (int) (((argb >> 8) & 0xFF) * s * 0.8F);
        int b = (int) ((argb & 0xFF) * s * 0.8F);
        float scroll = (time * 0.01F) % 1F;
        render(quads, RenderType.energySwirl(SWIRL, scroll, scroll), buffers, transform, FULL_BRIGHT, NO_OVERLAY, r << 24 | g << 16 | b << 8 | 0xFF);
    }
}
