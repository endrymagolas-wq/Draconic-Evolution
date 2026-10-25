package com.brandon3055.draconicevolution.gametest;

// WP-31b - FakePlayer variant for server-side GameTests (Forge 43.2.4 / MC 1.19.2).
// Forge's FakePlayer (net.minecraftforge.common.util.FakePlayer, [V] scratch/refsrc/forge43src) is deliberately inert:
//   * isInvulnerableTo(any) == true            -> ServerPlayer.hurt returns before LivingAttackEvent ever fires,
//   * die() is a no-op                         -> LivingDeathEvent is never posted (undying could never trigger),
//   * its net handler swallows teleport(...)   -> ServerPlayer teleports (BC TeleportUtils, same dimension) never move it,
//   * tick() is a no-op                        -> ServerPlayer.spawnInvulnerableTime (60) never counts down.
// This subclass keeps everything else of FakePlayer (not in the level's player list, no network) and restores exactly
// those four vanilla behaviours so DE's real event handlers and item code paths run. Nothing here changes DE code.

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class DETestPlayer extends FakePlayer {

    /** ServerPlayer.spawnInvulnerableTime: official name spawnInvulnerableTime, obf cD, SRG f_8921_ [V mcp_config-1.19.2 obf_to_srg.tsrg + client_mappings.txt]. */
    private static final String SRG_SPAWN_INVULNERABLE_TIME = "f_8921_";

    public DETestPlayer(ServerLevel level, String name) {
        super(level, new GameProfile(UUID.nameUUIDFromBytes(("deport-gametest:" + name).getBytes(StandardCharsets.UTF_8)), name));
        // ServerGamePacketListenerImpl's constructor assigns itself to player.connection, replacing FakePlayer's handler.
        new TestNetHandler(level.getServer(), this);
        clearSpawnInvulnerability();
    }

    /** A test player standing at the centre of the given relative block position (feet at its bottom). */
    public static DETestPlayer create(GameTestHelper helper, BlockPos rel, String name) {
        DETestPlayer player = new DETestPlayer(helper.getLevel(), name);
        Vec3 abs = helper.absoluteVec(new Vec3(rel.getX() + 0.5, rel.getY(), rel.getZ() + 0.5));
        player.moveTo(abs.x, abs.y, abs.z, 0F, 0F);
        return player;
    }

    public void holdMainHand(ItemStack stack) {
        setItemInHand(InteractionHand.MAIN_HAND, stack);
    }

    /**
     * Posts the Forge living-tick event the way LivingEntity.tick() does [V MC LivingEntity.tick -> ForgeHooks.onLivingTick].
     * FakePlayers are not ticked by the level, so module ticking (shield recharge, undying charge) is driven from here.
     */
    public void postLivingTick() {
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(this));
    }

    private void clearSpawnInvulnerability() {
        try {
            Field field = ObfuscationReflectionHelper.findField(ServerPlayer.class, SRG_SPAWN_INVULNERABLE_TIME);
            field.setInt(this, 0);
        } catch (RuntimeException | IllegalAccessException e) {
            throw new IllegalStateException("cannot clear ServerPlayer.spawnInvulnerableTime: " + e, e);
        }
    }

    /** Vanilla Entity/Player semantics instead of FakePlayer's "always invulnerable". */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return isRemoved() || (isInvulnerable() && source != DamageSource.OUT_OF_WORLD && !source.isCreativePlayer());
    }

    /** Posts LivingDeathEvent like ServerPlayer.die() does first [V MC ServerPlayer.die]; no drops/messages/respawn. */
    @Override
    public void die(DamageSource cause) {
        if (ForgeHooks.onLivingDeath(this, cause)) {
            return;
        }
        this.dead = true;
    }

    public boolean isDeadFlag() {
        return dead;
    }

    /** Keeps vanilla ServerGamePacketListenerImpl.teleport (absMoveTo) but sends nothing anywhere. */
    private static class TestNetHandler extends ServerGamePacketListenerImpl {
        TestNetHandler(MinecraftServer server, ServerPlayer player) {
            super(server, new Connection(PacketFlow.SERVERBOUND), player);
        }

        @Override
        public void send(Packet<?> packet) {}

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {}

        @Override
        public void tick() {}

        @Override
        public void disconnect(Component reason) {}
    }
}
