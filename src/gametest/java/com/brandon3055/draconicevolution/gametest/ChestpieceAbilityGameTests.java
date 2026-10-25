package com.brandon3055.draconicevolution.gametest;

// Chestpiece abilities, server side (handlers/ModularArmorEventHandler.livingTick + module entities). The wearer is a survival
// DETestPlayer; module ticking is driven by posting LivingEvent.LivingTickEvent (FakePlayers are not ticked), like
// ModularItemGameTests. Expectations from the implementation (unchanged from the 1.20.1 donor except API renames and the
// 9005 night-vision fix):
//   flight      creative flight (draconic+ module) -> abilities.mayfly; creativeFlightEnergy OP per tick while flying;
//               below creativeFlightEnergy: mayfly and flying are switched off
//   speed       MOVEMENT_SPEED modifier WALK_SPEED_UUID (MULTIPLY_BASE) = min(module speed, walk_speed / run_speed property)
//   jump        LivingJumpEvent: push(0, 0.1 * (jump + 1), 0)
//   hill step   STEP_HEIGHT_ADDITION modifier up to 1.1625, not while sneaking
//   night vision  every 20 ticks: applied at light <= light_level using nightVisionEnergy * 20 OP; removed on the 4th check
//               without enough energy or in brighter light
//   auto feed   eats food from the inventory into its store, refills hunger 1 point per update

import com.brandon3055.brandonscore.api.power.IOPStorage;
import com.brandon3055.draconicevolution.api.capability.PropertyProvider;
import com.brandon3055.draconicevolution.api.config.IntegerProperty;
import com.brandon3055.draconicevolution.api.modules.ModuleTypes;
import com.brandon3055.draconicevolution.handlers.ModularArmorEventHandler;
import com.brandon3055.draconicevolution.init.DEContent;
import com.brandon3055.draconicevolution.init.DEModules;
import com.brandon3055.draconicevolution.init.EquipCfg;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.function.Supplier;

import static com.brandon3055.draconicevolution.gametest.DEGameTestUtil.*;
import static com.brandon3055.draconicevolution.gametest.ModularTestUtil.*;

@GameTestHolder(DEGameTestUtil.MODID)
@PrefixGameTestTemplate(false)
public class ChestpieceAbilityGameTests {
    static {
        DEGameTestUtil.installReporter();
    }

    static final boolean REQUIRED = true;
    public static final String BATCH_ABILITIES = "de_abilities";

    @SafeVarargs
    static ItemStack wear(DETestPlayer player, Supplier<? extends Item> chest, Supplier<? extends Item>... modules) {
        ItemStack stack = new ItemStack(chest.get());
        for (Supplier<? extends Item> m : modules) install(stack, player, m);
        ModularItemGameTests.fill(energy(stack));
        player.setItemSlot(EquipmentSlot.CHEST, stack);
        return stack;
    }

    static void ticks(DETestPlayer player, int n) {
        for (int i = 0; i < n; i++) player.postLivingTick();
    }

    static void drain(IOPStorage storage, long leave) {
        storage.modifyEnergyStored(-(storage.getOPStored() - leave));
    }

    /**
     * Draconic flight on a survival player: mayfly after one update, no drain while not flying, creativeFlightEnergy per tick
     * while flying; with less than one tick of energy flight is switched off (mayfly and flying false).
     */
    @GameTest(template = T_3, batch = BATCH_ABILITIES, timeoutTicks = 40, required = REQUIRED)
    public static void flightGrantsMayflyDrainsEnergyAndStopsWhenEmpty(GameTestHelper helper) {
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(1, 2, 1), "de_flight_tester");
        safe(() -> {
            check(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL && !player.getAbilities().instabuild && !player.getAbilities().mayfly,
                    () -> "test player is not a plain survival player: " + player.gameMode.getGameModeForPlayer() + " mayfly " + player.getAbilities().mayfly);
            ItemStack chest = wear(player, DEContent.CHESTPIECE_DRACONIC, DEModules.ITEM_DRACONIC_ENERGY, DEModules.ITEM_DRACONIC_FLIGHT);
            IOPStorage storage = energy(chest);
            long full = storage.getOPStored();
            ticks(player, 1);
            check(player.getAbilities().mayfly, () -> "draconic flight module did not grant mayfly");
            check(storage.getOPStored() == full, () -> "energy used while not flying: " + (full - storage.getOPStored()));

            player.getAbilities().flying = true;
            ticks(player, 10);
            long used = full - storage.getOPStored();
            check(used == 10L * EquipCfg.creativeFlightEnergy, () -> "10 ticks of flight used " + used + " OP, expected 10 x " + EquipCfg.creativeFlightEnergy);
            check(player.getAbilities().mayfly && player.getAbilities().flying, () -> "flight stopped with energy left");

            drain(storage, EquipCfg.creativeFlightEnergy - 1);
            ticks(player, 1);
            check(!player.getAbilities().mayfly && !player.getAbilities().flying, () -> "flight still on with " + storage.getOPStored() + " OP: mayfly " + player.getAbilities().mayfly + " flying " + player.getAbilities().flying);

            ModularItemGameTests.fill(storage);
            ticks(player, 1);
            check(player.getAbilities().mayfly, () -> "mayfly not restored after recharging");
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            ticks(player, 1);
            check(!player.getAbilities().mayfly, () -> "mayfly kept after taking the chestpiece off");
        }).run();
        helper.succeed();
    }

    /** Wyvern flight has no creative flight (FlightData creative=false; elytra only): no mayfly. */
    @GameTest(template = T_3, batch = BATCH_ABILITIES, timeoutTicks = 40, required = REQUIRED)
    public static void wyvernFlightIsElytraOnly(GameTestHelper helper) {
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(1, 2, 1), "de_wyvern_flight");
        safe(() -> {
            wear(player, DEContent.CHESTPIECE_WYVERN, DEModules.ITEM_WYVERN_ENERGY, DEModules.ITEM_WYVERN_FLIGHT);
            ticks(player, 2);
            check(!player.getAbilities().mayfly, () -> "wyvern flight module granted creative flight");
        }).run();
        helper.succeed();
    }

    static AttributeModifier speedModifier(DETestPlayer player) {
        AttributeInstance inst = player.getAttribute(Attributes.MOVEMENT_SPEED);
        return inst == null ? null : inst.getModifier(ModularArmorEventHandler.WALK_SPEED_UUID);
    }

    /** Speed module (wyvern 0.25): no boost while walking by default (walk_speed 0), +25% (MULTIPLY_BASE) while sprinting; walk_speed setting applies. */
    @GameTest(template = T_3, batch = BATCH_ABILITIES, timeoutTicks = 40, required = REQUIRED)
    public static void speedModuleModifiesMovement(GameTestHelper helper) {
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(1, 2, 1), "de_speed_tester");
        safe(() -> {
            ItemStack chest = wear(player, DEContent.CHESTPIECE_WYVERN, DEModules.ITEM_WYVERN_ENERGY, DEModules.ITEM_WYVERN_SPEED);
            ticks(player, 1);
            check(speedModifier(player) == null, () -> "walking with walk_speed 0 has a speed modifier " + speedModifier(player));
            player.setSprinting(true);
            ticks(player, 1);
            AttributeModifier run = speedModifier(player);
            check(run != null && Math.abs(run.getAmount() - 0.25) < 1e-6 && run.getOperation() == AttributeModifier.Operation.MULTIPLY_BASE,
                    () -> "sprinting speed modifier " + run + ", expected +0.25 MULTIPLY_BASE");
            player.setSprinting(false);
            ((PropertyProvider) host(chest)).getDecimal("walk_speed").setValue(0.1);
            ticks(player, 1);
            AttributeModifier walk = speedModifier(player);
            check(walk != null && Math.abs(walk.getAmount() - 0.1) < 1e-6, () -> "walk_speed 0.1 modifier " + walk);
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            ticks(player, 1);
            check(speedModifier(player) == null, () -> "speed modifier kept after taking the chestpiece off");
        }).run();
        helper.succeed();
    }

    /** Jump module (wyvern 0.75): a jump gets +0.1 * (0.75 + 1) = +0.175 upward velocity over the vanilla jump. */
    @GameTest(template = T_3, batch = BATCH_ABILITIES, timeoutTicks = 40, required = REQUIRED)
    public static void jumpModuleBoostsJump(GameTestHelper helper) {
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(1, 2, 1), "de_jump_tester");
        safe(() -> {
            player.setDeltaMovement(Vec3.ZERO);
            player.jumpFromGround();
            double vanilla = player.getDeltaMovement().y;
            check(vanilla > 0.4, () -> "vanilla jump velocity " + vanilla);
            wear(player, DEContent.CHESTPIECE_WYVERN, DEModules.ITEM_WYVERN_ENERGY, DEModules.ITEM_WYVERN_JUMP);
            player.setDeltaMovement(Vec3.ZERO);
            player.jumpFromGround();
            double boosted = player.getDeltaMovement().y;
            check(Math.abs(boosted - vanilla - 0.175) < 1e-4, () -> "jump with the wyvern jump module " + boosted + ", vanilla " + vanilla + " (expected +0.175)");
            player.setShiftKeyDown(true);
            player.setDeltaMovement(Vec3.ZERO);
            player.jumpFromGround();
            double sneaking = player.getDeltaMovement().y;
            check(Math.abs(sneaking - vanilla) < 1e-4, () -> "sneak jump boosted: " + sneaking);
        }).run();
        helper.succeed();
    }

    /** Hill step: STEP_HEIGHT_ADDITION raised to 1.1625 (1 block steps), not while sneaking, gone without the chestpiece. */
    @GameTest(template = T_3, batch = BATCH_ABILITIES, timeoutTicks = 40, required = REQUIRED)
    public static void hillStepRaisesStepHeight(GameTestHelper helper) {
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(1, 2, 1), "de_step_tester");
        safe(() -> {
            AttributeInstance step = player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
            check(step != null, () -> "player has no step height attribute");
            double base = step.getValue();
            wear(player, DEContent.CHESTPIECE_WYVERN, DEModules.ITEM_WYVERN_ENERGY, DEModules.ITEM_WYVERN_HILL_STEP);
            ticks(player, 1);
            check(Math.abs(step.getValue() - 1.1625) < 1e-6, () -> "step height addition " + step.getValue() + " (base " + base + "), expected 1.1625");
            check(player.getStepHeight() > 1.0F, () -> "effective step height " + player.getStepHeight());
            player.setShiftKeyDown(true);
            ticks(player, 1);
            check(Math.abs(step.getValue() - base) < 1e-6, () -> "step height while sneaking " + step.getValue());
            player.setShiftKeyDown(false);
            ticks(player, 1);
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            ticks(player, 1);
            check(Math.abs(step.getValue() - base) < 1e-6, () -> "step height kept without the chestpiece: " + step.getValue());
        }).run();
        helper.succeed();
    }

    /**
     * Night vision with light_level 7: inside a closed stone box (light 0) it is applied (400 OP per check); with no energy it is
     * removed on the 4th check; recharged it comes back; with a glowstone roof (light 13 > 7) it is removed on the 4th check.
     */
    @GameTest(template = T_5, batch = BATCH_ABILITIES, timeoutTicks = 200, required = REQUIRED)
    public static void nightVisionInDarknessAndEnergyCutoff(GameTestHelper helper) {
        BlockPos feet = new BlockPos(2, 2, 2);   // template floor = relative y 1
        BlockPos roof = new BlockPos(2, 4, 2);
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(1, 2, 1), new BlockPos(3, 4, 3))) {
            if (!p.equals(feet) && !p.equals(feet.above())) helper.setBlock(p, Blocks.STONE);
        }
        DETestPlayer player = DETestPlayer.create(helper, feet, "de_nv_tester");
        ItemStack[] chest = {ItemStack.EMPTY};
        BlockPos absFeet = helper.absolutePos(feet);
        helper.startSequence()
                .thenWaitUntil(safe(() -> check(helper.getLevel().getRawBrightness(absFeet, 0) <= 7, () -> "box not dark yet: " + helper.getLevel().getRawBrightness(absFeet, 0))))
                .thenExecute(safe(() -> {
                    chest[0] = wear(player, DEContent.CHESTPIECE_WYVERN, DEModules.ITEM_WYVERN_ENERGY, DEModules.ITEM_WYVERN_NIGHT_VISION);
                    IntegerProperty level = host(chest[0]).getEntitiesByType(ModuleTypes.NIGHT_VISION).flatMap(e -> e.getEntityProperties().stream())
                            .filter(p -> p instanceof IntegerProperty).map(p -> (IntegerProperty) p).findFirst()   // entity properties are named by a random UUID (ConfigProperty.generateUnique); light_level is the only int
                            .orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("night vision module has no integer (light_level) property"));
                    check(level.getValue() == 15, () -> "default light_level " + level.getValue());
                    level.setValue(7);
                    IOPStorage storage = energy(chest[0]);
                    long full = storage.getOPStored();
                    ticks(player, 20);
                    check(player.hasEffect(MobEffects.NIGHT_VISION), () -> "no night vision in the dark (light " + helper.getLevel().getRawBrightness(absFeet, 0) + ")");
                    check(full - storage.getOPStored() == EquipCfg.nightVisionEnergy * 20L, () -> "one check used " + (full - storage.getOPStored()) + " OP, expected " + EquipCfg.nightVisionEnergy * 20L);

                    drain(storage, 0);
                    ticks(player, 60);
                    check(player.hasEffect(MobEffects.NIGHT_VISION), () -> "night vision removed before the 4th check without energy");
                    ticks(player, 20);
                    check(!player.hasEffect(MobEffects.NIGHT_VISION), () -> "night vision kept without energy");

                    ModularItemGameTests.fill(storage);
                    ticks(player, 20);
                    check(player.hasEffect(MobEffects.NIGHT_VISION), () -> "night vision not re-applied after recharging");
                    helper.setBlock(roof, Blocks.GLOWSTONE);   // block light 13 at the feet
                }))
                .thenWaitUntil(safe(() -> check(helper.getLevel().getRawBrightness(absFeet, 0) > 7, () -> "box not lit yet: " + helper.getLevel().getRawBrightness(absFeet, 0))))
                .thenExecute(safe(() -> {
                    ticks(player, 80);
                    check(!player.hasEffect(MobEffects.NIGHT_VISION), () -> "night vision kept in light " + helper.getLevel().getRawBrightness(absFeet, 0) + " > light_level 7");
                }))
                .thenSucceed();
    }

    /** Wyvern auto feed (store 150): takes plain food from the inventory into its store and refills hunger to 20. */
    @GameTest(template = T_3, batch = BATCH_ABILITIES, timeoutTicks = 40, required = REQUIRED)
    public static void autoFeedRefillsHungerFromInventoryFood(GameTestHelper helper) {
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(1, 2, 1), "de_feed_tester");
        safe(() -> {
            player.getInventory().setItem(9, new ItemStack(Items.COOKED_BEEF, 10));
            player.getInventory().setItem(10, new ItemStack(Items.POISONOUS_POTATO, 5));   // has an effect: never eaten
            player.getFoodData().setFoodLevel(2);
            player.getFoodData().setSaturation(0);
            wear(player, DEContent.CHESTPIECE_WYVERN, DEModules.ITEM_WYVERN_ENERGY, DEModules.ITEM_WYVERN_AUTO_FEED);
            ticks(player, 60);
            check(player.getFoodData().getFoodLevel() == 20, () -> "food level " + player.getFoodData().getFoodLevel() + " after auto feed");
            int beef = player.getInventory().getItem(9).getCount();
            check(beef < 10 && beef >= 0, () -> "auto feed did not take food from the inventory (" + beef + " beef left)");
            check(player.getInventory().getItem(10).getCount() == 5, () -> "auto feed ate poisonous potatoes");
        }).run();
        helper.succeed();
    }
}
