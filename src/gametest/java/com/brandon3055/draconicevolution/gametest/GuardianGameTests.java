package com.brandon3055.draconicevolution.gametest;

// WP-31b - Draconic Guardian, server side, no full fight (PORTING_PLAN §8.3 "Guardian": виклик, фази, кристали).
// Donor reference: command/CommandRespawnGuardian.java (needs a chaos crystal within 60 blocks, registers a
// GuardianFightManager world entity at the crystal), entity/guardian/GuardianFightManager.java (crystal positions,
// crystalSpawned/findAliveCrystals, onGuardianAttacked blocks damage while crystals live), DraconicGuardianEntity.java
// (multipart, shield charges while crystals live), control/PhaseManager.java + PhaseType, GuardianCrystalEntity.java
// (shielded unless destabilised; explosion damage destroys it without a second explosion).
// The guardian destroys blocks it flies through (destroyBlocksInAABB) and its arena is ~90 blocks wide, so the API
// test runs 4096 blocks away from the gametest grid in force-loaded chunks that are released afterwards.

import com.brandon3055.brandonscore.worldentity.WorldEntity;
import com.brandon3055.brandonscore.worldentity.WorldEntityHandler;
import com.brandon3055.draconicevolution.blocks.tileentity.TileChaosCrystal;
import com.brandon3055.draconicevolution.entity.GuardianCrystalEntity;
import com.brandon3055.draconicevolution.entity.guardian.DraconicGuardianEntity;
import com.brandon3055.draconicevolution.entity.guardian.GuardianFightManager;
import com.brandon3055.draconicevolution.entity.guardian.control.PhaseType;
import com.brandon3055.draconicevolution.init.DEContent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.brandon3055.draconicevolution.gametest.DEGameTestUtil.*;

@GameTestHolder(DEGameTestUtil.MODID)
@PrefixGameTestTemplate(false)
public class GuardianGameTests {
    static {
        DEGameTestUtil.installReporter();
    }

    static final boolean REQUIRED = false;

    /** Failure path: whatever a failed/timed-out test left behind is released after the batch. */
    static final Set<ChunkPos> FORCED = java.util.Collections.synchronizedSet(new LinkedHashSet<>());
    static final List<Runnable> CLEANUPS = java.util.Collections.synchronizedList(new ArrayList<>());

    @net.minecraft.gametest.framework.AfterBatch(batch = BATCH_GUARDIAN)
    public static void afterGuardianBatch(ServerLevel level) {
        synchronized (CLEANUPS) {
            CLEANUPS.forEach(r -> {
                try {
                    r.run();
                } catch (RuntimeException e) {
                    LOGGER.warn("guardian cleanup: {}", e.toString());
                }
            });
            CLEANUPS.clear();
        }
        synchronized (FORCED) {
            FORCED.forEach(p -> level.setChunkForced(p.x, p.z, false));
            FORCED.clear();
        }
    }

    static List<GuardianFightManager> managersAt(BlockPos origin) {
        List<GuardianFightManager> list = new ArrayList<>();
        for (WorldEntity e : WorldEntityHandler.getWorldEntities()) {
            if (e instanceof GuardianFightManager m && !m.isRemoved() && origin.equals(m.getArenaOrigin())) list.add(m);
        }
        return list;
    }

    /** /respawn_draconic_guardian next to a chaos crystal registers exactly one fight manager for that arena. */
    @GameTest(template = T_11, batch = BATCH_GUARDIAN, timeoutTicks = 100, required = REQUIRED)
    public static void guardianRespawnCommandRegistersFight(GameTestHelper helper) {
        BlockPos crystal = new BlockPos(5, 4, 5);
        ServerLevel level = helper.getLevel();
        helper.setBlock(crystal, DEContent.CHAOS_CRYSTAL.get());
        TileChaosCrystal tile = tile(helper, crystal, TileChaosCrystal.class);
        tile.onValidPlacement();   // what the chaos island generator does; otherwise the crystal removes itself as "moved"
        BlockPos abs = helper.absolutePos(crystal);
        CLEANUPS.add(() -> managersAt(abs).forEach(WorldEntity::removeEntity));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(safe(() -> {
                    helper.assertBlockPresent(DEContent.CHAOS_CRYSTAL.get(), crystal);
                    helper.assertBlockPresent(DEContent.CHAOS_CRYSTAL_PART.get(), crystal.above());
                    tile.guardianDefeated.set(true);
                    CommandSourceStack source = level.getServer().createCommandSourceStack().withLevel(level)
                            .withPosition(Vec3.atCenterOf(abs.east(3))).withPermission(4).withSuppressedOutput();
                    int rc = level.getServer().getCommands().performPrefixedCommand(source, "respawn_draconic_guardian");
                    List<GuardianFightManager> found = managersAt(abs);
                    check(found.size() == 1, () -> "command rc=" + rc + ", fight managers at the crystal: " + found.size());
                    check(!tile.guardianDefeated.get(), () -> "command did not reset guardianDefeated");
                    check(found.get(0).respawnState != null, () -> "fight manager not waiting for a player (respawnState null)");
                    level.getServer().getCommands().performPrefixedCommand(source, "respawn_draconic_guardian");
                    check(managersAt(abs).size() == 1, () -> "second command added another fight manager");
                    found.forEach(WorldEntity::removeEntity);
                }))
                .thenExecute(safe(() -> {
                    check(managersAt(abs).isEmpty(), () -> "fight manager not removed");
                    // remove the crystal for good: an undefeated crystal re-places itself one tick after removal
                    // (TileChaosCrystal.detonate), a defeated one drops its shards instead
                    tile.guardianDefeated.set(true);
                    helper.setBlock(crystal, Blocks.AIR);
                }))
                .thenIdle(2)
                .thenExecute(safe(() -> {
                    helper.assertBlockNotPresent(DEContent.CHAOS_CRYSTAL.get(), crystal);
                    discardAll(helper, net.minecraft.world.entity.item.ItemEntity.class, new BlockPos(0, 0, 0), new BlockPos(10, 8, 10));
                }))
                .thenSucceed();
    }

    static void force(ServerLevel level, BlockPos pos, int radius, Set<ChunkPos> forced) {
        ChunkPos c = new ChunkPos(pos);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                ChunkPos p = new ChunkPos(c.x + x, c.z + z);
                if (forced.add(p)) {
                    FORCED.add(p);
                    level.setChunkForced(p.x, p.z, true);
                }
            }
        }
    }

    /** API spawn (what GuardianFightManager.createNewGuardian does), crystal registration and shield, phase manager transitions. */
    @GameTest(template = T_3, batch = BATCH_GUARDIAN, timeoutTicks = 600, required = REQUIRED)
    public static void guardianSpawnCrystalsAndPhases(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1)).offset(4096, 0, 4096);
        Set<ChunkPos> forced = new LinkedHashSet<>();
        GuardianFightManager manager = new GuardianFightManager(origin);
        DraconicGuardianEntity[] guardian = {null};
        List<GuardianCrystalEntity> crystals = new ArrayList<>();
        Vec3[] lastPos = {null};
        Runnable cleanup = () -> {
            crystals.forEach(GuardianCrystalEntity::discard);
            if (guardian[0] != null) guardian[0].discard();
            manager.removeEntity();
            for (int i = 0; i < 2; i++) {
                BlockPos cp = manager.getCrystalPositions().get(i);
                level.setBlockAndUpdate(cp.above(), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(cp, Blocks.AIR.defaultBlockState());
            }
            forced.forEach(p -> level.setChunkForced(p.x, p.z, false));
            FORCED.removeAll(forced);
        };
        CLEANUPS.add(cleanup);
        helper.startSequence()
                .thenExecute(safe(() -> {
                    force(level, origin, 2, forced);
                    for (int i = 0; i < 2; i++) force(level, manager.getCrystalPositions().get(i), 0, forced);
                }))
                // forced tickets propagate over the next ticks; entities added before that land in non-ticking sections
                // that getEntitiesOfClass does not see
                .thenWaitUntil(safe(() -> {
                    check(level.isPositionEntityTicking(manager.guardianSpawnPos()), () -> "arena chunk not entity-ticking yet");
                    for (int i = 0; i < 2; i++) {
                        BlockPos cp = manager.getCrystalPositions().get(i);
                        check(level.isPositionEntityTicking(cp), () -> "crystal chunk " + new ChunkPos(cp) + " not entity-ticking yet");
                    }
                }))
                .thenExecute(safe(() -> {
                    WorldEntityHandler.addWorldEntity(level, manager);
                    for (int i = 0; i < 2; i++) {
                        BlockPos cp = manager.getCrystalPositions().get(i);
                        level.setBlockAndUpdate(cp, DEContent.INFUSED_OBSIDIAN.get().defaultBlockState());
                        GuardianCrystalEntity c = new GuardianCrystalEntity(level, cp.getX() + 0.5, cp.getY() + 1, cp.getZ() + 0.5, manager.getUniqueID());
                        check(level.addFreshEntity(c), () -> "crystal not added");
                        crystals.add(c);
                        manager.crystalSpawned();
                    }
                    DraconicGuardianEntity g = DEContent.ENTITY_DRACONIC_GUARDIAN.get().create(level);
                    check(g != null, () -> "guardian entity type created nothing");
                    g.getPhaseManager().setPhase(PhaseType.START);
                    BlockPos spawn = manager.guardianSpawnPos();
                    g.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0F, 0F);
                    g.setFightManager(manager);
                    g.setArenaOrigin(origin);
                    check(level.addFreshEntity(g), () -> "guardian not added");
                    guardian[0] = g;
                    check(g.isMultipartEntity() && g.getParts() != null && g.getParts().length == 8, () -> "guardian parts " + (g.getParts() == null ? null : g.getParts().length));
                    check(g.getPhaseManager().getCurrentPhase().getType() == PhaseType.START && g.getEntityData().get(DraconicGuardianEntity.PHASE) == PhaseType.START.getId(),
                            () -> "phase " + g.getPhaseManager().getCurrentPhase().getType() + " synced " + g.getEntityData().get(DraconicGuardianEntity.PHASE));
                    check(manager.getNumAliveCrystals() == 2 && manager.getCrystals().size() == 2, () -> "crystals alive " + manager.getNumAliveCrystals() + ", found " + manager.getCrystals().size());
                    check(crystals.stream().allMatch(c -> manager.getUniqueID().equals(c.getManagerId())), () -> "crystal manager id mismatch");
                }))
                .thenIdle(20)
                .thenExecute(safe(() -> {
                    DraconicGuardianEntity g = guardian[0];
                    check(g.isAlive() && !g.isRemoved(), () -> "guardian gone");
                    check(level.getEntityOrPart(g.getId() + 1) == g.getParts()[0], () -> "part entity " + (g.getId() + 1) + " not registered with the level");
                    check(g.getShieldPower() > 0, () -> "guardian shield not charging while crystals are alive (" + g.getShieldPower() + ")");
                    check(!manager.onGuardianAttacked(g, DamageSource.GENERIC, 10F), () -> "guardian damage not blocked while crystals are alive");
                    GuardianCrystalEntity c0 = crystals.get(0);
                    check(!c0.hurt(DamageSource.GENERIC, 5F) && c0.isAlive(), () -> "stable crystal took normal damage");
                    c0.destabilize();
                    c0.hurt(DamageSource.explosion((LivingEntity) null), 1000F);
                    check(c0.isRemoved(), () -> "destabilised crystal survived 1000 explosion damage (shield " + c0.getShieldPower() + ")");
                    check(manager.getNumAliveCrystals() == 1, () -> "alive crystals after one destroyed: " + manager.getNumAliveCrystals());
                    g.getPhaseManager().setPhase(PhaseType.HOVER);
                    check(g.getPhaseManager().getCurrentPhase().getType() == PhaseType.HOVER && g.getEntityData().get(DraconicGuardianEntity.PHASE) == PhaseType.HOVER.getId()
                            && g.getPhaseManager().getCurrentPhase().getIsStationary(), () -> "HOVER not applied");
                }))
                .thenIdle(5)
                .thenExecute(safe(() -> {
                    DraconicGuardianEntity g = guardian[0];
                    check(g.getPhaseManager().getCurrentPhase().getType() == PhaseType.HOVER, () -> "left HOVER on its own: " + g.getPhaseManager().getCurrentPhase().getType());
                    g.getPhaseManager().setPhase(PhaseType.START);
                    lastPos[0] = g.position();
                }))
                .thenIdle(20)
                .thenExecute(safe(() -> {
                    DraconicGuardianEntity g = guardian[0];
                    check(g.getPhaseManager().getCurrentPhase().getType() == PhaseType.START, () -> "phase " + g.getPhaseManager().getCurrentPhase().getType());
                    check(g.getPhaseManager().getCurrentPhase().getTargetLocation() != null, () -> "START phase picked no path target");
                    check(g.position().distanceTo(lastPos[0]) > 0.1, () -> "guardian did not move in START (" + g.position() + ")");
                    cleanup.run();
                }))
                .thenSucceed();
    }
}
