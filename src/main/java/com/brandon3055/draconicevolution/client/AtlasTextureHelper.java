package com.brandon3055.draconicevolution.client;

import com.brandon3055.draconicevolution.DraconicEvolution;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.brandon3055.draconicevolution.DraconicEvolution.MODID;

/**
 * Created by brandon3055 on 02/02/2024
 */
public class AtlasTextureHelper {
    private static final Map<ResourceLocation, Function<ResourceLocation, TextureAtlasSprite>> ATLAS_CACHE = new HashMap<>();

    public static TextureAtlasSprite[] ENERGY_PARTICLE = new TextureAtlasSprite[5];
    public static TextureAtlasSprite[] SPARK_PARTICLE = new TextureAtlasSprite[7];
    public static TextureAtlasSprite[] SPELL_PARTICLE = new TextureAtlasSprite[7];
    public static TextureAtlasSprite[] MIXED_PARTICLE;

    public static TextureAtlasSprite ORB_PARTICLE;
    public static TextureAtlasSprite PORTAL_PARTICLE;
    public static TextureAtlasSprite ENERGY_CORE_OVERLAY;

    public static ParticleRenderType PARTICLE_SHEET_TRANSLUCENT = new ParticleRenderType() {
        public void begin(BufferBuilder builder, TextureManager manager) {
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        public void end(Tesselator tessellator) {
            tessellator.end();
        }

        public String toString() {
            return "TERRAIN_SHEET_TRANSLUCENT";
        }
    };

    public static void init(IEventBus modBus) {
        modBus.addListener(AtlasTextureHelper::textureStitchPre); //1.19.2: there are no atlas json files, sprites that no model / particle json references are added here
        modBus.addListener(AtlasTextureHelper::textureStitch);
    }

    /**
     * 1.19.2: particle atlas sprite ids carry the "particle/" prefix (ns:particle/name -> ns:textures/particle/name.png).
     * (In 1.20.1 the vanilla particles atlas json adds the "particle" directory with an empty prefix.)
     */
    private static ResourceLocation particle(String name) {
        return new ResourceLocation(DraconicEvolution.MODID, "particle/" + name);
    }

    /**
     * 1.19.2 replacement for the 1.20.1 atlas json sources.
     * The particle atlas only contains textures listed in particle json files, and the block atlas only textures referenced by models,
     * so the sprites DE looks up directly are added here. Fires on the resource reload worker thread: only touch the event.
     */
    private static void textureStitchPre(TextureStitchEvent.Pre event) {
        ResourceLocation loc = event.getAtlas().location();
        if (loc.equals(TextureAtlas.LOCATION_PARTICLES)) {
            for (int i = 0; i < ENERGY_PARTICLE.length; i++) event.addSprite(particle("energy_" + i));
            for (int i = 0; i < SPARK_PARTICLE.length; i++) event.addSprite(particle("spark_" + i));
            for (int i = 0; i < SPELL_PARTICLE.length; i++) event.addSprite(particle("spell_" + i));
            event.addSprite(particle("white_orb"));
            event.addSprite(particle("portal"));
        } else if (loc.equals(InventoryMenu.BLOCK_ATLAS)) {
            event.addSprite(new ResourceLocation(DraconicEvolution.MODID, "block/energy_core/energy_core_overlay"));
        }
    }

    private static void textureStitch(TextureStitchEvent.Post event) {
        TextureAtlas atlas = event.getAtlas();
        if (atlas.location().equals(TextureAtlas.LOCATION_PARTICLES)) {
            ATLAS_CACHE.clear();
            for (int i = 0; i < ENERGY_PARTICLE.length; i++) {
                ENERGY_PARTICLE[i] = atlas.getSprite(particle("energy_" + i));
            }
            for (int i = 0; i < SPARK_PARTICLE.length; i++) {
                SPARK_PARTICLE[i] = atlas.getSprite(particle("spark_" + i));
            }
            for (int i = 0; i < SPELL_PARTICLE.length; i++) {
                SPELL_PARTICLE[i] = atlas.getSprite(particle("spell_" + i));
            }
            MIXED_PARTICLE = Stream.concat(Arrays.stream(SPARK_PARTICLE), Arrays.stream(SPELL_PARTICLE)).toArray(TextureAtlasSprite[]::new);

            ORB_PARTICLE = atlas.getSprite(particle("white_orb"));
            PORTAL_PARTICLE = atlas.getSprite(particle("portal"));
        }
        if (atlas.location().equals(InventoryMenu.BLOCK_ATLAS)) {
            ENERGY_CORE_OVERLAY = atlas.getSprite(new ResourceLocation(DraconicEvolution.MODID, "block/energy_core/energy_core_overlay"));
        }
    }
}
