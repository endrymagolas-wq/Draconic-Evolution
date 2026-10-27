package com.brandon3055.draconicevolution.client.render.tile;

import codechicken.lib.render.CCModel;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.model.OBJParser;
import codechicken.lib.vec.Matrix4;
import codechicken.lib.vec.Rotation;
import codechicken.lib.vec.Vector3;
import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorComponent;
import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorInjector;
import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorStabilizer;
import com.brandon3055.draconicevolution.init.DEContent;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.brandon3055.draconicevolution.client.render.BakedVertexModel;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.covers1624.quack.collection.FastStream;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.Map;

import static codechicken.lib.math.MathHelper.torad;
import static com.brandon3055.draconicevolution.DraconicEvolution.MODID;

/**
 * Created by brandon3055 on 20/01/2017.
 */
public class RenderTileReactorComponent implements BlockEntityRenderer<TileReactorComponent> {

    private static final RenderType STAB_FRAME_TYPE = RenderType.entitySolid(new ResourceLocation(MODID, "textures/block/reactor/reactor_stabilizer.png"));
    private static final RenderType INJECTOR_FRAME_TYPE = RenderType.entitySolid(new ResourceLocation(MODID, "textures/block/reactor/reactor_injector.png"));

    private static final RenderType STAB_GLOW_TYPE = RenderType.create(MODID + ":stab_glow", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true, RenderType.CompositeState.builder()
            .setTextureState(new RenderStateShard.TextureStateShard(new ResourceLocation(MODID, "textures/block/reactor/reactor_stabilizer.png"), false, false))
            .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEntitySolidShader)) //TODO Does this shader work?
            .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
            .createCompositeState(false)
    );

    private static final RenderType INJECTOR_GLOW_TYPE = RenderType.create(MODID + ":injector_glow", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true, RenderType.CompositeState.builder()
            .setTextureState(new RenderStateShard.TextureStateShard(new ResourceLocation(MODID, "textures/block/reactor/reactor_injector.png"), false, false))
            .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEntitySolidShader)) //TODO Does this shader work?
            .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
            .createCompositeState(false)
    );

    private static CCModel modelInjectorBase;
    private static CCModel modelInjectorEmitters;

    private static CCModel modelStabFrame;
    private static CCModel modelStabInnerRotor;
    private static CCModel modelStabInnerRotorArm;
    private static CCModel modelStabOuterRotor;
    private static CCModel modelStabOuterRotorArm;
    private static CCModel modelStabRing;
    private static CCModel modelStabRingEmitter;

    private static CCModel modelInnerRotorPart;
    private static CCModel modelOuterRotorPart;

    static {
        Map<String, CCModel> map = new OBJParser(new ResourceLocation(MODID, "models/block/reactor/reactor_injector.obj")).quads().ignoreMtl().parse();
        modelInjectorBase = CCModel.combine(FastStream.of(map.entrySet())
                .filter(e -> !e.getKey().startsWith("emitter"))
                .map(Map.Entry::getValue)
                .toLinkedList()).backfacedCopy();
        modelInjectorEmitters = CCModel.combine(FastStream.of(map.entrySet())
                .filter(e -> e.getKey().startsWith("emitter"))
                .map(Map.Entry::getValue)
                .toLinkedList()).backfacedCopy();

        Map<String, CCModel> stabMap = new OBJParser(new ResourceLocation(MODID, "models/block/reactor/reactor_stabilizer.obj")).quads().ignoreMtl().parse();
        modelStabFrame = CCModel.combine(FastStream.of(stabMap.entrySet())
                .filter(e -> e.getKey().startsWith("frame"))
                .map(Map.Entry::getValue)
                .toLinkedList()).backfacedCopy();

        modelStabInnerRotor = CCModel.combine(FastStream.of(stabMap.entrySet())
                .filter(e -> e.getKey().startsWith("inner_rotor_blade"))
                .map(Map.Entry::getValue)
                .toLinkedList()).backfacedCopy();

        modelStabInnerRotorArm = CCModel.combine(FastStream.of(stabMap.entrySet())
                .filter(e -> e.getKey().startsWith("inner_rotor_arm"))
                .map(Map.Entry::getValue)
                .toLinkedList()).backfacedCopy();

        modelStabOuterRotor = CCModel.combine(FastStream.of(stabMap.entrySet())
                .filter(e -> e.getKey().startsWith("outer_rotor_blade"))
                .map(Map.Entry::getValue)
                .toLinkedList()).backfacedCopy();

        modelStabOuterRotorArm = CCModel.combine(FastStream.of(stabMap.entrySet())
                .filter(e -> e.getKey().startsWith("outer_rotor_arm"))
                .map(Map.Entry::getValue)
                .toLinkedList()).backfacedCopy();

        modelStabRing = CCModel.combine(FastStream.of(stabMap.entrySet())
                .filter(e -> e.getKey().startsWith("ring"))
                .map(Map.Entry::getValue)
                .toLinkedList()).backfacedCopy();

        modelStabRingEmitter = CCModel.combine(FastStream.of(stabMap.entrySet())
                .filter(e -> e.getKey().startsWith("focus_panel"))
                .map(Map.Entry::getValue)
                .toLinkedList()).backfacedCopy();

        modelInnerRotorPart = CCModel.combine(FastStream.of(stabMap.entrySet())
                .filter(e -> e.getKey().startsWith("inner_rotor_blade_a"))
                .map(Map.Entry::getValue)
                .toLinkedList()).backfacedCopy();

        modelOuterRotorPart = CCModel.combine(FastStream.of(stabMap.entrySet())
                .filter(e -> e.getKey().startsWith("outer_rotor_blade_a"))
                .map(Map.Entry::getValue)
                .toLinkedList()).backfacedCopy();
    }

    public RenderTileReactorComponent(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TileReactorComponent te, float partialTicks, PoseStack mStack, MultiBufferSource getter, int packedLight, int packedOverlay) {
        Matrix4 mat = new Matrix4(mStack);
        mat.translate(0.5, 0, 0.5);

        CCRenderState ccrs = CCRenderState.instance();
        ccrs.reset();
        ccrs.brightness = packedLight;
        ccrs.overlay = packedOverlay;

        switch (te.facing.get()) {
            case SOUTH -> mat.rotate(180 * torad, Vector3.Y_POS);
            case EAST -> mat.rotate(-90 * torad, Vector3.Y_POS);
            case WEST -> mat.rotate(90 * torad, Vector3.Y_POS);
            case UP -> mat.apply(new Rotation(90 * torad, Vector3.X_POS).at(new Vector3(0, 0.5, 0)));
            case DOWN -> mat.apply(new Rotation(-90 * torad, Vector3.X_POS).at(new Vector3(0, 0.5, 0)));
        }

        if (te instanceof TileReactorStabilizer) {
            float coreRotation = te.animRotation + (partialTicks * te.animRotationSpeed);//Remember Partial Ticks here
            renderStabilizer(ccrs, mat, getter, coreRotation, te.animRotationSpeed / 15F, packedLight, packedOverlay);
        } else if (te instanceof TileReactorInjector) {
            renderInjector(ccrs, mat, getter, te.animRotationSpeed / 15F, packedLight, packedOverlay);
        }

        ccrs.reset();
    }

    //PORT(1.19.2) perf: baked once, written straight to the buffer (see BakedVertexModel); drawn for every component every frame.
    private static BakedVertexModel bInjectorBase, bInjectorEmitters, bStabFrame, bStabInnerRotor, bStabInnerRotorArm, bStabOuterRotor, bStabOuterRotorArm, bStabRing, bStabRingEmitter;

    private static void bake() {
        if (bStabFrame != null) return;
        bInjectorBase = new BakedVertexModel(modelInjectorBase);
        bInjectorEmitters = new BakedVertexModel(modelInjectorEmitters);
        bStabInnerRotor = new BakedVertexModel(modelStabInnerRotor);
        bStabInnerRotorArm = new BakedVertexModel(modelStabInnerRotorArm);
        bStabOuterRotor = new BakedVertexModel(modelStabOuterRotor);
        bStabOuterRotorArm = new BakedVertexModel(modelStabOuterRotorArm);
        bStabRing = new BakedVertexModel(modelStabRing);
        bStabRingEmitter = new BakedVertexModel(modelStabRingEmitter);
        bStabFrame = new BakedVertexModel(modelStabFrame);
    }

    public static void renderStabilizer(CCRenderState ccrs, Matrix4 mat, MultiBufferSource getter, float coreRotation, float brightness, int packedLight, int packedOverlay) {
        bake();
        float ringRotation = coreRotation * -0.5F;//Remember Partial Ticks here
        int glowLight = (int) (brightness * 240);
        int overlay = packedOverlay;

        Matrix4 innerRotorMat = mat.copy();
        innerRotorMat.apply(new Rotation(coreRotation * torad, Vector3.Z_POS).at(new Vector3(0, 0.5, 0)));
        Matrix4 outerRotorMat = mat.copy();
        outerRotorMat.apply(new Rotation(coreRotation * torad * -2, Vector3.Z_POS).at(new Vector3(0, 0.5, 0)));

        VertexConsumer frame = getter.getBuffer(STAB_FRAME_TYPE);
        bStabFrame.render(frame, mat, -1, packedLight, overlay);
        bStabInnerRotorArm.render(frame, innerRotorMat, -1, packedLight, overlay);
        bStabOuterRotorArm.render(frame, outerRotorMat, -1, packedLight, overlay);

        Matrix4[] emitterMats = new Matrix4[4];
        for (int i = 0; i < 4; i++) {
            Matrix4 ringMat = mat.copy();
            ringMat.apply(new Rotation(((90 * i) + ringRotation) * torad, Vector3.Z_POS).at(new Vector3(0, 0.5, 0)));
            bStabRing.render(frame, ringMat, -1, packedLight, overlay);

            Matrix4 emitterMat = ringMat.copy();
            emitterMat.apply(new Rotation(45 * torad, Vector3.X_NEG).at(new Vector3(0, 15 / 16F, -((8 + 1.5) / 16F))));
            emitterMats[i] = emitterMat;
            bStabRingEmitter.render(frame, emitterMat, -1, glowLight, overlay);
        }

        bStabInnerRotor.render(frame, innerRotorMat, -1, glowLight, overlay);
        bStabOuterRotor.render(frame, outerRotorMat, -1, glowLight, overlay);

        if (brightness >= 1) {
            VertexConsumer glow = getter.getBuffer(STAB_GLOW_TYPE);
            bStabInnerRotor.render(glow, innerRotorMat, -1, glowLight, overlay);
            bStabOuterRotor.render(glow, outerRotorMat, -1, glowLight, overlay);
            for (int i = 0; i < 4; i++) {
                bStabRingEmitter.render(glow, emitterMats[i], -1, glowLight, overlay);
            }
        }
    }

    public static void renderInjector(CCRenderState ccrs, Matrix4 mat, MultiBufferSource getter, float brightness, int packedLight, int packedOverlay) {
        bake();
        int glowLight = (int) (brightness * 240);
        VertexConsumer frame = getter.getBuffer(INJECTOR_FRAME_TYPE);
        bInjectorBase.render(frame, mat, -1, packedLight, packedOverlay);
        bInjectorEmitters.render(frame, mat, -1, glowLight, packedOverlay);

        if (brightness >= 1) {
            bInjectorEmitters.render(getter.getBuffer(INJECTOR_GLOW_TYPE), mat, -1, glowLight, packedOverlay);
        }
    }

    public static void renderComponent(Item item, CCRenderState ccrs, Matrix4 mat, MultiBufferSource getter, int packedLight, int packedOverlay) {
        if (item == DEContent.REACTOR_PRT_STAB_FRAME.get()) {
            ccrs.bind(STAB_FRAME_TYPE, getter);
            mat.translate(0.5, 0, 0.5);
            modelStabFrame.render(ccrs, mat);

        } else if (item == DEContent.REACTOR_PRT_IN_ROTOR.get()) {
            ccrs.bind(STAB_FRAME_TYPE, getter);
            mat.translate(0.3, 0, 0.5);
            mat.scale(1.5F, 1.5F, 1.5F);
            modelInnerRotorPart.render(ccrs, mat);

        } else if (item == DEContent.REACTOR_PRT_OUT_ROTOR.get()) {
            ccrs.bind(STAB_FRAME_TYPE, getter);
            mat.translate(0.3, 0, 0.5);
            mat.scale(1.5F, 1.5F, 1.5F);
            modelOuterRotorPart.render(ccrs, mat);

        } else if (item == DEContent.REACTOR_PRT_ROTOR_FULL.get()) {
            ccrs.bind(STAB_FRAME_TYPE, getter);
            mat.translate(0.5, -0.2, 0.5);
            mat.scale(1.5F, 1.5F, 1.5F);

            modelStabInnerRotor.render(ccrs, mat);
            modelStabInnerRotorArm.render(ccrs, mat);
            mat.apply(new Rotation(60 * torad, Vector3.Z_NEG).at(new Vector3(0, 0.5, 0)));
            modelStabOuterRotor.render(ccrs, mat);
            modelStabOuterRotorArm.render(ccrs, mat);

        } else if (item == DEContent.REACTOR_PRT_FOCUS_RING.get()) {
            ccrs.bind(STAB_FRAME_TYPE, getter);
            mat.translate(0.5, -0.2, 1.25);
            mat.scale(1.5F, 1.5F, 1.5F);

            for (int i = 0; i < 4; i++) {
                Matrix4 ringMat = mat.copy();
                ringMat.apply(new Rotation((90 * i) * torad, Vector3.Z_POS).at(new Vector3(0, 0.5, 0)));
                modelStabRing.render(ccrs, ringMat);

                Matrix4 emitterMat = ringMat.copy();
                emitterMat.apply(new Rotation(45 * torad, Vector3.X_NEG).at(new Vector3(0, 15 / 16F, -((8 + 1.5) / 16F))));
                modelStabRingEmitter.render(ccrs, emitterMat);
            }
        }
    }
}
