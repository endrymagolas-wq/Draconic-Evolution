package com.brandon3055.draconicevolution.init;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.IDraconicMelee;
import com.brandon3055.draconicevolution.api.damage.IDraconicDamage;
import com.brandon3055.draconicevolution.lib.DEDamageSources;
import com.brandon3055.draconicevolution.lib.DEDamageSources.IDEDamageSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

import static com.brandon3055.draconicevolution.DraconicEvolution.MODID;

/**
 * Created by brandon3055 on 13/01/2024
 * <p>
 * 1.19.2 backport (WP-22, specs/WP-22-damage.md §3) of the 1.20 data-driven damage types
 * (data/draconicevolution/damage_type/*.json + the damage_type tags). 1.19.2 has no damage_type registry, so each
 * {@link Type} mirrors one JSON file and its tag memberships, and the {@link DamageSource} objects are built by
 * {@link DEDamageSources}. The factory method names and parameters are the same as in 1.20 ({@code Level} is accepted
 * and ignored, it may be {@code null}).
 * <p>
 * Call-site rule: {@code src.is(DEDamage.X)} becomes {@code DEDamage.is(src, DEDamage.X)} and
 * {@code src.is(DEDamage.Tags.X)} becomes {@code DEDamage.hasTag(src, DEDamage.Tags.X)}.
 */
public class DEDamage {

    /** 1.20 damage_type "scaling" field. */
    public enum Scaling {NEVER, WHEN_CAUSED_BY_LIVING_NON_PLAYER, ALWAYS}

    /** The 1.20 #minecraft:* damage_type tags DE uses. */
    public enum Flag {IS_EXPLOSION, IS_PROJECTILE, BYPASSES_ARMOR, BYPASSES_SHIELD, BYPASSES_EFFECTS, BYPASSES_RESISTANCE, BYPASSES_INVULNERABILITY, BYPASSES_COOLDOWN}

    /** The 1.20 #draconicevolution:* damage_type tags. */
    public enum TypeTag {CHAOTIC, DRACONIC, WYVERN, DRACONIUM, PROJECTILE_ANTI_DODGE}

    /** One 1.20 damage type (one JSON file in data/draconicevolution/damage_type/ plus its tag memberships). */
    public static final class Type {
        private final ResourceLocation id;
        private final String msgId;
        private final Scaling scaling;
        private final float exhaustion;
        private final boolean burningEffect; // 1.20 "effects": "burning" (hurt sound only), no 1.19.2 equivalent
        private final Set<Flag> flags;
        private final Set<TypeTag> tags;

        public Type(ResourceLocation id, String msgId, Scaling scaling, float exhaustion, boolean burningEffect, Set<Flag> flags, Set<TypeTag> tags) {
            this.id = id;
            this.msgId = msgId;
            this.scaling = scaling;
            this.exhaustion = exhaustion;
            this.burningEffect = burningEffect;
            this.flags = flags.isEmpty() ? EnumSet.noneOf(Flag.class) : EnumSet.copyOf(flags);
            this.tags = tags.isEmpty() ? EnumSet.noneOf(TypeTag.class) : EnumSet.copyOf(tags);
        }

        public ResourceLocation id() { return id; }

        public String msgId() { return msgId; }

        public Scaling scaling() { return scaling; }

        public float exhaustion() { return exhaustion; }

        public boolean burningEffect() { return burningEffect; }

        public boolean has(Flag flag) { return flags.contains(flag); }

        public boolean has(TypeTag tag) { return tags.contains(tag); }

        @Nullable
        public TechLevel techLevel() {
            if (tags.contains(TypeTag.CHAOTIC)) return TechLevel.CHAOTIC;
            if (tags.contains(TypeTag.DRACONIC)) return TechLevel.DRACONIC;
            if (tags.contains(TypeTag.WYVERN)) return TechLevel.WYVERN;
            if (tags.contains(TypeTag.DRACONIUM)) return TechLevel.DRACONIUM;
            return null;
        }

        /** 1.20 DamageSource#scalesWithDifficulty semantics (checks the causing entity). */
        public boolean scales(@Nullable Entity causingEntity) {
            return switch (scaling) {
                case NEVER -> false;
                case ALWAYS -> true;
                case WHEN_CAUSED_BY_LIVING_NON_PLAYER -> causingEntity instanceof LivingEntity && !(causingEntity instanceof Player);
            };
        }

        /**
         * Tag to 1.19.2 flag mapping (WP-22 spec §1.2). BYPASSES_SHIELD is implied by bypassArmor() (vanilla shields only
         * block sources that do not bypass armour); BYPASSES_COOLDOWN is handled by {@link #applyCooldownBypass}.
         */
        public void applyFlags(DamageSource source) {
            if (has(Flag.BYPASSES_ARMOR)) source.bypassArmor();
            if (has(Flag.BYPASSES_EFFECTS) || has(Flag.BYPASSES_RESISTANCE)) source.bypassMagic();
            if (has(Flag.BYPASSES_INVULNERABILITY)) source.bypassInvul();
            if (has(Flag.IS_EXPLOSION)) source.setExplosion();
            if (has(Flag.IS_PROJECTILE)) source.setProjectile();
            if (scaling == Scaling.ALWAYS) source.setScalesWithDifficulty();
        }

        @Override
        public String toString() { return "DEDamage.Type(" + id + ")"; }
    }

    /** The five #minecraft:bypasses_* tags shared by the six "boss" types (spec §1.1 "G6"). */
    private static final Set<Flag> BYPASS5 = EnumSet.of(Flag.BYPASSES_ARMOR, Flag.BYPASSES_SHIELD, Flag.BYPASSES_EFFECTS, Flag.BYPASSES_RESISTANCE, Flag.BYPASSES_COOLDOWN);

    private static Set<Flag> bypass5(Flag... extra) {
        EnumSet<Flag> set = EnumSet.copyOf(BYPASS5);
        for (Flag f : extra) set.add(f);
        return set;
    }

    private static Type type(String name, Scaling scaling, boolean burning, Set<Flag> flags, Set<TypeTag> tags) {
        return new Type(new ResourceLocation(MODID, name), MODID + "." + name, scaling, 0.1F, burning, flags, tags);
    }

    private static Type arrow(String name, String msgName, boolean spoof, TypeTag tier) {
        return new Type(new ResourceLocation(MODID, name), MODID + "." + msgName, Scaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.1F, false,
                spoof ? EnumSet.noneOf(Flag.class) : EnumSet.of(Flag.IS_PROJECTILE),
                spoof ? EnumSet.of(tier, TypeTag.PROJECTILE_ANTI_DODGE) : EnumSet.of(tier));
    }

    // Field names identical to 1.20 (init/DEDamage.java:29-45).
    public static final Type FUSION_EXPLOSION = type("fusion_explosion", Scaling.ALWAYS, false, bypass5(Flag.IS_EXPLOSION), EnumSet.of(TypeTag.CHAOTIC));
    /** Replaces DamageTypes.GENERIC_KILL. msgId and flags from de-1.18 ModularArmorEventHandler.KILL_COMMAND. */
    public static final Type KILL = new Type(new ResourceLocation("minecraft", "generic_kill"), "administrative.kill", Scaling.NEVER, 0.0F, false,
            EnumSet.of(Flag.BYPASSES_ARMOR, Flag.BYPASSES_EFFECTS, Flag.BYPASSES_RESISTANCE, Flag.BYPASSES_INVULNERABILITY), EnumSet.noneOf(TypeTag.class));
    public static final Type CRYSTAL_MOVE = type("crystal_move", Scaling.ALWAYS, false, bypass5(Flag.BYPASSES_INVULNERABILITY), EnumSet.noneOf(TypeTag.class));
    public static final Type CHAOS_IMPLOSION = type("chaos_implosion", Scaling.ALWAYS, false, bypass5(Flag.IS_EXPLOSION), EnumSet.of(TypeTag.CHAOTIC));
    public static final Type GUARDIAN = type("guardian", Scaling.ALWAYS, false, bypass5(), EnumSet.of(TypeTag.CHAOTIC));
    public static final Type GUARDIAN_PROJECTILE = type("guardian_projectile", Scaling.ALWAYS, true, bypass5(Flag.IS_EXPLOSION, Flag.IS_PROJECTILE), EnumSet.of(TypeTag.CHAOTIC));
    public static final Type GUARDIAN_LASER = type("guardian_laser", Scaling.ALWAYS, true, bypass5(Flag.IS_EXPLOSION), EnumSet.of(TypeTag.CHAOTIC));

    public static final Type DRACONIUM_ARROW = arrow("draconium_arrow", "draconium_arrow", false, TypeTag.DRACONIUM);
    public static final Type DRACONIUM_ARROW_SPOOF = arrow("draconium_arrow_spoof", "draconium_arrow", true, TypeTag.DRACONIUM);
    public static final Type WYVERN_ARROW = arrow("wyvern_arrow", "wyvern_arrow", false, TypeTag.WYVERN);
    public static final Type WYVERN_ARROW_SPOOF = arrow("wyvern_arrow_spoof", "wyvern_arrow", true, TypeTag.WYVERN);
    public static final Type DRACONIC_ARROW = arrow("draconic_arrow", "draconic_arrow", false, TypeTag.DRACONIC);
    public static final Type DRACONIC_ARROW_SPOOF = arrow("draconic_arrow_spoof", "draconic_arrow", true, TypeTag.DRACONIC);
    public static final Type CHAOTIC_ARROW = arrow("chaotic_arrow", "chaotic_arrow", false, TypeTag.CHAOTIC);
    public static final Type CHAOTIC_ARROW_SPOOF = arrow("chaotic_arrow_spoof", "chaotic_arrow", true, TypeTag.CHAOTIC);

    // Entity-less sources are shared, like the 1.20 SOURCES cache (1.20 DEDamage:27, :94-96). Declared after the types.
    private static final DamageSource FUSION_SOURCE = new DEDamageSources.PlainSource(FUSION_EXPLOSION);
    private static final DamageSource KILL_SOURCE = new DEDamageSources.KillSource(KILL);
    private static final DamageSource CRYSTAL_MOVE_SOURCE = new DEDamageSources.PlainSource(CRYSTAL_MOVE);
    private static final DamageSource CHAOS_IMPLOSION_SOURCE = new DEDamageSources.PlainSource(CHAOS_IMPLOSION);

    public static DamageSource fusionDamage(@Nullable Level level) {
        return FUSION_SOURCE;
    }

    public static DamageSource killDamage(@Nullable Level level) {
        return KILL_SOURCE;
    }

    public static DamageSource crystalMove(@Nullable Level level) {
        return CRYSTAL_MOVE_SOURCE;
    }

    public static DamageSource chaosImplosion(@Nullable Level level) {
        return CHAOS_IMPLOSION_SOURCE;
    }

    public static DamageSource guardianProjectile(@Nullable Level level, @Nullable Entity projectile, @Nullable Entity owner) {
        return indirect(GUARDIAN_PROJECTILE, projectile, owner);
    }

    public static DamageSource guardianLaser(@Nullable Level level, @Nullable Entity attacker) {
        return direct(GUARDIAN_LASER, attacker);
    }

    public static DamageSource guardian(@Nullable Level level, @Nullable Entity attacker) {
        return direct(GUARDIAN, attacker);
    }

    public static DamageSource draconicArrow(@Nullable Level level, @Nullable Entity projectile, @Nullable Entity owner, TechLevel techLevel, boolean bypassImmune) {
        Type type = switch (techLevel) {
            case DRACONIUM -> bypassImmune ? DRACONIUM_ARROW_SPOOF : DRACONIUM_ARROW;
            case WYVERN -> bypassImmune ? WYVERN_ARROW_SPOOF : WYVERN_ARROW;
            case DRACONIC -> bypassImmune ? DRACONIC_ARROW_SPOOF : DRACONIC_ARROW;
            case CHAOTIC -> bypassImmune ? CHAOTIC_ARROW_SPOOF : CHAOTIC_ARROW;
        };
        return indirect(type, bypassImmune ? null : projectile, owner); // 1.20 DEDamage:83
    }

    /** 1.20 new DamageSource(holder, attacker): direct == causing. */
    private static DamageSource direct(Type type, @Nullable Entity attacker) {
        return attacker == null ? new DEDamageSources.PlainSource(type) : new DEDamageSources.AttackerSource(type, attacker);
    }

    /** 1.20 new DamageSource(holder, projectile, owner). */
    private static DamageSource indirect(Type type, @Nullable Entity projectile, @Nullable Entity owner) {
        if (projectile != null) return new DEDamageSources.ProjectileSource(type, projectile, owner);
        return owner == null ? new DEDamageSources.PlainSource(type) : new DEDamageSources.CausingOnlySource(type, owner);
    }

    // ---- replacements for DamageSource#is(ResourceKey) / #is(TagKey) ----

    /** @return the DE damage type of this source, or null if it was not created by this class. */
    @Nullable
    public static Type getType(@Nullable DamageSource source) {
        return source instanceof IDEDamageSource de ? de.getDEType() : null;
    }

    /** 1.20 {@code source.is(DEDamage.X)}. */
    public static boolean is(@Nullable DamageSource source, Type type) {
        return getType(source) == type;
    }

    /** 1.20 {@code source.is(DEDamage.Tags.X)}. */
    public static boolean hasTag(@Nullable DamageSource source, TypeTag tag) {
        Type type = getType(source);
        return type != null && type.has(tag);
    }

    public static class Tags {
        public static final TypeTag CHAOTIC = TypeTag.CHAOTIC;
        public static final TypeTag DRACONIC = TypeTag.DRACONIC;
        public static final TypeTag WYVERN = TypeTag.WYVERN;
        public static final TypeTag DRACONIUM = TypeTag.DRACONIUM;

        public static final TypeTag PROJECTILE_ANTI_DODGE = TypeTag.PROJECTILE_ANTI_DODGE;
    }

    /**
     * This is the recommended way to check the draconic tech level of a damage source.
     * Same order and fallback as 1.20 (DEDamage:113-125); the tag checks become {@link IDraconicDamage}.
     *
     * @param source The damage source being tested.
     */
    @Nullable
    public static TechLevel getDamageLevel(DamageSource source) {
        if (source instanceof IDraconicDamage damage) {
            TechLevel level = damage.getTechLevel(null);
            if (level != null) return level;
        }
        if (source.getEntity() instanceof Player player) {
            ItemStack stack = player.getMainHandItem();
            if (stack.getItem() instanceof IDraconicMelee melee) {
                return melee.getTechLevel();
            }
        }
        return null;
    }

    /**
     * Emulates 1.20 #minecraft:bypasses_cooldown: a DE hit inside the 10-tick hurt window deals full damage.
     * Registered by ModularArmorEventHandler.init() at EventPriority.LOWEST (not receiving canceled events).
     */
    public static void applyCooldownBypass(LivingAttackEvent event) {
        Type type = getType(event.getSource());
        if (type != null && type.has(Flag.BYPASSES_COOLDOWN)) {
            event.getEntity().invulnerableTime = 0;
        }
    }
}
