package com.brandon3055.draconicevolution.client.render;

import codechicken.lib.render.CCModel;
import codechicken.lib.render.pipeline.attribute.NormalAttribute;
import codechicken.lib.vec.Vector3;
import codechicken.lib.vec.Vertex5;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PORT(1.19.2): geometry conversion used by the shader-pack fallback (triangle VBO models -> vanilla QUADS buffers).
 */
public class ShaderPackFallbackTest {

    @Test
    public void triangleModelBecomesDegenerateQuads() {
        CCModel tri = CCModel.triModel(6);
        for (int i = 0; i < 6; i++) {
            tri.verts[i] = new Vertex5(i, i * 2, i * 3, i / 10D, i / 20D);
        }
        Vector3[] normals = tri.getOrAllocate(NormalAttribute.attributeKey);
        for (int i = 0; i < 6; i++) {
            normals[i] = new Vector3(0, 0, i < 3 ? 1 : -1);
        }

        CCModel quads = ShaderPackFallback.toQuads(tri);
        assertEquals(4, quads.vp);
        assertEquals(8, quads.verts.length);
        int[] expected = { 0, 1, 2, 2, 3, 4, 5, 5 };
        for (int i = 0; i < 8; i++) {
            assertEquals(tri.verts[expected[i]].vec.x, quads.verts[i].vec.x, 1e-9, "vertex " + i);
            assertEquals(tri.verts[expected[i]].vec.z, quads.verts[i].vec.z, 1e-9, "vertex " + i);
            assertEquals(tri.verts[expected[i]].uv.u, quads.verts[i].uv.u, 1e-9);
            //back-faced normals are negated back
            assertEquals(-normals[expected[i]].z, quads.normals()[i].z, 1e-9);
        }
        //source untouched
        assertEquals(1, tri.normals()[0].z, 1e-9);
    }

    @Test
    public void argbToRgba() {
        assertEquals(0x8C00A5FF, ShaderPackFallback.argbToRgba(0xFF8C00A5, 0xFF));
        assertEquals(0x12345680, ShaderPackFallback.argbToRgba(0x00123456, 0x80));
    }
}
