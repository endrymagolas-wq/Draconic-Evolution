package com.brandon3055.draconicevolution.items.cosmetic;

import com.brandon3055.brandonscore.api.TechLevel;
import net.minecraft.world.item.Rarity;

import java.util.Locale;

/**
 * [1.19.2 port addition, not in upstream 1.20.1] Tiers of the purely cosmetic armor pieces (see PORT_NOTES.md,
 * "Additions not in upstream 1.20.1: cosmetic armor").
 * <p>
 * The 3D geometry comes from DE 1.12.2 ({@code models/cosmetic_armor/<geometry>_*.obj}). 1.12.2 only had Wyvern and
 * Draconic armor, so the Chaotic set reuses the Draconic geometry with recoloured textures.
 */
public enum CosmeticArmorTier {
    WYVERN(TechLevel.WYVERN, Rarity.UNCOMMON, "wyvern"),
    DRACONIC(TechLevel.DRACONIC, Rarity.RARE, "draconic"),
    CHAOTIC(TechLevel.CHAOTIC, Rarity.EPIC, "draconic");

    public final TechLevel techLevel;
    public final Rarity rarity;
    /** Prefix of the OBJ files (and of the 1.12.2 transforms) used by this tier. */
    public final String geometry;

    CosmeticArmorTier(TechLevel techLevel, Rarity rarity, String geometry) {
        this.techLevel = techLevel;
        this.rarity = rarity;
        this.geometry = geometry;
    }

    /** Item id prefix and texture prefix: wyvern, draconic, chaotic. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
