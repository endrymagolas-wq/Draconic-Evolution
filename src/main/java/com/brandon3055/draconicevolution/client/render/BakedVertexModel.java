package com.brandon3055.draconicevolution.client.render;

import codechicken.lib.render.CCModel;
import codechicken.lib.vec.Matrix4;
import codechicken.lib.vec.Vector3;
import codechicken.lib.vec.Vertex5;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;

/**
 * PORT(1.19.2) perf: a CCModel baked once into x,y,z,u,v,nx,ny,nz floats and written straight to a vanilla buffer.
 * Block entity models (reactor stabilizers/injector, pylon spheres, the reactor core fallback) are drawn every frame;
 * going through the CCRenderState pipeline for every vertex was a large part of the render thread's time.
 * Same output as {@code ccrs.bind(type, getter); model.render(ccrs, mat)} for models without per-vertex colour/lighting.
 */
public final class BakedVertexModel {
    private static final int STRIDE = 8;
    private final float[] data;

    public BakedVertexModel(CCModel model) {
        Vector3[] normals = model.normals();
        data = new float[model.verts.length * STRIDE];
        for (int i = 0; i < model.verts.length; i++) {
            Vertex5 v = model.verts[i];
            Vector3 n = normals != null && normals[i] != null ? normals[i] : Vector3.Y_POS;
            int o = i * STRIDE;
            data[o] = (float) v.vec.x;
            data[o + 1] = (float) v.vec.y;
            data[o + 2] = (float) v.vec.z;
            data[o + 3] = (float) v.uv.u;
            data[o + 4] = (float) v.uv.v;
            data[o + 5] = (float) n.x;
            data[o + 6] = (float) n.y;
            data[o + 7] = (float) n.z;
        }
    }

    /**
     * @param rgba colour packed as CCL {@code Colour.packRGBA} (0xRRGGBBAA), the same value CCRenderState.baseColour takes.
     */
    public void render(VertexConsumer buffer, Matrix4 mat, int rgba, int light, int overlay) {
        render(buffer, mat.toMatrix4f(), ((rgba >>> 24) & 0xFF) / 255F, ((rgba >> 16) & 0xFF) / 255F, ((rgba >> 8) & 0xFF) / 255F, (rgba & 0xFF) / 255F, light, overlay);
    }

    public void render(VertexConsumer buffer, Matrix4f pose, float r, float g, float b, float a, int light, int overlay) {
        Matrix3f normalMat = new Matrix3f(pose);
        Vector3f normal = new Vector3f();
        Vector4f pos = new Vector4f();
        float[] d = data;
        for (int o = 0; o < d.length; o += STRIDE) {
            pos.set(d[o], d[o + 1], d[o + 2], 1F);
            pos.transform(pose);
            normal.set(d[o + 5], d[o + 6], d[o + 7]);
            normal.transform(normalMat);
            normal.normalize();
            //Full-vertex call: BufferBuilder takes its fast path for NEW_ENTITY and falls back to the element-wise writes otherwise.
            buffer.vertex(pos.x(), pos.y(), pos.z(), r, g, b, a, d[o + 3], d[o + 4], overlay, light, normal.x(), normal.y(), normal.z());
        }
    }
}
