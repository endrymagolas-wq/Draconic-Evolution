package com.brandon3055.draconicevolution.client.model.classic;

import codechicken.lib.render.CCModel;
import codechicken.lib.render.model.OBJParser;
import codechicken.lib.vec.Vertex5;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * [1.19.2 port addition, not in upstream 1.20.1] Loader for the DE 1.12.2 OBJ models (cosmetic armor, classic
 * chestpiece, classic tools).
 * <p>
 * The models are returned as QUADS (triangles become degenerate quads) so they can go into vanilla entity buffers.
 * The 1.12.2 OBJs carry no normals (flat normals are computed, as the 1.12.2 Forge OBJ loader did; every model is wound
 * counter-clockwise, outward) and use a top-down texture V (the 1.12.2 Forge OBJ loader did not flip V), while CCL's
 * parser flips V (OBJ convention), so V is flipped back here. The .mtl files are not used: each 1.12.2 model has
 * exactly one texture (a small palette PNG), chosen by the caller.
 */
public final class ClassicObj {

    private ClassicObj() {}

    public static CCModel load(ResourceLocation obj) {
        Map<String, CCModel> map = new OBJParser(obj).ignoreMtl().quads().parse();
        CCModel model = CCModel.combine(map.values());
        for (Vertex5 v : model.verts) {
            v.uv.v = 1 - v.uv.v;
        }
        return model.computeNormals();
    }
}
