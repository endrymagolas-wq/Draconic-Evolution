package com.brandon3055.draconicevolution.gametest;

// WP-31b - modular items, server logic (PORTING_PLAN §8.3 "Модульні предмети"; specs/WP-22-damage.md §5.2 A1, A8, A9, A10).
// Donor reference (unchanged in the port except the WP-22 damage facade): api/modules/lib/ModuleGrid.java (install/remove),
// inventory/ModularItemMenu.java (mgui container on the server), items/equipment/IModularMiningTool.java (AOE),
// handlers/ModularArmorEventHandler.java (shield on LivingAttack/LivingDamage, undying on LivingDeath, /kill bypass),
// api/modules/entities/ShieldControlEntity.java (cost x1 normal hit, x3 bypass-armour, x2 more for MAGIC),
// api/modules/entities/UndyingEntity.java (charge, health boost, invulnerability window).
// The wearer is a DETestPlayer (vanilla hurt/death semantics, see that class); module ticking is driven by posting
// LivingEvent.LivingTickEvent each tick (ModularArmorEventHandler.livingTick), because FakePlayers are not ticked.

import com.brandon3055.brandonscore.api.power.IOPStorage;
import com.brandon3055.brandonscore.inventory.PlayerSlot;
import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.brandon3055.draconicevolution.api.modules.ModuleTypes;
import com.brandon3055.draconicevolution.api.modules.entities.ShieldControlEntity;
import com.brandon3055.draconicevolution.api.modules.entities.UndyingEntity;
import com.brandon3055.draconicevolution.api.modules.lib.InstallResult;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleEntity;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleGrid;
import com.brandon3055.draconicevolution.blocks.tileentity.TileGenerator;
import com.brandon3055.draconicevolution.init.DEContent;
import com.brandon3055.draconicevolution.init.DEModules;
import com.brandon3055.draconicevolution.init.EquipCfg;
import com.brandon3055.draconicevolution.inventory.ModularItemMenu;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.brandon3055.draconicevolution.gametest.DEGameTestUtil.*;
import static com.brandon3055.draconicevolution.gametest.ModularTestUtil.*;

@GameTestHolder(DEGameTestUtil.MODID)
@PrefixGameTestTemplate(false)
public class ModularItemGameTests {
    static {
        DEGameTestUtil.installReporter();
    }

    /** required since WP-31b run-03: every test of this class passed in run-01..03 or after its test-bug fix (logs/WP-31b.md). */
    static final boolean REQUIRED = true;

    static String key(Component c) {
        return c == null ? "null" : c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    static long fill(IOPStorage storage) {
        long total = 0;
        for (int i = 0; i < 10_000 && storage.getOPStored() < storage.getMaxOPStored(); i++) {
            long r = storage.receiveOP(storage.getMaxOPStored(), false);
            if (r <= 0) break;
            total += r;
        }
        return total;
    }

    /** Module grid through the real ModularItemMenu (mgui container): install from the cursor, tier rejection, removal, quick-move, NBT persistence. */
    @GameTest(template = T_3, batch = BATCH_MODULAR, timeoutTicks = 40, required = REQUIRED)
    public static void moduleGridInstallRemoveThroughMenu(GameTestHelper helper) {
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(1, 2, 1), "de_grid_tester");
        safe(() -> {
            ItemStack chest = new ItemStack(DEContent.CHESTPIECE_WYVERN.get());
            player.holdMainHand(chest);
            ModularItemMenu menu = new ModularItemMenu(1, player.getInventory(), new PlayerSlot(player, InteractionHand.MAIN_HAND));
            player.containerMenu = menu;
            check(menu.stillValid(player), () -> "menu not valid for the held chestpiece");
            ModuleGrid grid = menu.getGrid();
            ModuleHost host = menu.getModuleHost();
            check(host == host(chest), () -> "menu host is not the held stack's host");

            menu.setCarried(new ItemStack(DEModules.ITEM_WYVERN_SHIELD_CONTROL.get()));
            InstallResult r1 = grid.cellClicked(grid.getCell(0, 0), 0, 0, 0, ClickType.PICKUP);
            check(r1 == null && menu.getCarried().isEmpty(), () -> "install from cursor: result " + (r1 == null ? null : r1.resultType) + ", carried " + menu.getCarried());
            check(host.getModuleEntities().size() == 1 && grid.getCell(0, 0).hasEntity(), () -> "host modules " + host.getModuleEntities());

            menu.setCarried(new ItemStack(DEModules.ITEM_CHAOTIC_SHIELD_CONTROL.get()));
            InstallResult r2 = grid.cellClicked(grid.getCell(2, 0), 0, 0, 0, ClickType.PICKUP);
            check(r2 != null && r2.resultType == InstallResult.InstallResultType.NO && r2.reason != null && !r2.reason.isEmpty()
                    && key(r2.reason.get(0)).equals("modular_item.draconicevolution.cant_install.level_high"), () -> "chaotic module in a wyvern host: " + (r2 == null ? "installed" : r2.resultType + " " + (r2.reason == null ? "" : key(r2.reason.get(0)))));
            check(menu.getCarried().getCount() == 1, () -> "rejected module consumed");
            menu.setCarried(ItemStack.EMPTY);

            // persistence: a copy of the stack (capability NBT round trip) keeps the module
            ItemStack copy = chest.copy();
            check(host(copy).getEntitiesByType(ModuleTypes.SHIELD_CONTROLLER).count() == 1, () -> "copied stack lost the module: " + host(copy).getModuleEntities());

            InstallResult r3 = grid.cellClicked(grid.getCell(0, 0), 0, 0, 0, ClickType.PICKUP);
            check(r3 == null && menu.getCarried().is(DEModules.ITEM_WYVERN_SHIELD_CONTROL.get()) && host.getModuleEntities().isEmpty(), () -> "remove to cursor: carried " + menu.getCarried() + ", modules " + host.getModuleEntities());
            menu.setCarried(ItemStack.EMPTY);

            ItemStack energy = new ItemStack(DEModules.ITEM_WYVERN_ENERGY.get());
            player.getInventory().setItem(5, energy);
            Slot slot = menu.slots.stream().filter(s -> s.getItem() == energy).findFirst().orElse(null);
            check(slot != null, () -> "energy module slot not found in the menu");
            check(menu.quickMoveModule(player, slot) && energy.isEmpty(), () -> "quick-move did not install the energy module");
            check(host.getEntitiesByType(ModuleTypes.ENERGY_STORAGE).count() == 1, () -> "modules after quick-move " + host.getModuleEntities());
            player.containerMenu = player.inventoryMenu;
        }).run();
        helper.succeed();
    }

    /** Energy: a module adds its capacity; a generator's charge slot (BC power slot) charges the item. */
    @GameTest(template = T_3, batch = BATCH_MODULAR, timeoutTicks = 100, required = REQUIRED)
    public static void modularItemEnergyModuleAndCharging(GameTestHelper helper) {
        BlockPos genPos = new BlockPos(1, 2, 1);
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(0, 2, 0), "de_energy_tester");
        ItemStack chest = new ItemStack(DEContent.CHESTPIECE_WYVERN.get());
        long[] base = {0};
        helper.setBlock(genPos, DEContent.GENERATOR.get());
        TileGenerator gen = tile(helper, genPos, TileGenerator.class);
        helper.startSequence()
                .thenExecute(safe(() -> {
                    base[0] = energy(chest).getMaxOPStored();
                    check(base[0] == EquipCfg.getBaseChestpieceEnergy(com.brandon3055.brandonscore.api.TechLevel.WYVERN), () -> "base capacity " + base[0]);
                    install(chest, player, DEModules.ITEM_DRACONIUM_ENERGY);
                    long withModule = energy(chest).getMaxOPStored();
                    check(withModule == base[0] + 1_000_000L, () -> "capacity with a draconium energy module " + withModule + ", expected " + (base[0] + 1_000_000L));
                    gen.opStorage.modifyEnergyStored(100_000L);
                    check(gen.itemHandler.insertItem(3, chest, false).isEmpty(), () -> "generator charge slot rejected the chestpiece");
                }))
                .thenIdle(5)
                .thenExecute(safe(() -> {
                    ItemStack inSlot = gen.itemHandler.getStackInSlot(3);
                    long stored = energy(inSlot).getOPStored();
                    check(stored > 0, () -> "chestpiece not charged by the generator slot");
                    check(stored + gen.opStorage.getOPStored() == 100_000L, () -> "energy not conserved: item " + stored + " + generator " + gen.opStorage.getOPStored());
                }))
                .thenSucceed();
    }

    /** Wyvern pickaxe + draconium AOE module (radius 1): one break through ServerPlayerGameMode clears exactly the 3x3 face. */
    @GameTest(template = T_9, batch = BATCH_MODULAR, timeoutTicks = 60, required = REQUIRED)
    public static void modularPickaxeMinesAoeArea(GameTestHelper helper) {
        BlockPos center = new BlockPos(6, 3, 4);
        List<BlockPos> area = BlockPos.betweenClosedStream(center.offset(0, -1, -1), center.offset(0, 1, 1)).map(BlockPos::immutable).toList();
        List<BlockPos> keep = new java.util.ArrayList<>();
        BlockPos.betweenClosedStream(center.offset(0, -1, -2), center.offset(0, 1, 2)).map(BlockPos::immutable).filter(p -> !area.contains(p)).forEach(keep::add);
        BlockPos.betweenClosedStream(center.offset(1, -1, -1), center.offset(1, 1, 1)).map(BlockPos::immutable).forEach(keep::add);   // behind (depth 0)
        area.forEach(p -> helper.setBlock(p, Blocks.STONE));
        keep.forEach(p -> helper.setBlock(p, Blocks.STONE));
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(4, 2, 4), "de_mining_tester");
        safe(() -> {
            ItemStack pick = new ItemStack(DEContent.PICKAXE_WYVERN.get());
            // EquipCfg *_BASE_ENERGY = 0 (same in the 1.20.1 donor): modular tools hold energy only through energy modules
            install(pick, player, DEModules.ITEM_WYVERN_ENERGY);
            install(pick, player, DEModules.ITEM_DRACONIUM_AOE);
            check(host(pick).getModuleData(ModuleTypes.AOE) != null && host(pick).getModuleData(ModuleTypes.AOE).aoe() == 1, () -> "AOE data " + host(pick).getModuleData(ModuleTypes.AOE));
            fill(energy(pick));
            long before = energy(pick).getOPStored();
            check(before >= 9L * EquipCfg.energyHarvest, () -> "pickaxe holds only " + before + " OP");
            player.holdMainHand(pick);
            player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(center)).add(-0.5, 0, 0));
            player.gameMode.destroyBlock(helper.absolutePos(center));
            for (BlockPos p : area) checkAt(helper, helper.getBlockState(p).isAir(), p, () -> "AOE block not broken");
            for (BlockPos p : keep) checkAt(helper, helper.getBlockState(p).is(Blocks.STONE), p, () -> "block outside the 3x3 AOE was broken");
            // ModuleHelper.handleItemCollection drops the collected stacks as ItemEntities at the player (pickup delay 0); a real
            // player picks them up in its own tick, a FakePlayer is not ticked, so they are counted in the world.
            int cobble = entitiesIn(helper, net.minecraft.world.entity.item.ItemEntity.class, new BlockPos(3, 1, 3), new BlockPos(5, 4, 5)).stream()
                    .filter(e -> e.getItem().is(Items.COBBLESTONE)).mapToInt(e -> e.getItem().getCount()).sum();
            check(cobble == 9, () -> "collected " + cobble + " cobblestone at the player, expected 9");
            discardAll(helper, net.minecraft.world.entity.item.ItemEntity.class, new BlockPos(3, 1, 3), new BlockPos(5, 4, 5));
            long used = before - energy(player.getMainHandItem()).getOPStored();
            check(used >= 9L * EquipCfg.energyHarvest, () -> "AOE mining used " + used + " OP, expected >= " + 9L * EquipCfg.energyHarvest);
        }).run();
        helper.succeed();
    }

    static ItemStack chaoticChest(DETestPlayer player, int recoveryModules, boolean undyingCharged) {
        ItemStack chest = new ItemStack(DEContent.CHESTPIECE_CHAOTIC.get());
        install(chest, player, DEModules.ITEM_CHAOTIC_ENERGY);
        if (recoveryModules > 0) {
            install(chest, player, DEModules.ITEM_CHAOTIC_SHIELD_CONTROL);
            for (int i = 0; i < recoveryModules; i++) install(chest, player, DEModules.ITEM_CHAOTIC_SHIELD_RECOVERY);
        }
        if (undyingCharged) {
            ItemStack undying = new ItemStack(DEModules.ITEM_CHAOTIC_UNDYING.get());
            undying.getOrCreateTag().putInt("charge", 45 * 20);   // chaotic_undying charge_ticks default (DEModules undyingData)
            install(chest, player, undying);
        }
        fill(energy(chest));
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        return chest;
    }

    static ShieldControlEntity shield(ItemStack chest) {
        return host(chest).getEntitiesByType(ModuleTypes.SHIELD_CONTROLLER).map(e -> (ShieldControlEntity) e).findFirst().orElseThrow(() -> new IllegalStateException("no shield controller"));
    }

    static UndyingEntity undying(ItemStack chest) {
        return host(chest).getEntitiesByType(ModuleTypes.UNDYING).map(e -> (UndyingEntity) e).findFirst().orElseThrow(() -> new IllegalStateException("no undying module"));
    }

    /**
     * A9/A8: shield charges from item energy, absorbs a normal hit at x1 (sweet berry bush: no flags), MAGIC at x6, a
     * bypass-armour hit at x3 (1.19.2 DamageSource.GENERIC is bypassArmor, like #bypasses_armor contains generic in 1.20.1),
     * then absorbs part of an oversized hit at the LivingDamageEvent stage.
     */
    @GameTest(template = T_3, batch = BATCH_MODULAR, timeoutTicks = 300, required = REQUIRED)
    public static void armorShieldAbsorbsDamage(GameTestHelper helper) {
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(1, 2, 1), "de_shield_tester");
        ItemStack[] chest = {ItemStack.EMPTY};
        helper.startSequence()
                .thenExecute(safe(() -> chest[0] = chaoticChest(player, 4, false)))
                .thenExecuteFor(150, safe(player::postLivingTick))
                .thenExecute(safe(() -> {
                    ShieldControlEntity s = shield(chest[0]);
                    check(s.getShieldCapacity() == 80 && s.getShieldPoints() >= 80, () -> "shield " + s.getShieldPoints() + "/" + s.getShieldCapacity() + " after 150 ticks (4 x chaotic recovery = 80 points, 1/t)");
                    check(player.getHealth() == 20F, () -> "health " + player.getHealth());
                    boolean hurt1 = player.hurt(DamageSource.SWEET_BERRY_BUSH, 10F);
                    check(!hurt1 && player.getHealth() == 20F, () -> "normal hit: hurt=" + hurt1 + " health " + player.getHealth());
                    check(Math.abs(s.getShieldPoints() - 70) < 1e-6, () -> "normal hit cost " + (80 - s.getShieldPoints()) + " points, expected 10 (x1)");
                    player.invulnerableTime = 0;
                    boolean hurt2 = player.hurt(DamageSource.MAGIC, 5F);
                    check(!hurt2 && player.getHealth() == 20F, () -> "magic hit: hurt=" + hurt2 + " health " + player.getHealth());
                    check(Math.abs(s.getShieldPoints() - 40) < 1e-6, () -> "MAGIC 5 cost " + (70 - s.getShieldPoints()) + " points, expected 30 (x3 bypass armour, x2 magic)");
                    check(s.getShieldCoolDown() > 0, () -> "no recharge cool-down after a hit");
                    player.invulnerableTime = 0;
                    boolean hurt3 = player.hurt(DamageSource.GENERIC, 2F);
                    check(!hurt3 && Math.abs(s.getShieldPoints() - 34) < 1e-6, () -> "GENERIC 2 (bypass armour) cost " + (40 - s.getShieldPoints()) + " points, expected 6 (x3)");
                    player.invulnerableTime = 0;
                    player.hurt(DamageSource.SWEET_BERRY_BUSH, 40F);   // > shield: the LivingDamageEvent stage takes the remaining 34 points
                    check(s.getShieldPoints() == 0, () -> "shield after the oversized hit " + s.getShieldPoints());
                    check(player.getHealth() >= 14F - 1e-4F && player.getHealth() < 20F, () -> "health after 40 damage with 34 shield points: " + player.getHealth() + " (expected 14 without armour attributes)");
                }))
                .thenSucceed();
    }

    /** A10: a charged undying module cancels a lethal hit, boosts health, opens an invulnerability window, and is discharged. */
    @GameTest(template = T_3, batch = BATCH_MODULAR, timeoutTicks = 60, required = REQUIRED)
    public static void undyingModuleSavesFromLethalHit(GameTestHelper helper) {
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(1, 2, 1), "de_undying_tester");
        safe(() -> {
            ItemStack chest = chaoticChest(player, 0, true);
            UndyingEntity u = undying(chest);
            check(u.isCharged(), () -> "undying not charged from the module item NBT (charge " + u.getCharge() + ")");
            player.hurt(DamageSource.GENERIC, 1000F);
            check(!player.isDeadFlag() && player.isAlive(), () -> "player died with a charged undying module");
            check(player.getHealth() == 20F, () -> "health after undying " + player.getHealth() + ", expected the 20 HP boost");
            check(!u.isCharged(), () -> "undying still charged after triggering");
            player.invulnerableTime = 0;
            boolean hurt = player.hurt(DamageSource.GENERIC, 10F);
            check(!hurt && player.getHealth() == 20F, () -> "hit during the undying invulnerability: hurt=" + hurt + " health " + player.getHealth());
        }).run();
        helper.succeed();
    }

    /** A1: /kill (Entity.kill -> OUT_OF_WORLD Float.MAX_VALUE) bypasses shield and undying; neither is consumed. */
    @GameTest(template = T_3, batch = BATCH_MODULAR, timeoutTicks = 300, required = REQUIRED)
    public static void killBypassesShieldAndUndying(GameTestHelper helper) {
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(1, 2, 1), "de_kill_tester");
        ItemStack[] chest = {ItemStack.EMPTY};
        helper.startSequence()
                .thenExecute(safe(() -> chest[0] = chaoticChest(player, 4, true)))
                .thenExecuteFor(120, safe(player::postLivingTick))
                .thenExecute(safe(() -> {
                    ShieldControlEntity s = shield(chest[0]);
                    UndyingEntity u = undying(chest[0]);
                    double points = s.getShieldPoints();
                    check(points > 0 && u.isCharged(), () -> "setup: shield " + points + " undying charged " + u.isCharged());
                    player.kill();
                    check(player.isDeadFlag() && player.getHealth() <= 0F, () -> "player survived /kill: health " + player.getHealth());
                    check(s.getShieldPoints() == points, () -> "/kill consumed shield points: " + points + " -> " + s.getShieldPoints());
                    check(u.isCharged(), () -> "/kill consumed the undying charge");
                }))
                .thenSucceed();
    }

    /**
     * A12/A13/A14 (specs/WP-22-damage.md §5.2): a guardian melee hit (DEDamage.guardian, flags F + scales with difficulty)
     * ignores Resistance V, is scaled for EASY as min(x/2+1, x) (server.properties difficulty=easy) and adds the type's
     * 0.1 exhaustion; a flag-less hit is fully stopped by Resistance V (control).
     */
    @GameTest(template = T_3, batch = BATCH_MODULAR, timeoutTicks = 40, required = REQUIRED)
    public static void guardianMeleeBypassesResistance(GameTestHelper helper) {
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(1, 2, 1), "de_resistance_tester");
        net.minecraft.world.entity.monster.Zombie zombie = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(0, 2, 0));
        safe(() -> {
            check(helper.getLevel().getDifficulty() == net.minecraft.world.Difficulty.EASY, () -> "test expects difficulty EASY, server has " + helper.getLevel().getDifficulty());
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 200, 4));
            player.hurt(DamageSource.SWEET_BERRY_BUSH, 10F);
            check(player.getHealth() == 20F, () -> "Resistance V did not stop a flag-less hit: health " + player.getHealth());
            player.invulnerableTime = 0;
            float exhaustion = player.getFoodData().getExhaustionLevel();
            player.hurt(com.brandon3055.draconicevolution.init.DEDamage.guardian(helper.getLevel(), zombie), 10F);
            float expected = 20F - Math.min(10F / 2F + 1F, 10F);
            check(Math.abs(player.getHealth() - expected) < 1e-4, () -> "guardian hit: health " + player.getHealth() + ", expected " + expected + " (EASY scaling, Resistance bypassed)");
            float dEx = player.getFoodData().getExhaustionLevel() - exhaustion;
            check(Math.abs(dEx - 0.1F) < 1e-4, () -> "guardian hit exhaustion " + dEx + ", expected 0.1");
            zombie.discard();
        }).run();
        helper.succeed();
    }

    /** A4: a mob (husk: no sunburn) wearing a shielded DE chestpiece is shielded by its own ticking, and /kill still kills it. */
    @GameTest(template = T_3, batch = BATCH_MODULAR, timeoutTicks = 300, required = REQUIRED)
    public static void mobWearingChestpieceShieldedButKillable(GameTestHelper helper) {
        DETestPlayer setup = DETestPlayer.create(helper, new BlockPos(2, 2, 2), "de_mob_armor_setup");
        net.minecraft.world.entity.monster.Husk husk = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.HUSK, new BlockPos(1, 2, 1));
        ItemStack[] chest = {ItemStack.EMPTY};
        helper.startSequence()
                .thenExecute(safe(() -> {
                    ItemStack c = new ItemStack(DEContent.CHESTPIECE_CHAOTIC.get());
                    install(c, setup, DEModules.ITEM_CHAOTIC_ENERGY);
                    install(c, setup, DEModules.ITEM_CHAOTIC_SHIELD_CONTROL);
                    for (int i = 0; i < 4; i++) install(c, setup, DEModules.ITEM_CHAOTIC_SHIELD_RECOVERY);
                    fill(energy(c));
                    husk.setItemSlot(EquipmentSlot.CHEST, c);
                    husk.setDropChance(EquipmentSlot.CHEST, 0F);
                    chest[0] = husk.getItemBySlot(EquipmentSlot.CHEST);
                }))
                .thenWaitUntil(safe(() -> check(shield(chest[0]).getShieldPoints() >= 40, () -> "husk shield " + shield(chest[0]).getShieldPoints())))
                .thenExecute(safe(() -> {
                    ShieldControlEntity s = shield(chest[0]);
                    double before = s.getShieldPoints();
                    float health = husk.getHealth();
                    boolean hurt = husk.hurt(DamageSource.SWEET_BERRY_BUSH, 10F);
                    check(!hurt && husk.getHealth() == health, () -> "husk shield did not absorb: hurt=" + hurt + " health " + husk.getHealth());
                    check(before - s.getShieldPoints() > 9.99, () -> "husk shield cost " + (before - s.getShieldPoints()));
                    husk.kill();
                    check(husk.isDeadOrDying() || !husk.isAlive(), () -> "husk survived /kill: health " + husk.getHealth());
                }))
                .thenIdle(25)
                .thenExecute(safe(() -> discardAll(helper, net.minecraft.world.entity.Entity.class, new BlockPos(-1, 1, -1), new BlockPos(3, 4, 3))))
                .thenSucceed();
    }
}
