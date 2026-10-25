package com.brandon3055.draconicevolution.gametest;

// GameTests for the damage findings of the adversarial review (WP-30 review-fix round) and for decision D-3.
//  - COMBAT-1: DE is_explosion damage with a causing entity gives no hurt-knockback (1.20.1 rule), other sources do.
//  - COMBAT-3/TILES-1: a lightChunk future usually completes off the server thread (plain thenRun; logged, not asserted),
//    and thenRunAsync(.., server) always brings the callback back to the server thread (the pattern ExplosionHelper now uses).
//    These two were WP-30's out-of-tree check R\logs\WP-30\verify\...\Wp30VerifyGameTests.java, adopted by CR WP-30-3
//    (test names unchanged, batch de_ext).
//  - D-3 (lead decision (c), CR WP-30-2 section 2): the staff explosion (DraconicProjectileEntity.activateDamageEffect)
//    uses DEDamageSources.explosion(owner) = the 1.20.1 damageSources().explosion(owner, null): direct entity = owner,
//    no causing entity. Expected 1.20.1 effects [I-1.20, vanilla 1.20.1 code]: no kill credit, XP or player-kill loot,
//    no aggro on the owner, no PvP gate, no hurt-knockback, difficulty scaling "always", shield blocking against the
//    owner's position, death message death.attack.explosion, endermen take the hit (only #is_projectile is dodged).
//    Each check has a control with the old de-1.18 source DamageSource.explosion(owner) (or another variant) that shows
//    the test can see the difference. Batch de_damage_d3 (own batch: the call-site test explodes).
//    Positions: relative y=1 is the template floor (GameTestHelper positions are relative to the structure block, one
//    below the template), so entities stand at y=2.

import com.brandon3055.draconicevolution.entity.projectile.DraconicProjectileEntity;
import com.brandon3055.draconicevolution.init.DEDamage;
import com.brandon3055.draconicevolution.lib.DEDamageSources;
import com.brandon3055.draconicevolution.lib.Serializers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.EntityDamageSource;
import net.minecraft.world.damagesource.IndirectEntityDamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static com.brandon3055.draconicevolution.gametest.DEGameTestUtil.*;

@GameTestHolder(DEGameTestUtil.MODID)
@PrefixGameTestTemplate(false)
public class DamageReviewGameTests {
    static {
        DEGameTestUtil.installReporter();
    }

    /** D-3 tests; separate from de_ext because the call-site test detonates an explosion. */
    public static final String BATCH_D3 = "de_damage_d3";

    // ==== COMBAT-1 / COMBAT-3 (adopted from WP-30, CR WP-30-3) =====================================================

    /** Horizontal velocity of a fresh NoAI pig after one hit by {@code source} from an attacker 2 blocks east of it. */
    private static double horizontalPush(GameTestHelper helper, BlockPos rel, String name, java.util.function.BiFunction<ServerLevel, Entity, DamageSource> factory, List<String> log) {
        ServerLevel level = helper.getLevel();
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, rel);
        Entity attacker = Objects.requireNonNull(EntityType.ZOMBIE.create(level));
        attacker.moveTo(pig.getX() + 2, pig.getY(), pig.getZ(), 0, 0);
        pig.setDeltaMovement(Vec3.ZERO);
        float before = pig.getHealth();
        DamageSource source = factory.apply(level, attacker);
        boolean hurt = pig.hurt(source, 1.0F);
        Vec3 v = pig.getDeltaMovement();
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        log.add(name + ": hurt=" + hurt + " health " + before + "->" + pig.getHealth() + " v=" + v + " h=" + h);
        check(hurt && pig.getHealth() < before, () -> name + ": hit was not applied (hurt=" + hurt + ")");
        pig.discard();
        return h;
    }

    /** COMBAT-1: guardian_laser / guardian_projectile (is_explosion) do not knock back; guardian (not is_explosion) and a vanilla mob attack do. */
    @GameTest(template = T_3, batch = BATCH_EXT)
    public static void wp30ExplosionTypesNoHurtKnockback(GameTestHelper helper) {
        List<String> log = new ArrayList<>();
        Entity snowball = Objects.requireNonNull(EntityType.SNOWBALL.create(helper.getLevel()));
        double laser = horizontalPush(helper, new BlockPos(1, 2, 1), "guardianLaser", DEDamage::guardianLaser, log);
        double projectile = horizontalPush(helper, new BlockPos(1, 2, 1), "guardianProjectile", (l, a) -> DEDamage.guardianProjectile(l, snowball, a), log);
        double guardian = horizontalPush(helper, new BlockPos(1, 2, 1), "guardian", DEDamage::guardian, log);
        double mob = horizontalPush(helper, new BlockPos(1, 2, 1), "mobAttack", (l, a) -> DamageSource.mobAttack((net.minecraft.world.entity.LivingEntity) a), log);
        double vanillaExplosion = horizontalPush(helper, new BlockPos(1, 2, 1), "vanillaExplosion(mob)", (l, a) -> DamageSource.explosion((net.minecraft.world.entity.LivingEntity) a), log);
        LOGGER.info("DEPORT-GATE wp30.knockback {}", log);
        check(laser < 1.0E-6, () -> "guardianLaser knocked back: " + log);
        check(projectile < 1.0E-6, () -> "guardianProjectile knocked back: " + log);
        check(guardian > 0.1, () -> "guardian (no is_explosion) did not knock back: " + log);
        check(mob > 0.1, () -> "mobAttack did not knock back: " + log);
        check(vanillaExplosion > 0.1, () -> "vanilla explosion(mob) lost its 1.19.2 knockback: " + log);
        helper.succeed();
    }

    /** COMBAT-3/TILES-1: where the lightChunk callbacks run (the ExplosionHelper.finishChunks call, same arguments). */
    @GameTest(template = T_3, batch = BATCH_EXT, timeoutTicks = 200)
    public static void wp30RelightCallbackOnServerThread(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Thread serverThread = server.getRunningThread();
        LevelChunk chunk = level.getChunkAt(helper.absolutePos(BlockPos.ZERO));
        ThreadedLevelLightEngine light = (ThreadedLevelLightEngine) level.getLightEngine();
        AtomicReference<Thread> plain = new AtomicReference<>();
        AtomicReference<Thread> async = new AtomicReference<>();
        chunk.setLightCorrect(false);
        var future = light.lightChunk(chunk, true);
        future.thenRun(() -> plain.set(Thread.currentThread()));
        future.thenRunAsync(() -> async.set(Thread.currentThread()), server);
        helper.succeedWhen(() -> {
            check(plain.get() != null && async.get() != null, () -> "lightChunk callbacks not run yet");
            LOGGER.info("DEPORT-GATE wp30.relight plainThenRun={} thenRunAsync={} serverThread={}", plain.get().getName(), async.get().getName(), serverThread.getName());
            check(async.get() == serverThread, () -> "thenRunAsync(.., server) ran on " + async.get().getName());
            // Not asserted: a plain thenRun runs on whichever thread completes the future (usually the light worker,
            // but the server thread when the future is already complete), so the finding's premise is timing-dependent.
        });
    }

    // ==== D-3: staff explosion source (decision (c)) ================================================================

    /**
     * A pig that exposes the protected LivingEntity state vanilla 1.19.2 uses for XP and player-kill loot
     * ({@code lastHurtByPlayer}, {@code lastHurtByPlayerTime}, {@code createLootContext}; LivingEntity.java:187-188,
     * 1331-1384). Same entity type and behaviour as a vanilla pig.
     */
    static class ProbePig extends Pig {
        ProbePig(Level level) {
            super(EntityType.PIG, level);
        }

        @Nullable
        Player lastPlayer() {
            return lastHurtByPlayer;
        }

        int lastPlayerTime() {
            return lastHurtByPlayerTime;
        }

        /** The context LivingEntity.dropFromLootTable builds (flag = lastHurtByPlayerTime > 0, as dropAllDeathLoot passes it). */
        LootContext deathLootContext(DamageSource source) {
            return createLootContext(lastHurtByPlayerTime > 0, source).create(LootContextParamSets.ENTITY);
        }
    }

    /**
     * DETestPlayer with the vanilla ServerPlayer.canHarmPlayer rule (ServerPlayer.java:676-682 + Player.canHarmPlayer)
     * instead of Forge FakePlayer's constant false, so the server pvp setting decides like on a real server.
     */
    static class PvpTestPlayer extends DETestPlayer {
        PvpTestPlayer(ServerLevel level, String name) {
            super(level, name);
        }

        @Override
        public boolean canHarmPlayer(Player other) {
            if (!getServer().isPvpAllowed()) return false;
            Team team = getTeam();
            return team == null || !team.isAlliedTo(other.getTeam()) || team.isAllowFriendlyFire();
        }
    }

    private static void expect(List<String> problems, String what, Object got, Object want) {
        if (!Objects.equals(got, want)) problems.add(what + "=" + got + " (want " + want + ")");
    }

    private static ProbePig probePig(GameTestHelper helper, BlockPos rel, float health) {
        ProbePig pig = new ProbePig(helper.getLevel());
        Vec3 abs = helper.absoluteVec(Vec3.atBottomCenterOf(rel));
        pig.moveTo(abs.x, abs.y, abs.z, 0F, 0F);
        pig.setNoAi(true);
        if (health > pig.getMaxHealth()) {
            Objects.requireNonNull(pig.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(health);
        }
        pig.setHealth(health);
        check(helper.getLevel().addFreshEntity(pig), () -> "could not add the probe pig at " + rel);
        return pig;
    }

    private static <T extends net.minecraft.world.entity.Mob> T noAi(GameTestHelper helper, EntityType<T> type, BlockPos rel) {
        T mob = helper.spawn(type, rel);
        mob.setNoAi(true);
        mob.setDeltaMovement(Vec3.ZERO);
        return mob;
    }

    private static String deathKey(DamageSource source, LivingEntity victim) {
        Component msg = source.getLocalizedDeathMessage(victim);
        return msg.getContents() instanceof TranslatableContents tc ? tc.getKey() + "/" + tc.getArgs().length : String.valueOf(msg.getContents());
    }

    private static int orbs(GameTestHelper helper, BlockPos max) {
        return entitiesIn(helper, ExperienceOrb.class, BlockPos.ZERO, max).stream().mapToInt(ExperienceOrb::getValue).sum();
    }

    private static boolean killedByPlayerLoot(ProbePig pig, DamageSource source) {
        return LootItemKilledByPlayerCondition.killedByPlayer().build().test(pig.deathLootContext(source));
    }

    /**
     * Upstream never registers {@code Serializers.OPT_MODULE_SERIALIZER} (its registration is commented out, identical
     * in 1.20.1), so {@code new DraconicProjectileEntity(..)} throws "Unregistered serializer" in
     * SynchedEntityData.define (same check in 1.20.1 [I-1.20]): the class is dead code in normal play. To reach the D-3
     * call site, this test registers the serializer in the vanilla serializer map of the throwaway gametest server
     * (test harness only; the mod is unchanged).
     */
    private static void makeProjectileConstructible() {
        if (EntityDataSerializers.getSerializedId(Serializers.OPT_MODULE_SERIALIZER) < 0) {
            EntityDataSerializers.registerSerializer(Serializers.OPT_MODULE_SERIALIZER);
            LOGGER.info("DEPORT-GATE d3.callsite test-only registration of Serializers.OPT_MODULE_SERIALIZER (never registered upstream)");
        }
    }

    /**
     * D-3 at the real call site: a DraconicProjectileEntity with explosive power, owned by a player, detonates on its
     * first tick (maxFlightTime 1) through activateDamageEffect -> level.explode(owner, DEDamageSources.explosion(owner)).
     * The victims' last damage source is the D-3 source; a pig killed by it has no kill credit, drops no XP and its loot
     * context fails killed_by_player. Control: the old de-1.18 source DamageSource.explosion(owner) gives all three.
     */
    @GameTest(template = T_9, batch = BATCH_D3, timeoutTicks = 100)
    public static void d3StaffExplosionCallSite(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos max = new BlockPos(8, 4, 8);
        // Relative y=1 is the template floor (positions are relative to the structure block); entities stand at y=2.
        // The owner is not added to the level (like every DETestPlayer); level.explode(owner, ...) would exclude it anyway.
        DETestPlayer owner = DETestPlayer.create(helper, new BlockPos(8, 2, 4), "d3_owner");
        ProbePig survivor = probePig(helper, new BlockPos(3, 2, 4), 100F);
        ProbePig dying = probePig(helper, new BlockPos(5, 2, 4), 1F);
        Vec3 centre = helper.absoluteVec(new Vec3(4.5, 2.5, 4.5));
        makeProjectileConstructible();
        DraconicProjectileEntity projectile = new DraconicProjectileEntity(level, centre.x, centre.y, centre.z);
        projectile.setOwner(owner);
        projectile.setArrowProjectile(false);
        projectile.setNoGravity(true);
        projectile.setDeltaMovement(Vec3.ZERO);
        projectile.setExplosivePower(2, false);
        projectile.setMaxFlightTime(1);
        check(projectile.getOwner() == owner, () -> "projectile owner not set: " + projectile.getOwner());
        check(level.addFreshEntity(projectile), () -> "could not add the projectile");
        int orbsBefore = orbs(helper, max);
        List<String> log = new ArrayList<>();
        List<String> p = new ArrayList<>();

        helper.startSequence()
                .thenWaitUntil(safe(() -> check(projectile.isRemoved() && dying.isDeadOrDying(),
                        () -> "explosion not yet: projectile removed=" + projectile.isRemoved() + " dying pig health=" + dying.getHealth())))
                .thenExecute(safe(() -> {
                    DamageSource s = survivor.getLastDamageSource();
                    DamageSource d = dying.getLastDamageSource();
                    log.add("survivor health=" + survivor.getHealth() + " source=" + s + " (" + (s == null ? null : s.getMsgId()) + ")");
                    log.add("dying source=" + d + " dead=" + dying.isDeadOrDying());
                    expect(p, "survivor.lastDamageSource class", s == null ? null : s.getClass().getSimpleName(), "DirectExplosionSource");
                    expect(p, "dying.lastDamageSource class", d == null ? null : d.getClass().getSimpleName(), "DirectExplosionSource");
                    check(s != null && d != null, () -> "explosion damage not recorded: " + log);
                    expect(p, "source.msgId", s.getMsgId(), "explosion");
                    expect(p, "source.isExplosion", s.isExplosion(), true);
                    expect(p, "source.getEntity", s.getEntity(), null);
                    expect(p, "source.getDirectEntity==owner", s.getDirectEntity() == owner, true);
                    expect(p, "survivor.health<100", survivor.getHealth() < 100F, true);
                    expect(p, "survivor.lastHurtByMob (aggro)", survivor.getLastHurtByMob(), null);
                    expect(p, "survivor.killCredit", survivor.getKillCredit(), null);
                    expect(p, "survivor.lastHurtByPlayer", survivor.lastPlayer(), null);
                    expect(p, "dying.killCredit", dying.getKillCredit(), null);
                    expect(p, "dying.lastHurtByPlayer", dying.lastPlayer(), null);
                    expect(p, "dying.lastHurtByPlayerTime", dying.lastPlayerTime(), 0);
                    LootContext ctx = dying.deathLootContext(d);
                    expect(p, "loot killed_by_player", killedByPlayerLoot(dying, d), false);
                    expect(p, "loot killer_entity", ctx.getParamOrNull(LootContextParams.KILLER_ENTITY), null);
                    expect(p, "loot direct_killer_entity==owner", ctx.getParamOrNull(LootContextParams.DIRECT_KILLER_ENTITY) == owner, true);
                    int xp = orbs(helper, max) - orbsBefore;
                    log.add("xp after explosion kill=" + xp);
                    expect(p, "XP dropped by the explosion kill", xp, 0);
                }))
                .thenExecute(safe(() -> {
                    // control: the old de-1.18 source (D-3 default before the decision) on a fresh 1 HP pig
                    ProbePig control = probePig(helper, new BlockPos(1, 2, 1), 1F);
                    DamageSource old = DamageSource.explosion((LivingEntity) owner);
                    int before = orbs(helper, max);
                    boolean hurt = control.hurt(old, 5F);
                    int xp = orbs(helper, max) - before;
                    boolean loot = killedByPlayerLoot(control, old);
                    log.add("control(old explosion.player): hurt=" + hurt + " dead=" + control.isDeadOrDying() + " xp=" + xp + " killed_by_player=" + loot + " credit=" + control.getKillCredit());
                    expect(p, "control dead", control.isDeadOrDying(), true);
                    expect(p, "control XP > 0", xp > 0, true);
                    expect(p, "control killed_by_player", loot, true);
                    expect(p, "control killCredit==owner", control.getKillCredit() == owner, true);
                    LOGGER.info("DEPORT-GATE d3.callsite {}", log);
                    check(p.isEmpty(), () -> p.size() + " D-3 call-site problems: " + head(p) + " | " + log);
                }))
                .thenSucceed();
    }

    /**
     * D-3 source effects without an explosion: shape, death message, no hurt-knockback and no aggro, PvP gate and
     * difficulty scaling against players, endermen. Each with a control (old de-1.18 source, or an Indirect variant).
     */
    @GameTest(template = T_3, batch = BATCH_D3)
    public static void d3StaffExplosionSourceEffects(GameTestHelper helper) {
        safe(() -> sourceEffects(helper)).run();
        helper.succeed();
    }

    private static void sourceEffects(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        List<String> p = new ArrayList<>();
        List<String> log = new ArrayList<>();
        // the owner stands 2 blocks east of the victims at x=1 (not added to the level); y=2 = on the template floor
        PvpTestPlayer owner = new PvpTestPlayer(level, "d3_owner_fx");
        Vec3 ownerPos = helper.absoluteVec(new Vec3(3.5, 2, 1.5));
        owner.moveTo(ownerPos.x, ownerPos.y, ownerPos.z, 0F, 0F);
        DamageSource src = DEDamageSources.explosion(owner);
        DamageSource old = DamageSource.explosion((LivingEntity) owner);

        // 1. shape (1.20.1 minecraft:explosion, direct = owner, causing = null)
        expect(p, "instanceof EntityDamageSource", src instanceof EntityDamageSource, true);
        expect(p, "instanceof IndirectEntityDamageSource", src instanceof IndirectEntityDamageSource, false);
        expect(p, "msgId", src.getMsgId(), "explosion");
        expect(p, "isExplosion", src.isExplosion(), true);
        expect(p, "isProjectile", src.isProjectile(), false);
        expect(p, "isBypassArmor", src.isBypassArmor(), false);
        expect(p, "isBypassMagic", src.isBypassMagic(), false);
        expect(p, "isBypassInvul", src.isBypassInvul(), false);
        expect(p, "isFire", src.isFire(), false);
        expect(p, "getFoodExhaustion", src.getFoodExhaustion(), 0.1F);
        expect(p, "scalesWithDifficulty", src.scalesWithDifficulty(), true);
        expect(p, "getEntity", src.getEntity(), null);
        expect(p, "getDirectEntity==owner", src.getDirectEntity() == owner, true);
        expect(p, "getSourcePosition", src.getSourcePosition(), owner.position());
        expect(p, "isCreativePlayer", src.isCreativePlayer(), false);
        expect(p, "DEDamage.getType", DEDamage.getType(src), null);
        expect(p, "DEDamage.getDamageLevel", DEDamage.getDamageLevel(src), null);
        // control documents the old source: credited to the owner, never scaled for a player owner
        expect(p, "old.getEntity==owner", old.getEntity() == owner, true);
        expect(p, "old.scalesWithDifficulty (player owner)", old.scalesWithDifficulty(), false);

        // 2. death message: death.attack.explosion (victim, owner), also when the owner holds a renamed item
        LivingEntity victim = Objects.requireNonNull(EntityType.ZOMBIE.create(level));
        expect(p, "death key", deathKey(src, victim), "death.attack.explosion/2");
        expect(p, "lang has death.attack.explosion", Language.getInstance().has("death.attack.explosion"), true);
        owner.holdMainHand(new ItemStack(Items.STICK).setHoverName(Component.literal("Staff")));
        expect(p, "death key (named item)", deathKey(src, victim), "death.attack.explosion/2");
        expect(p, "control: plain EntityDamageSource death key (named item)", deathKey(new EntityDamageSource("explosion", owner).setExplosion(), victim), "death.attack.explosion.item/3");
        expect(p, "control: old death key (named item)", deathKey(old, victim), "death.attack.explosion.player.item/3");
        owner.holdMainHand(ItemStack.EMPTY);
        expect(p, "control: old death key", deathKey(old, victim), "death.attack.explosion.player/2");

        // 3. hurt-knockback and aggro on a NoAI pig (owner 2 blocks east)
        for (boolean useOld : new boolean[]{false, true}) {
            Pig pig = noAi(helper, EntityType.PIG, new BlockPos(1, 2, 1));
            float before = pig.getHealth();
            boolean hurt = pig.hurt(useOld ? old : src, 1F);
            Vec3 v = pig.getDeltaMovement();
            double h = Math.sqrt(v.x * v.x + v.z * v.z);
            String n = useOld ? "control(old) pig" : "pig";
            log.add(n + ": hurt=" + hurt + " health " + before + "->" + pig.getHealth() + " h=" + h + " lastHurtByMob=" + pig.getLastHurtByMob() + " credit=" + pig.getKillCredit());
            expect(p, n + " hit applied", hurt && pig.getHealth() < before, true);
            if (useOld) {
                expect(p, n + " knockback 0.4", Math.abs(h - 0.4) < 1.0E-3, true);
                expect(p, n + " lastHurtByMob==owner", pig.getLastHurtByMob() == owner, true);
                expect(p, n + " killCredit==owner", pig.getKillCredit() == owner, true);
            } else {
                expect(p, n + " knockback", h < 1.0E-6, true);
                expect(p, n + " lastHurtByMob (aggro)", pig.getLastHurtByMob(), null);
                expect(p, n + " killCredit", pig.getKillCredit(), null);
            }
            pig.discard();
        }

        // 4. PvP gate and difficulty scaling against a second player (pvp=false, HARD; restored in the same tick)
        boolean pvpWas = server.isPvpAllowed();
        Difficulty diffWas = level.getDifficulty();
        try {
            server.setDifficulty(Difficulty.HARD, true);
            server.setPvpAllowed(false);
            PvpTestPlayer a = new PvpTestPlayer(level, "d3_victim_a");
            PvpTestPlayer b = new PvpTestPlayer(level, "d3_victim_b");
            boolean ha = a.hurt(src, 4F);
            boolean hb = b.hurt(old, 4F);
            server.setPvpAllowed(true);
            PvpTestPlayer c = new PvpTestPlayer(level, "d3_victim_c");
            boolean hc = c.hurt(old, 4F);
            log.add("players (HARD): D-3 pvp=false hurt=" + ha + " health=" + a.getHealth() + "; old pvp=false hurt=" + hb + " health=" + b.getHealth() + "; old pvp=true hurt=" + hc + " health=" + c.getHealth());
            expect(p, "player hit by D-3 source on pvp=false", ha, true);
            expect(p, "player health after 4 (HARD x1.5)", a.getHealth(), 14F);
            expect(p, "control: old source on pvp=false (PvP gate)", hb, false);
            expect(p, "control: old source pvp=false health", b.getHealth(), 20F);
            expect(p, "control: old source on pvp=true", hc, true);
            expect(p, "control: old source pvp=true health (no scaling)", c.getHealth(), 16F);
        } finally {
            server.setPvpAllowed(pvpWas);
            server.setDifficulty(diffWas, true);
        }
        expect(p, "difficulty restored", level.getDifficulty(), diffWas);

        // 5. endermen take the D-3 hit (not Indirect); control: an IndirectEntityDamageSource variant is dodged (1.19.2 EnderMan.hurt)
        EnderMan e1 = noAi(helper, EntityType.ENDERMAN, new BlockPos(1, 2, 2));
        EnderMan e2 = noAi(helper, EntityType.ENDERMAN, new BlockPos(1, 2, 0));
        float e1Before = e1.getHealth();
        float e2Before = e2.getHealth();
        boolean h1 = e1.hurt(src, 4F);
        boolean h2 = e2.hurt(new IndirectEntityDamageSource("explosion", owner, null).setExplosion(), 4F);
        log.add("enderman: D-3 hurt=" + h1 + " health " + e1Before + "->" + e1.getHealth() + "; control(Indirect) hurt=" + h2 + " health " + e2Before + "->" + e2.getHealth());
        expect(p, "enderman damaged by D-3 source", e1.getHealth() < e1Before, true);
        expect(p, "control: enderman dodges the Indirect variant", e2.getHealth(), e2Before);
        e1.discard();
        e2.discard();

        LOGGER.info("DEPORT-GATE d3.effects difficulty={} {}", diffWas, log);
        check(p.isEmpty(), () -> p.size() + " D-3 source problems: " + head(p) + " | " + log);
    }

    /** A NoAI pig using a shield in the off hand, facing {@code yaw} (-90 = east, +90 = west). */
    private static Pig shieldPig(GameTestHelper helper, BlockPos rel, float yaw) {
        Pig pig = noAi(helper, EntityType.PIG, rel);
        pig.setYRot(yaw);
        pig.setYHeadRot(yaw);
        pig.setYBodyRot(yaw);
        pig.setXRot(0F);
        pig.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        pig.startUsingItem(InteractionHand.OFF_HAND);
        return pig;
    }

    /**
     * D-3: vanilla shield blocking uses the source position = the owner's position (direct entity), as 1.20.1 does,
     * and LivingEntity.blockUsingShield runs against the direct entity: owner.blockedByShield(pig) gives the blocking pig
     * knockback(0.5, pig - owner) (LivingEntity.java:1182-1188), i.e. 0.5 towards the owner. Controls: a pig facing
     * away is hit; the option (b) source DamageSource.explosion((LivingEntity) null) has no position and is not blocked.
     */
    @GameTest(template = T_3, batch = BATCH_D3, timeoutTicks = 60)
    public static void d3StaffExplosionShieldBlocking(GameTestHelper helper) {
        DETestPlayer owner = DETestPlayer.create(helper, new BlockPos(12, 2, 1), "d3_owner_shield");
        Pig facing = shieldPig(helper, new BlockPos(0, 2, 0), -90F);
        Pig away = shieldPig(helper, new BlockPos(1, 2, 1), 90F);
        Pig noPos = shieldPig(helper, new BlockPos(2, 2, 2), -90F);
        List<String> p = new ArrayList<>();
        List<String> log = new ArrayList<>();
        helper.startSequence()
                .thenIdle(7) // LivingEntity.isBlocking: used for >= 5 ticks
                .thenExecute(safe(() -> {
                    for (Pig pig : List.of(facing, away, noPos)) {
                        check(pig.isBlocking(), () -> "shield pig not blocking: using=" + pig.isUsingItem() + " remaining=" + pig.getUseItemRemainingTicks());
                        check(pig.getHealth() == 10F && pig.getLastDamageSource() == null, () -> "shield pig was damaged before the test hit: " + pig.getHealth() + " " + pig.getLastDamageSource());
                        pig.setDeltaMovement(Vec3.ZERO);
                    }
                    DamageSource src = DEDamageSources.explosion(owner);
                    boolean hf = facing.hurt(src, 4F);
                    Vec3 push = facing.getDeltaMovement();
                    boolean ha = away.hurt(src, 4F);
                    boolean hn = noPos.hurt(DamageSource.explosion((LivingEntity) null), 4F);
                    log.add("facing owner: hurt=" + hf + " health=" + facing.getHealth() + " view=" + facing.getViewVector(1F) + " push=" + push);
                    log.add("facing away: hurt=" + ha + " health=" + away.getHealth() + " v=" + away.getDeltaMovement());
                    log.add("option (b) no-position source: hurt=" + hn + " health=" + noPos.getHealth());
                    expect(p, "D-3 hit blocked by a shield facing the owner", hf, false);
                    expect(p, "blocked: health", facing.getHealth(), 10F);
                    expect(p, "blocked: blockUsingShield(owner) push 0.5 towards the owner", Math.abs(push.horizontalDistance() - 0.5) < 1.0E-3 && push.x > 0.45, true);
                    expect(p, "control: no hurt-knockback on the unblocked pig", away.getDeltaMovement().horizontalDistance() < 1.0E-6, true);
                    expect(p, "control: shield facing away does not block", ha, true);
                    expect(p, "control: facing away health", away.getHealth(), 6F);
                    expect(p, "control: option (b) source is not blocked", hn, true);
                    expect(p, "control: option (b) health", noPos.getHealth(), 6F);
                    LOGGER.info("DEPORT-GATE d3.shield {}", log);
                    check(p.isEmpty(), () -> p.size() + " D-3 shield problems: " + head(p) + " | " + log);
                }))
                .thenSucceed();
    }
}
