package com.brandon3055.draconicevolution.lib;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.damage.DraconicDamage;
import com.brandon3055.draconicevolution.api.damage.DraconicIndirectEntityDamage;
import com.brandon3055.draconicevolution.api.damage.IDraconicDamage;
import com.brandon3055.draconicevolution.init.DEDamage;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Created by brandon3055 on 5/07/2016.
 * <p>
 * 1.19.2 (WP-22, specs/WP-22-damage.md s.3.3): {@link DamageSource} implementations for {@link DEDamage.Type}.
 * Do not construct these directly; use the {@link DEDamage} factory methods.
 * (The 1.20 version of this file was fully commented out; its deprecated 1.16 content, CHAOS_ISLAND_IMPLOSION and
 * DamageSourceChaos, is superseded by DEDamage.CHAOS_IMPLOSION / DEDamage.GUARDIAN.)
 */
public final class DEDamageSources {
    private DEDamageSources() {}

    /** Implemented by every DE-created source; carries the 1.20 type identity. */
    public interface IDEDamageSource extends IDraconicDamage {
        DEDamage.Type getDEType();

        @Nullable
        @Override
        default TechLevel getTechLevel(@Nullable ItemStack stack) {
            return getDEType().techLevel();
        }
    }

    /** No entities (1.20: new DamageSource(holder)). */
    public static class PlainSource extends DamageSource implements IDEDamageSource {
        private final DEDamage.Type type;

        public PlainSource(DEDamage.Type type) {
            super(type.msgId());
            this.type = type;
            type.applyFlags(this);
        }

        @Override
        public DEDamage.Type getDEType() { return type; }

        @Override
        public float getFoodExhaustion() { return type.exhaustion(); }

        @Override
        public boolean scalesWithDifficulty() { return type.scales(getEntity()); }
    }

    /** /kill replacement: never uses the ".player" death-message variant (no such key is shipped). */
    public static class KillSource extends PlainSource {
        public KillSource(DEDamage.Type type) { super(type); }

        @Override
        public Component getLocalizedDeathMessage(LivingEntity victim) {
            return Component.translatable("death.attack." + msgId, victim.getDisplayName());
        }
    }

    /** direct == causing == attacker (1.20: new DamageSource(holder, attacker)). attacker must be non-null. */
    public static class AttackerSource extends DraconicDamage implements IDEDamageSource {
        private final DEDamage.Type type;

        public AttackerSource(DEDamage.Type type, Entity attacker) {
            super(type.msgId(), attacker, type.techLevel());
            this.type = type;
            type.applyFlags(this);
        }

        @Override
        public DEDamage.Type getDEType() { return type; }

        @Override
        public float getFoodExhaustion() { return type.exhaustion(); }

        @Override
        public boolean scalesWithDifficulty() { return type.scales(getEntity()); }
    }

    /** direct = projectile, causing = owner (1.20: new DamageSource(holder, projectile, owner)). */
    public static class ProjectileSource extends DraconicIndirectEntityDamage implements IDEDamageSource {
        private final DEDamage.Type type;

        public ProjectileSource(DEDamage.Type type, Entity projectile, @Nullable Entity owner) {
            super(type.msgId(), projectile, owner, type.techLevel());
            this.type = type;
            type.applyFlags(this);
        }

        @Override
        public DEDamage.Type getDEType() { return type; }

        @Override
        public float getFoodExhaustion() { return type.exhaustion(); }

        @Override
        public boolean scalesWithDifficulty() { return type.scales(getEntity()); } // owner, not the arrow
    }

    /**
     * direct = null, causing = owner (1.20 *_arrow_spoof). Deliberately NOT an IndirectEntityDamageSource and without a
     * direct entity or position, so EnderMan (instanceof Indirect), WitherBoss / dragon sitting phase / closed Shulker
     * (direct instanceof AbstractArrow) and vanilla shields (getSourcePosition) treat it like 1.20.1 does.
     * Exception: the Warden (1.20.1 uses isIndirect(), true for a spoof; here it is not Indirect) - accepted, the Warden
     * is not in the default DEConfig.projectileAntiImmuneEntities list.
     */
    public static class CausingOnlySource extends AttackerSource {
        public CausingOnlySource(DEDamage.Type type, Entity owner) { super(type, owner); }

        @Nullable
        @Override
        public Entity getDirectEntity() { return null; }

        @Nullable
        @Override
        public Vec3 getSourcePosition() { return null; }
    }
}
