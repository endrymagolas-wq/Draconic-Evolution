package com.brandon3055.draconicevolution.client.render.item;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.client.model.ModularChestpieceModel;
import com.brandon3055.draconicevolution.client.render.item.ClassicToolRenderer.Kind;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PORT(1.19.2): classic (DE 1.12.2) models. Checks the asset set used by {@link ClassicToolRenderer} and the classic
 * chestpiece, the 1.12.2 texture convention (top-down V: every face's UV centre must hit an opaque palette cell, which
 * fails for every model if V were flipped) and the bow frame rule.
 */
public class ClassicToolRendererTest {

    private static final String ASSETS = "/assets/draconicevolution/";
    private static final TechLevel[] TIERS = {TechLevel.WYVERN, TechLevel.DRACONIC, TechLevel.CHAOTIC};

    @Test
    public void bowFrameFollowsDrawProgress() {
        assertEquals(0, ClassicToolRenderer.bowFrame(0, 20));
        assertEquals(0, ClassicToolRenderer.bowFrame(6, 20));
        assertEquals(1, ClassicToolRenderer.bowFrame(7, 20));
        assertEquals(2, ClassicToolRenderer.bowFrame(14, 20));
        assertEquals(3, ClassicToolRenderer.bowFrame(20, 20));
        assertEquals(3, ClassicToolRenderer.bowFrame(200, 20));
        assertEquals(3, ClassicToolRenderer.bowFrame(5, 5));
        assertEquals(0, ClassicToolRenderer.bowFrame(-1, 20));
    }

    @Test
    public void geometryPerTier() {
        assertEquals("wyvern", ClassicToolRenderer.geometry(TechLevel.WYVERN, Kind.SWORD));
        assertEquals("wyvern", ClassicToolRenderer.geometry(TechLevel.WYVERN, Kind.BOW));
        assertEquals("draconic", ClassicToolRenderer.geometry(TechLevel.WYVERN, Kind.HOE));
        assertEquals("draconic", ClassicToolRenderer.geometry(TechLevel.CHAOTIC, Kind.PICKAXE));
        assertEquals("draconic", ClassicToolRenderer.geometry(TechLevel.DRACONIC, Kind.STAFF));
        assertEquals("wyvern", ModularChestpieceModel.classicGeometry(TechLevel.WYVERN));
        assertEquals("draconic", ModularChestpieceModel.classicGeometry(TechLevel.CHAOTIC));
    }

    @Test
    public void toolModelsAndTexturesMatch() throws IOException {
        int checked = 0;
        for (Kind kind : Kind.values()) {
            for (TechLevel tier : TIERS) {
                if (kind == Kind.STAFF && tier == TechLevel.WYVERN) continue; //no Wyvern staff item
                List<String> names = new ArrayList<>();
                if (kind == Kind.BOW) for (int i = 0; i < 4; i++) names.add("bow0" + i);
                else names.add(kind.model);
                for (String name : names) {
                    String obj = "models/item/classic/" + ClassicToolRenderer.geometry(tier, kind) + "_" + name + ".obj";
                    String tex = "textures/item/classic/" + ClassicToolRenderer.texture(tier) + "_" + name + ".png";
                    checkModel(obj, tex);
                    checked++;
                }
            }
        }
        assertEquals(29, checked);
    }

    @Test
    public void chestpiecePartsAndTexturesMatch() throws IOException {
        for (TechLevel tier : TIERS) {
            for (String part : new String[]{"body", "left_arm", "right_arm"}) {
                checkModel("models/cosmetic_armor/" + ModularChestpieceModel.classicGeometry(tier) + "_" + part + ".obj",
                        "textures/models/cosmetic_armor/" + tier.name().toLowerCase(Locale.ROOT) + "_" + part + ".png");
            }
        }
    }

    private static void checkModel(String obj, String tex) throws IOException {
        BufferedImage img;
        try (InputStream in = ClassicToolRendererTest.class.getResourceAsStream(ASSETS + tex)) {
            assertNotNull(in, "missing " + tex);
            img = ImageIO.read(in);
        }
        List<double[]> uvs = new ArrayList<>();
        int faces = 0;
        try (InputStream in = ClassicToolRendererTest.class.getResourceAsStream(ASSETS + obj)) {
            assertNotNull(in, "missing " + obj);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] s = line.trim().split("\\s+");
                if (s[0].equals("vt")) {
                    uvs.add(new double[]{Double.parseDouble(s[1]), Double.parseDouble(s[2])});
                } else if (s[0].equals("f")) {
                    double u = 0, v = 0;
                    for (int i = 1; i < s.length; i++) {
                        double[] uv = uvs.get(Integer.parseInt(s[i].split("/")[1]) - 1);
                        u += uv[0];
                        v += uv[1];
                    }
                    u /= s.length - 1;
                    v /= s.length - 1;
                    int x = Math.min(img.getWidth() - 1, (int) (u * img.getWidth()));
                    int y = Math.min(img.getHeight() - 1, (int) (v * img.getHeight())); //top-down V (1.12.2)
                    assertTrue((img.getRGB(x, y) >>> 24) > 0, obj + ": face " + faces + " samples a transparent texel of " + tex);
                    faces++;
                }
            }
        }
        assertTrue(faces > 100, obj + " has too few faces");
    }
}
