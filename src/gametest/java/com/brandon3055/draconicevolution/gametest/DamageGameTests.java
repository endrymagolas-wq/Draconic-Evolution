package com.brandon3055.draconicevolution.gametest;

// WP-31 extended GameTests (optional, batch de_ext): runtime checks of the 1.19.2 damage facade that
// specs/WP-22-damage.md §5.1 assigns to WP-31 (H1-H6, H8; H7 is a static grep gate). Expected values: WP-22 §1.4 / §4
// (copied into DE/PORT_NOTES.md "Damage types"). Entities are created with EntityType.create(level) and never added
// to the world, so nothing is spawned, ticked or damaged.

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.init.DEDamage;
import com.brandon3055.draconicevolution.lib.DEDamageSources;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.EntityDamageSource;
import net.minecraft.world.damagesource.IndirectEntityDamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import static com.brandon3055.draconicevolution.gametest.DEGameTestUtil.*;

@GameTestHolder(DEGameTestUtil.MODID)
@PrefixGameTestTemplate(false)
public class DamageGameTests {
    static {
        DEGameTestUtil.installReporter();
    }

    /** Expected 1.19.2 shape of one source (WP-22 §1.4). */
    private record Want(String msgId, boolean armor, boolean magic, boolean invul, boolean explosion, boolean projectile,
                        float exhaustion, boolean scales, TechLevel tech) {}

    private static void expect(List<String> problems, String src, String what, Object got, Object want) {
        if (!Objects.equals(got, want)) problems.add(src + "." + what + "=" + got + " (want " + want + ")");
    }

    private static void shape(List<String> p, String name, DamageSource s, Want w) {
        expect(p, name, "msgId", s.getMsgId(), w.msgId());
        expect(p, name, "isBypassArmor", s.isBypassArmor(), w.armor());
        expect(p, name, "isBypassMagic", s.isBypassMagic(), w.magic());
        expect(p, name, "isBypassInvul", s.isBypassInvul(), w.invul());
        expect(p, name, "isExplosion", s.isExplosion(), w.explosion());
        expect(p, name, "isProjectile", s.isProjectile(), w.projectile());
        expect(p, name, "getFoodExhaustion", s.getFoodExhaustion(), w.exhaustion());
        expect(p, name, "scalesWithDifficulty", s.scalesWithDifficulty(), w.scales());
        expect(p, name, "getDamageLevel", DEDamage.getDamageLevel(s), w.tech());
    }

    private static void finish(GameTestHelper helper, String test, List<String> problems) {
        check(problems.isEmpty(), () -> problems.size() + " " + test + " problems: " + head(problems));
        helper.succeed();
    }

    private static String tierName(TechLevel t) {
        return t.name().toLowerCase(Locale.ROOT);
    }

    /** H1: entity-less sources (null level): msgId, flags, exhaustion, scaling, tech level; shared like the 1.20 cache. */
    @GameTest(template = T_3, batch = BATCH_EXT, required = false)
    public static void damageEntityLessSources(GameTestHelper helper) {
        List<String> p = new ArrayList<>();
        shape(p, "fusionDamage", DEDamage.fusionDamage(null), new Want("draconicevolution.fusion_explosion", true, true, false, true, false, 0.1F, true, TechLevel.CHAOTIC));
        shape(p, "chaosImplosion", DEDamage.chaosImplosion(null), new Want("draconicevolution.chaos_implosion", true, true, false, true, false, 0.1F, true, TechLevel.CHAOTIC));
        shape(p, "crystalMove", DEDamage.crystalMove(null), new Want("draconicevolution.crystal_move", true, true, true, false, false, 0.1F, true, null));
        shape(p, "killDamage", DEDamage.killDamage(null), new Want("administrative.kill", true, true, true, false, false, 0.0F, false, null));
        expect(p, "fusionDamage", "shared", DEDamage.fusionDamage(null) == DEDamage.fusionDamage(helper.getLevel()), true);
        expect(p, "killDamage", "class", DEDamage.killDamage(null) instanceof DEDamageSources.KillSource, true);
        finish(helper, "H1", p);
    }

    /** H2: guardian / guardianLaser are direct EntityDamageSources (not Indirect: endermen must not dodge the laser). */
    @GameTest(template = T_3, batch = BATCH_EXT, required = false)
    public static void damageGuardianDirectSources(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity zombie = Objects.requireNonNull(EntityType.ZOMBIE.create(level));
        List<String> p = new ArrayList<>();
        Map<String, DamageSource> sources = new LinkedHashMap<>();
        sources.put("guardian", DEDamage.guardian(level, zombie));
        sources.put("guardianLaser", DEDamage.guardianLaser(level, zombie));
        for (Map.Entry<String, DamageSource> e : sources.entrySet()) {
            DamageSource s = e.getValue();
            expect(p, e.getKey(), "instanceof EntityDamageSource", s instanceof EntityDamageSource, true);
            expect(p, e.getKey(), "instanceof IndirectEntityDamageSource", s instanceof IndirectEntityDamageSource, false);
            expect(p, e.getKey(), "getEntity==zombie", s.getEntity() == zombie, true);
            expect(p, e.getKey(), "getDirectEntity==zombie", s.getDirectEntity() == zombie, true);
        }
        shape(p, "guardian", sources.get("guardian"), new Want("draconicevolution.guardian", true, true, false, false, false, 0.1F, true, TechLevel.CHAOTIC));
        shape(p, "guardianLaser", sources.get("guardianLaser"), new Want("draconicevolution.guardian_laser", true, true, false, true, false, 0.1F, true, TechLevel.CHAOTIC));
        expect(p, "guardian(null)", "class", DEDamage.guardian(level, null) instanceof DEDamageSources.PlainSource, true);
        finish(helper, "H2", p);
    }

    /** H3: guardianProjectile is an IndirectEntityDamageSource, projectile + explosion, always scales. */
    @GameTest(template = T_3, batch = BATCH_EXT, required = false)
    public static void damageGuardianProjectile(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity zombie = Objects.requireNonNull(EntityType.ZOMBIE.create(level));
        Entity snowball = Objects.requireNonNull(EntityType.SNOWBALL.create(level));
        List<String> p = new ArrayList<>();
        Map<String, Entity> owners = new LinkedHashMap<>();
        owners.put("ownerNull", null);
        owners.put("ownerZombie", zombie);
        for (Map.Entry<String, Entity> o : owners.entrySet()) {
            String n = "guardianProjectile(" + o.getKey() + ")";
            DamageSource s = DEDamage.guardianProjectile(level, snowball, o.getValue());
            expect(p, n, "instanceof IndirectEntityDamageSource", s instanceof IndirectEntityDamageSource, true);
            expect(p, n, "getDirectEntity==snowball", s.getDirectEntity() == snowball, true);
            expect(p, n, "getEntity==owner", s.getEntity() == o.getValue(), true);
            shape(p, n, s, new Want("draconicevolution.guardian_projectile", true, true, false, true, true, 0.1F, true, TechLevel.CHAOTIC));
        }
        finish(helper, "H3", p);
    }

    /** H4: draconic arrows of the 4 tiers: Indirect, projectile, WHEN_CAUSED_BY_LIVING_NON_PLAYER scaling, tier level. */
    @GameTest(template = T_3, batch = BATCH_EXT, required = false)
    public static void damageArrowTiers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity arrow = Objects.requireNonNull(EntityType.ARROW.create(level));
        Entity skeleton = Objects.requireNonNull(EntityType.SKELETON.create(level));
        Player player = helper.makeMockPlayer();
        List<String> p = new ArrayList<>();
        int tiers = 0;
        for (TechLevel tier : TechLevel.values()) {
            tiers++;
            String n = "draconicArrow(" + tier + ")";
            DamageSource s = DEDamage.draconicArrow(level, arrow, player, tier, false);
            expect(p, n, "instanceof IndirectEntityDamageSource", s instanceof IndirectEntityDamageSource, true);
            expect(p, n, "getDirectEntity==arrow", s.getDirectEntity() == arrow, true);
            expect(p, n, "getEntity==player", s.getEntity() == player, true);
            shape(p, n, s, new Want("draconicevolution." + tierName(tier) + "_arrow", false, false, false, false, true, 0.1F, false, tier));
            DamageSource sk = DEDamage.draconicArrow(level, arrow, skeleton, tier, false);
            expect(p, n + "[skeleton]", "scalesWithDifficulty", sk.scalesWithDifficulty(), true);
            DamageSource none = DEDamage.draconicArrow(level, arrow, null, tier, false);
            expect(p, n + "[no owner]", "scalesWithDifficulty", none.scalesWithDifficulty(), false);
        }
        int t = tiers;
        expect(p, "TechLevel", "values", t, 4);
        finish(helper, "H4", p);
    }

    /** H5: spoof arrows: CausingOnlySource (no direct entity/position, not projectile, anti-dodge tag); owner null -> Plain. */
    @GameTest(template = T_3, batch = BATCH_EXT, required = false)
    public static void damageArrowSpoof(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity arrow = Objects.requireNonNull(EntityType.ARROW.create(level));
        Player player = helper.makeMockPlayer();
        List<String> p = new ArrayList<>();
        for (TechLevel tier : TechLevel.values()) {
            String n = "draconicArrow(" + tier + ", spoof)";
            DamageSource spoof = DEDamage.draconicArrow(level, arrow, player, tier, true);
            DamageSource base = DEDamage.draconicArrow(level, arrow, player, tier, false);
            expect(p, n, "instanceof CausingOnlySource", spoof instanceof DEDamageSources.CausingOnlySource, true);
            expect(p, n, "instanceof IndirectEntityDamageSource", spoof instanceof IndirectEntityDamageSource, false);
            expect(p, n, "getDirectEntity", spoof.getDirectEntity(), null);
            expect(p, n, "getSourcePosition", spoof.getSourcePosition(), null);
            expect(p, n, "getEntity==player", spoof.getEntity() == player, true);
            expect(p, n, "isProjectile", spoof.isProjectile(), false);
            expect(p, n, "hasTag(PROJECTILE_ANTI_DODGE)", DEDamage.hasTag(spoof, DEDamage.Tags.PROJECTILE_ANTI_DODGE), true);
            expect(p, n, "hasTag(PROJECTILE_ANTI_DODGE) on base", DEDamage.hasTag(base, DEDamage.Tags.PROJECTILE_ANTI_DODGE), false);
            expect(p, n, "msgId==base msgId", spoof.getMsgId(), base.getMsgId());
            expect(p, n, "getDamageLevel", DEDamage.getDamageLevel(spoof), tier);
            DamageSource orphan = DEDamage.draconicArrow(level, arrow, null, tier, true);
            expect(p, n + "[no owner]", "instanceof PlainSource", orphan instanceof DEDamageSources.PlainSource, true);
            expect(p, n + "[no owner]", "msgId", orphan.getMsgId(), base.getMsgId());
        }
        finish(helper, "H5", p);
    }

    /** One representative source per DE type (15), shared by H6 and H8. */
    private static Map<DEDamage.Type, DamageSource> sourcesByType(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity zombie = Objects.requireNonNull(EntityType.ZOMBIE.create(level));
        Entity snowball = Objects.requireNonNull(EntityType.SNOWBALL.create(level));
        Entity arrow = Objects.requireNonNull(EntityType.ARROW.create(level));
        Player player = helper.makeMockPlayer();
        Map<DEDamage.Type, DamageSource> m = new LinkedHashMap<>();
        m.put(DEDamage.FUSION_EXPLOSION, DEDamage.fusionDamage(level));
        m.put(DEDamage.KILL, DEDamage.killDamage(level));
        m.put(DEDamage.CRYSTAL_MOVE, DEDamage.crystalMove(level));
        m.put(DEDamage.CHAOS_IMPLOSION, DEDamage.chaosImplosion(level));
        m.put(DEDamage.GUARDIAN, DEDamage.guardian(level, zombie));
        m.put(DEDamage.GUARDIAN_PROJECTILE, DEDamage.guardianProjectile(level, snowball, zombie));
        m.put(DEDamage.GUARDIAN_LASER, DEDamage.guardianLaser(level, zombie));
        m.put(DEDamage.DRACONIUM_ARROW, DEDamage.draconicArrow(level, arrow, player, TechLevel.DRACONIUM, false));
        m.put(DEDamage.DRACONIUM_ARROW_SPOOF, DEDamage.draconicArrow(level, arrow, player, TechLevel.DRACONIUM, true));
        m.put(DEDamage.WYVERN_ARROW, DEDamage.draconicArrow(level, arrow, player, TechLevel.WYVERN, false));
        m.put(DEDamage.WYVERN_ARROW_SPOOF, DEDamage.draconicArrow(level, arrow, player, TechLevel.WYVERN, true));
        m.put(DEDamage.DRACONIC_ARROW, DEDamage.draconicArrow(level, arrow, player, TechLevel.DRACONIC, false));
        m.put(DEDamage.DRACONIC_ARROW_SPOOF, DEDamage.draconicArrow(level, arrow, player, TechLevel.DRACONIC, true));
        m.put(DEDamage.CHAOTIC_ARROW, DEDamage.draconicArrow(level, arrow, player, TechLevel.CHAOTIC, false));
        m.put(DEDamage.CHAOTIC_ARROW_SPOOF, DEDamage.draconicArrow(level, arrow, player, TechLevel.CHAOTIC, true));
        return m;
    }

    /** H6: DEDamage.is(src, X) is true only for the source's own type (15 x 15); spoof != base; vanilla sources match none. */
    @GameTest(template = T_3, batch = BATCH_EXT, required = false)
    public static void damageTypeIdentity(GameTestHelper helper) {
        Map<DEDamage.Type, DamageSource> m = sourcesByType(helper);
        List<String> p = new ArrayList<>();
        expect(p, "types", "count", m.size(), 15);
        Set<String> ids = new TreeSet<>();
        for (DEDamage.Type t : m.keySet()) ids.add(t.id().toString());
        expect(p, "types", "distinct ids", ids.size(), 15);
        for (Map.Entry<DEDamage.Type, DamageSource> src : m.entrySet()) {
            for (DEDamage.Type x : m.keySet()) {
                boolean got = DEDamage.is(src.getValue(), x);
                if (got != (x == src.getKey())) p.add("is(" + src.getKey().id() + " source, " + x.id() + ")=" + got);
            }
            expect(p, src.getKey().id().toString(), "getType", DEDamage.getType(src.getValue()), src.getKey());
        }
        for (DEDamage.Type x : m.keySet()) {
            if (DEDamage.is(DamageSource.GENERIC, x) || DEDamage.is(DamageSource.OUT_OF_WORLD, x)) p.add("vanilla source matches " + x.id());
        }
        expect(p, "GENERIC", "getType", DEDamage.getType(DamageSource.GENERIC), null);
        finish(helper, "H6", p);
    }

    // WP-22 §4: keys that are missing in 1.20.1 as well (gate-8 allowlist, D-5) - accepted, not regressions.
    private static final Set<String> ALLOW_MISSING = Set.of(
            "death.attack.draconicevolution.draconic_arrow", "death.attack.draconicevolution.wyvern_arrow",
            "death.attack.draconicevolution.draconium_arrow", "death.attack.draconicevolution.fusion_explosion",
            "death.attack.draconicevolution.chaos_implosion", "death.attack.draconicevolution.crystal_move",
            "death.attack.draconicevolution.fusion_explosion.player", "death.attack.draconicevolution.chaos_implosion.player",
            "death.attack.draconicevolution.crystal_move.player",
            "death.attack.draconicevolution.guardian.item", "death.attack.draconicevolution.guardian_laser.item",
            "death.attack.draconicevolution.guardian_projectile.item", "death.attack.draconicevolution.chaotic_arrow.item",
            "death.attack.draconicevolution.draconic_arrow.item", "death.attack.draconicevolution.wyvern_arrow.item",
            "death.attack.draconicevolution.draconium_arrow.item");

    /**
     * H8: every death.attack key a DE source can produce (WP-22 §4: Plain -> base + .player, KillSource -> base only,
     * entity sources -> base + .item) exists in the server language (Forge loads the mods' en_us.json on the server),
     * or is on the §4 allowlist; and getLocalizedDeathMessage(victim without kill credit) really uses the base key.
     */
    @GameTest(template = T_3, batch = BATCH_EXT, required = false)
    public static void damageDeathMessageKeys(GameTestHelper helper) {
        Map<DEDamage.Type, DamageSource> m = sourcesByType(helper);
        LivingEntity victim = Objects.requireNonNull(EntityType.ZOMBIE.create(helper.getLevel()));
        Language lang = Language.getInstance();
        List<String> p = new ArrayList<>();
        Set<String> allowedMissing = new TreeSet<>();
        Set<String> allowedButPresent = new TreeSet<>();
        int present = 0;
        for (Map.Entry<DEDamage.Type, DamageSource> e : m.entrySet()) {
            DamageSource s = e.getValue();
            String base = "death.attack." + s.getMsgId();
            List<String> keys = new ArrayList<>(List.of(base));
            if (s instanceof DEDamageSources.KillSource) {
                // base only (KillSource overrides getLocalizedDeathMessage)
            } else if (s instanceof DEDamageSources.PlainSource) {
                keys.add(base + ".player");
            } else {
                keys.add(base + ".item");
            }
            for (String k : keys) {
                boolean has = lang.has(k);
                if (has) present++;
                if (!has && ALLOW_MISSING.contains(k)) allowedMissing.add(k);
                else if (!has) p.add("missing lang key " + k + " (" + e.getKey().id() + ")");
                else if (ALLOW_MISSING.contains(k)) allowedButPresent.add(k);
            }
            Component msg = s.getLocalizedDeathMessage(victim);
            String key = msg.getContents() instanceof TranslatableContents tc ? tc.getKey() : String.valueOf(msg.getContents());
            expect(p, e.getKey().id().toString(), "deathMessageKey", key, base);
        }
        int n = present;
        LOGGER.info("DEPORT-GATE death_keys present={} allowlisted_missing={} {}", n, allowedMissing.size(), allowedMissing);
        if (!allowedButPresent.isEmpty()) LOGGER.info("DEPORT-GATE death_keys allowlisted_but_present={}", allowedButPresent);
        finish(helper, "H8", p);
    }
}
