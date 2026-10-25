package com.brandon3055.draconicevolution.gametest;

// WP-31b - dislocators (PORTING_PLAN §8.3 "Машини": Dislocator Pedestal/Receptacle, Portal; dislocator item).
// Donor reference: donors/de-1.20/.../items/tools/Dislocator.java (use(): sneak binds TargetPos(player); use teleports,
// costs 2 health and 1 durability, 20-tick cooldown), blocks/tileentity/TileDislocatorPedestal.java (onBlockUse
// inserts/teleports), TileDislocatorReceptacle.java + PortalHelper.java (infused obsidian frame -> portal blocks ->
// entities touching the portal are sent to the dislocator target), BC TeleportUtils (same-dimension teleport).
// Not exercised on purpose: Dislocator/DislocatorAdvanced.onLeftClickEntity with a bound target builds its chat text
// with net.minecraft.client.resources.language.I18n, a client-only class, in 1.20.1 as well (donor line 94/111); on a
// dedicated server that is an upstream defect, not a port regression, and triggering it would make loggate fail on
// "invalid dist". The entity path is tested through Dislocator.dislocateEntity, which onLeftClickEntity calls.

import com.brandon3055.brandonscore.utils.TargetPos;
import com.brandon3055.draconicevolution.blocks.DislocatorReceptacle;
import com.brandon3055.draconicevolution.blocks.tileentity.TileDislocatorPedestal;
import com.brandon3055.draconicevolution.blocks.tileentity.TileDislocatorReceptacle;
import com.brandon3055.draconicevolution.init.DEContent;
import com.brandon3055.draconicevolution.items.tools.Dislocator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.brandon3055.draconicevolution.gametest.DEGameTestUtil.*;

@GameTestHolder(DEGameTestUtil.MODID)
@PrefixGameTestTemplate(false)
public class DislocatorGameTests {
    static {
        DEGameTestUtil.installReporter();
    }

    static final boolean REQUIRED = false;

    static Vec3 feet(GameTestHelper helper, BlockPos rel) {
        return helper.absoluteVec(new Vec3(rel.getX() + 0.5, rel.getY(), rel.getZ() + 0.5));
    }

    static ItemStack boundDislocator(GameTestHelper helper, BlockPos rel) {
        ItemStack stack = new ItemStack(DEContent.DISLOCATOR.get());
        Vec3 v = feet(helper, rel);
        ((Dislocator) stack.getItem()).setLocation(stack, new TargetPos(v.x, v.y, v.z, helper.getLevel().dimension()));
        return stack;
    }

    static void checkNear(net.minecraft.world.entity.Entity e, Vec3 target, double tolerance, String what) {
        double d = e.position().distanceTo(target);
        check(d <= tolerance, () -> what + " at " + e.position() + ", expected within " + tolerance + " of " + target + " (distance " + d + ")");
    }

    /** Sneak-use binds the dislocator to the player's position; use from elsewhere teleports back (2 health, 1 durability). */
    @GameTest(template = T_9, batch = BATCH_DISLOCATOR, timeoutTicks = 60, required = REQUIRED)
    public static void dislocatorBindsAndTeleportsPlayer(GameTestHelper helper) {
        BlockPos home = new BlockPos(1, 2, 1), away = new BlockPos(7, 2, 7);
        DETestPlayer player = DETestPlayer.create(helper, home, "de_dislocator_tester");
        ItemStack stack = new ItemStack(DEContent.DISLOCATOR.get());
        safe(() -> {
            player.holdMainHand(stack);
            player.setShiftKeyDown(true);
            player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
            player.setShiftKeyDown(false);
            ItemStack held = player.getMainHandItem();
            TargetPos target = ((Dislocator) held.getItem()).getTargetPos(held, helper.getLevel());
            check(target != null, () -> "sneak-use did not bind the dislocator: " + held.getTag());
            check(new Vec3(target.getX(), target.getY(), target.getZ()).distanceTo(feet(helper, home)) < 0.01 && target.getDimension() == helper.getLevel().dimension(),
                    () -> "bound to " + target.getReadableName(true));
            Vec3 a = feet(helper, away);
            player.moveTo(a.x, a.y, a.z, 90F, 0F);
            float health = player.getHealth();
            player.gameMode.useItem(player, helper.getLevel(), held, InteractionHand.MAIN_HAND);
            checkNear(player, feet(helper, home), 0.01, "player after use");
            check(player.getHealth() == health - 2, () -> "health " + player.getHealth() + " after a dislocation from " + health);
            check(player.getMainHandItem().getDamageValue() == 1, () -> "dislocator damage " + player.getMainHandItem().getDamageValue());
            check(player.getCooldowns().isOnCooldown(held.getItem()), () -> "no cooldown after use");
        }).run();
        helper.succeed();
    }

    /** Dislocator.dislocateEntity (the onLeftClickEntity teleport) moves a mob to the bound target. */
    @GameTest(template = T_9, batch = BATCH_DISLOCATOR, timeoutTicks = 60, required = REQUIRED)
    public static void dislocatorTeleportsEntity(GameTestHelper helper) {
        BlockPos target = new BlockPos(1, 2, 1);
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(4, 2, 4), "de_dislocator_user");
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(7, 2, 7));
        safe(() -> {
            ItemStack stack = boundDislocator(helper, target);
            Dislocator item = (Dislocator) stack.getItem();
            item.dislocateEntity(stack, player, cow, item.getTargetPos(stack, helper.getLevel()));
            checkNear(cow, feet(helper, target), 0.01, "cow");
            cow.discard();
        }).run();
        helper.succeed();
    }

    /** Pedestal: right-click inserts the held bound dislocator; right-click with an empty hand teleports the player. */
    @GameTest(template = T_9, batch = BATCH_DISLOCATOR, timeoutTicks = 60, required = REQUIRED)
    public static void dislocatorPedestalTeleportsPlayer(GameTestHelper helper) {
        BlockPos pedestal = new BlockPos(4, 2, 4), target = new BlockPos(7, 2, 1);
        helper.setBlock(pedestal, DEContent.DISLOCATOR_PEDESTAL.get());
        TileDislocatorPedestal tile = tile(helper, pedestal, TileDislocatorPedestal.class);
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(4, 2, 2), "de_pedestal_tester");
        safe(() -> {
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pedestal)), Direction.UP, helper.absolutePos(pedestal), false);
            player.holdMainHand(boundDislocator(helper, target));
            tile.onBlockUse(helper.getBlockState(pedestal), player, InteractionHand.MAIN_HAND, hit);
            check(tile.itemHandler.getStackInSlot(0).is(DEContent.DISLOCATOR.get()) && player.getMainHandItem().isEmpty(), () -> "pedestal holds " + tile.itemHandler.getStackInSlot(0) + ", hand " + player.getMainHandItem());
            tile.onBlockUse(helper.getBlockState(pedestal), player, InteractionHand.MAIN_HAND, hit);
            checkNear(player, feet(helper, target), 0.01, "player after using the pedestal");
            check(tile.itemHandler.getStackInSlot(0).is(DEContent.DISLOCATOR.get()), () -> "pedestal lost its dislocator");
        }).run();
        helper.succeed();
    }

    /**
     * Receptacle in a 1x2 infused-obsidian frame (vertical, X-Y plane): inserting a bound dislocator scans the frame and
     * fills it with portal blocks; a cow walking into the portal arrives at the target; removing the dislocator closes it.
     */
    @GameTest(template = T_9, batch = BATCH_DISLOCATOR, timeoutTicks = 300, required = REQUIRED)
    public static void dislocatorReceptacleBuildsPortalAndTeleports(GameTestHelper helper) {
        BlockPos receptacle = new BlockPos(3, 2, 4), in1 = new BlockPos(4, 2, 4), in2 = new BlockPos(4, 3, 4), target = new BlockPos(7, 2, 7);
        for (BlockPos p : List.of(new BlockPos(5, 2, 4), new BlockPos(5, 3, 4), new BlockPos(3, 3, 4), new BlockPos(4, 1, 4), new BlockPos(4, 4, 4))) {
            helper.setBlock(p, DEContent.INFUSED_OBSIDIAN.get());
        }
        helper.setBlock(receptacle, DEContent.DISLOCATOR_RECEPTACLE.get());
        TileDislocatorReceptacle tile = tile(helper, receptacle, TileDislocatorReceptacle.class);
        Cow[] cow = {null};
        helper.startSequence()
                .thenExecute(safe(() -> tile.itemHandler.setStackInSlot(0, boundDislocator(helper, target))))
                .thenWaitUntil(safe(() -> {
                    check(tile.isActive(), () -> "receptacle not active, ignition stage " + tile.ignitionStage.get());
                    helper.assertBlockPresent(DEContent.PORTAL.get(), in1);
                    helper.assertBlockPresent(DEContent.PORTAL.get(), in2);
                }))
                .thenExecute(safe(() -> {
                    helper.assertBlockProperty(receptacle, DislocatorReceptacle.ACTIVE, true);
                    cow[0] = helper.spawn(EntityType.COW, in1);
                }))
                .thenWaitUntil(safe(() -> checkNear(cow[0], feet(helper, target), 1.5, "cow")))
                .thenExecute(safe(() -> {
                    cow[0].discard();
                    tile.itemHandler.setStackInSlot(0, ItemStack.EMPTY);   // onInventoryChange -> deactivate -> portal blocks removed
                }))
                .thenIdle(1)
                .thenExecute(safe(() -> {
                    check(!tile.isActive(), () -> "receptacle still active without a dislocator");
                    helper.assertBlockNotPresent(DEContent.PORTAL.get(), in1);
                    helper.assertBlockNotPresent(DEContent.PORTAL.get(), in2);
                    helper.setBlock(receptacle, Blocks.AIR);
                }))
                .thenSucceed();
    }
}
