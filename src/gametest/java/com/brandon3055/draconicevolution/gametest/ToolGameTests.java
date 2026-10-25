package com.brandon3055.draconicevolution.gametest;

// Modular tools, server logic (sword, bow, axe + tree harvest, shovel, hoe, staff, AOE size, tools without energy, tiers).
// Every expectation below is taken from the implementation (unchanged from the 1.20.1 donor except the 1.19.2 API renames):
//   items/equipment/IModularMelee.onLeftClickEntity/dealAOEDamage   sword energy per hit, AOE sweep (cone 100 deg, radius attack_aoe)
//   items/equipment/IModularTieredItem.getAttackDamage              damage modules count only while energy >= energyAttack * damage
//   items/equipment/IModularMiningTool.onBlockStartBreak/getMiningArea  AOE radius = mining_aoe property (<= module aoe), plane of the hit face
//   items/equipment/IModularItem.getDestroySpeed                     speed 1 (bare hand) when energy < energyHarvest
//   items/equipment/ModularBow.use/releaseUsing                      ammo (or Infinity / creative), energy = bowBaseEnergy * damage * power * 3
//   items/equipment/ModularHoe.useOn                                 tills the clicked block and the (2*tool_aoe+1)^2 square around it
//   items/equipment/ModularStaff                                     mines anything (isCorrectToolForDrops = true), melee; no projectile (upstream TODO)
//   api/modules/entities/TreeHarvestEntity + logic/TreeHarvestHandler  right-click a log, hold: logs then leaves, energyHarvest per block
// Players are DETestPlayers (not ticked): attack charge, main-hand attributes and item-use ticks are driven by the test.

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.capability.PropertyProvider;
import com.brandon3055.draconicevolution.api.modules.ModuleTypes;
import com.brandon3055.draconicevolution.entity.projectile.DraconicArrowEntity;
import com.brandon3055.draconicevolution.init.DEContent;
import com.brandon3055.draconicevolution.init.DEModules;
import com.brandon3055.draconicevolution.init.EquipCfg;
import com.brandon3055.draconicevolution.items.equipment.DETier;
import com.brandon3055.draconicevolution.items.equipment.IModularTieredItem;
import com.brandon3055.draconicevolution.items.equipment.ModularBow;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.brandon3055.draconicevolution.gametest.DEGameTestUtil.*;
import static com.brandon3055.draconicevolution.gametest.ModularTestUtil.*;

@GameTestHolder(DEGameTestUtil.MODID)
@PrefixGameTestTemplate(false)
public class ToolGameTests {
    static {
        DEGameTestUtil.installReporter();
    }

    static final boolean REQUIRED = true;
    public static final String BATCH_TOOLS = "de_tools";

    // ==== helpers ====================================================================================================

    @SafeVarargs
    static ItemStack tool(Supplier<? extends Item> item, DETestPlayer player, boolean charged, Supplier<? extends Item>... modules) {
        ItemStack stack = new ItemStack(item.get());
        for (Supplier<? extends Item> m : modules) install(stack, player, m);
        if (charged) ModularItemGameTests.fill(energy(stack));
        return stack;
    }

    static List<BlockPos> square(BlockPos center, int r) {
        List<BlockPos> list = new ArrayList<>();
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) list.add(center.offset(x, 0, z));
        return list;
    }

    /** A player standing on {@code center} (feet one block above it), looking straight down at its top face. */
    static DETestPlayer standOnAndLookDown(GameTestHelper helper, BlockPos center, String name) {
        DETestPlayer player = DETestPlayer.create(helper, center.above(), name);
        Vec3 eye = player.getEyePosition();
        player.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(eye.x, eye.y - 4, eye.z));
        return player;
    }

    static int countItems(GameTestHelper helper, Item item, BlockPos min, BlockPos max) {
        return entitiesIn(helper, ItemEntity.class, min, max).stream().filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }

    static int setInt(ItemStack stack, String property, int value) {
        check(host(stack) instanceof PropertyProvider p && p.hasInt(property), () -> stack + " has no integer property " + property);
        ((PropertyProvider) host(stack)).getInt(property).setValue(value);
        return ((PropertyProvider) host(stack)).getInt(property).getValue();
    }

    static Husk dummy(GameTestHelper helper, BlockPos pos) {
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, pos);
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        husk.getAttribute(Attributes.ARMOR).setBaseValue(0);
        husk.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        husk.setHealth(1000);
        return husk;
    }

    static float lost(Husk husk) {
        return 1000F - husk.getHealth();
    }

    /** Checks that every block of {@code broken} became air and every block of {@code kept} is still there. */
    static void checkMined(GameTestHelper helper, List<BlockPos> broken, List<BlockPos> kept) {
        for (BlockPos p : broken) checkAt(helper, helper.getBlockState(p).isAir(), p, () -> "block in the AOE area not broken: " + helper.getBlockState(p));
        for (BlockPos p : kept) checkAt(helper, !helper.getBlockState(p).isAir(), p, () -> "block outside the AOE area was broken");
    }

    // ==== sword ======================================================================================================

    /**
     * Sword damage: the damage module adds its points to the attack damage attribute and to the dealt damage; one hit takes
     * energyAttack * damage OP; with too little energy the module's points are dropped (getAttackDamage).
     */
    @GameTest(template = T_9, batch = BATCH_TOOLS, timeoutTicks = 60, required = REQUIRED)
    public static void swordDamageModuleAndEnergyPerHit(GameTestHelper helper) {
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(2, 1, 2), "de_sword_dmg");
        Husk a = dummy(helper, new BlockPos(2, 1, 4));
        Husk b = dummy(helper, new BlockPos(6, 1, 4));
        Husk c = dummy(helper, new BlockPos(4, 1, 7));
        safe(() -> {
            ItemStack plain = tool(DEContent.SWORD_WYVERN, player, true, DEModules.ITEM_WYVERN_ENERGY);
            ItemStack dmg = tool(DEContent.SWORD_WYVERN, player, true, DEModules.ITEM_WYVERN_ENERGY, DEModules.ITEM_WYVERN_DAMAGE);
            double tierDamage = EquipCfg.wyvernDamage * EquipCfg.swordDamageMultiplier;   // DETier(WYVERN).getAttackDamageBonus * multiplier

            player.holdMainHand(plain);
            player.refreshMainHandAttributes();
            double attrPlain = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            check(Math.abs(attrPlain - tierDamage) < 1e-4, () -> "plain wyvern sword attack damage " + attrPlain + ", expected " + tierDamage);
            player.chargeAttack();
            player.attack(a);
            check(Math.abs(lost(a) - attrPlain) < 1e-3, () -> "plain sword dealt " + lost(a) + ", expected " + attrPlain);

            player.holdMainHand(dmg);
            player.refreshMainHandAttributes();
            double attrDmg = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            check(Math.abs(attrDmg - attrPlain - 4) < 1e-4, () -> "wyvern damage module: attack damage " + attrDmg + " vs " + attrPlain + " without (expected +4)");
            long before = energy(dmg).getOPStored();
            player.chargeAttack();
            player.attack(b);
            check(Math.abs(lost(b) - attrDmg) < 1e-3, () -> "sword with damage module dealt " + lost(b) + ", expected " + attrDmg);
            long perHit = (long) (EquipCfg.energyAttack * (float) ((IModularTieredItem) dmg.getItem()).getAttackDamage(host(dmg), dmg));
            long used = before - energy(dmg).getOPStored();
            check(used == perHit && used > 0, () -> "one hit used " + used + " OP, expected energyAttack * damage = " + perHit);

            // drain below one hit: the module's +4 is gone
            energy(dmg).modifyEnergyStored(-energy(dmg).getOPStored());
            player.refreshMainHandAttributes();
            double attrEmpty = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            check(Math.abs(attrEmpty - attrPlain) < 1e-4, () -> "empty sword attack damage " + attrEmpty + ", expected the tier damage " + attrPlain + " (modules need energy)");
            player.chargeAttack();
            player.attack(c);
            check(Math.abs(lost(c) - attrPlain) < 1e-3, () -> "empty sword dealt " + lost(c) + ", expected " + attrPlain);
            a.discard();
            b.discard();
            c.discard();
        }).run();
        helper.succeed();
    }

    /**
     * Sword AOE (wyvern AOE: aoe 2 -> attack_aoe 3.0 blocks around the target, 100 deg cone in front of the player): two mobs
     * 2.2 blocks beside the target (outside vanilla's 1-block sweep) take the full charged damage; a mob behind the player
     * does not; energy = one hit per entity in range (target + 2). Without AOE module / without energy they are untouched.
     */
    @GameTest(template = T_9, batch = BATCH_TOOLS, timeoutTicks = 60, required = REQUIRED)
    public static void swordAoeHitsMobsAroundTarget(GameTestHelper helper) {
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(4, 1, 2), "de_sword_aoe");
        player.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(4.5, 2.6, 8.5)));   // facing +Z (yaw 0)
        Husk target = dummy(helper, new BlockPos(4, 1, 4));
        Husk left = dummy(helper, new BlockPos(2, 1, 5));
        Husk right = dummy(helper, new BlockPos(6, 1, 5));
        Husk far = dummy(helper, new BlockPos(4, 1, 8));    // 4 blocks behind the target: outside attack_aoe 3
        safe(() -> {
            check(Math.abs(player.getYRot()) < 1, () -> "player yaw " + player.getYRot());
            ItemStack sword = tool(DEContent.SWORD_WYVERN, player, true, DEModules.ITEM_WYVERN_ENERGY, DEModules.ITEM_WYVERN_AOE);
            check(host(sword).getModuleData(ModuleTypes.AOE).aoe() == 2, () -> "AOE data " + host(sword).getModuleData(ModuleTypes.AOE));
            player.holdMainHand(sword);
            player.refreshMainHandAttributes();
            double attr = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            long before = energy(sword).getOPStored();
            long perHit = (long) (EquipCfg.energyAttack * (float) ((IModularTieredItem) sword.getItem()).getAttackDamage(host(sword), sword));
            player.chargeAttack();
            player.attack(target);
            check(Math.abs(lost(target) - attr) < 1e-3, () -> "target took " + lost(target) + ", expected " + attr);
            float aoeDamage = (float) ((IModularTieredItem) sword.getItem()).getAttackDamage(host(sword), sword);   // full charge: x(0.2 + 0.8)
            check(Math.abs(lost(left) - aoeDamage) < 1e-3 && Math.abs(lost(right) - aoeDamage) < 1e-3,
                    () -> "AOE damage left " + lost(left) + " right " + lost(right) + ", expected " + aoeDamage + " each");
            check(lost(far) == 0, () -> "mob outside attack_aoe was hit: " + lost(far));
            long used = before - energy(sword).getOPStored();
            check(used == 3 * perHit, () -> "AOE swing used " + used + " OP, expected 3 x " + perHit + " (target + 2 in range)");

            // no AOE module: the same layout, side mobs untouched
            for (Husk h : List.of(target, left, right)) { h.setHealth(1000); h.invulnerableTime = 0; }
            ItemStack plain = tool(DEContent.SWORD_WYVERN, player, true, DEModules.ITEM_WYVERN_ENERGY);
            player.holdMainHand(plain);
            player.refreshMainHandAttributes();
            player.chargeAttack();
            player.attack(target);
            check(lost(target) > 0 && lost(left) == 0 && lost(right) == 0, () -> "sword without AOE module: target " + lost(target) + " left " + lost(left) + " right " + lost(right));

            // AOE module but no energy: no sweep
            for (Husk h : List.of(target, left, right)) { h.setHealth(1000); h.invulnerableTime = 0; }
            energy(sword).modifyEnergyStored(-energy(sword).getOPStored());
            player.holdMainHand(sword);
            player.refreshMainHandAttributes();
            player.chargeAttack();
            player.attack(target);
            check(lost(target) > 0 && lost(left) == 0 && lost(right) == 0, () -> "empty AOE sword: target " + lost(target) + " left " + lost(left) + " right " + lost(right));
            for (Husk h : List.of(target, left, right, far)) h.discard();
        }).run();
        helper.succeed();
    }

    // ==== bow ========================================================================================================

    static DraconicArrowEntity shoot(GameTestHelper helper, DETestPlayer player, ItemStack bow, int drawTicks) {
        player.holdMainHand(bow);
        InteractionResultHolder<ItemStack> r = bow.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        check(r.getResult().consumesAction() && player.isUsingItem(), () -> "bow did not start drawing: " + r.getResult());
        player.setUseItemRemaining(bow.getUseDuration() - drawTicks);
        player.releaseUsingItem();
        List<DraconicArrowEntity> arrows = entitiesIn(helper, DraconicArrowEntity.class, new BlockPos(-1, 0, -1), new BlockPos(5, 4, 5));
        check(arrows.size() == 1, () -> "expected one DE arrow after release, found " + arrows.size());
        DraconicArrowEntity arrow = arrows.get(0);
        arrow.discard();
        return arrow;
    }

    /**
     * Wyvern bow, full draw (20 ticks, no speed module): one DraconicArrowEntity at 3 m/t, base damage 2, crit; one arrow
     * consumed; energy = bowBaseEnergy * 2 * 1 * 3. Infinity: fires without arrows, nothing consumed, pickup creative-only.
     * No arrows (no Infinity) or not enough energy: the bow does not even start drawing.
     */
    @GameTest(template = T_5, batch = BATCH_TOOLS, timeoutTicks = 40, required = REQUIRED)
    public static void bowFiresArrowUsingEnergyAndAmmo(GameTestHelper helper) {
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(2, 1, 2), "de_bow_tester");
        safe(() -> {
            ItemStack bow = tool(DEContent.BOW_WYVERN, player, true, DEModules.ITEM_WYVERN_ENERGY);
            check(ModularBow.getChargeTicks(bow) == 20, () -> "charge ticks " + ModularBow.getChargeTicks(bow));

            // no arrows, no infinity -> cannot draw
            player.holdMainHand(bow);
            InteractionResultHolder<ItemStack> none = bow.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            check(!none.getResult().consumesAction() && !player.isUsingItem(), () -> "bow drew without ammo: " + none.getResult());

            player.getInventory().setItem(9, new ItemStack(Items.ARROW, 4));
            long before = energy(bow).getOPStored();
            DraconicArrowEntity arrow = shoot(helper, player, bow, 20);
            double speed = arrow.getDeltaMovement().length();
            check(Math.abs(speed - 3.0) < 0.15, () -> "arrow speed " + speed + ", expected 3.0 (full draw)");
            check(Math.abs(arrow.getBaseDamage() - 2.0) < 1e-6, () -> "arrow base damage " + arrow.getBaseDamage());
            check(arrow.isCritArrow(), () -> "full draw arrow is not a crit arrow");
            check(arrow.pickup == AbstractArrow.Pickup.ALLOWED, () -> "arrow pickup " + arrow.pickup);
            check(player.getInventory().getItem(9).getCount() == 3, () -> "arrows left " + player.getInventory().getItem(9));
            long used = before - energy(bow).getOPStored();
            long expected = (long) (EquipCfg.bowBaseEnergy * 2.0 * 1.0F * 3);
            check(used == expected, () -> "shot used " + used + " OP, expected " + expected);

            // half draw: power (p^2 + 2p) / 3 with p = 10/20 -> 0.4167
            DraconicArrowEntity half = shoot(helper, player, bow, 10);
            float power = (0.25F + 1.0F) / 3F;
            check(Math.abs(half.getDeltaMovement().length() - power * 3) < 0.1 && !half.isCritArrow(), () -> "half draw speed " + half.getDeltaMovement().length() + ", expected " + power * 3);

            // infinity: no ammo needed, nothing consumed
            player.getInventory().setItem(9, ItemStack.EMPTY);
            ItemStack infBow = tool(DEContent.BOW_WYVERN, player, true, DEModules.ITEM_WYVERN_ENERGY);
            infBow.enchant(Enchantments.INFINITY_ARROWS, 1);
            DraconicArrowEntity inf = shoot(helper, player, infBow, 20);
            check(inf.pickup == AbstractArrow.Pickup.CREATIVE_ONLY, () -> "infinity arrow pickup " + inf.pickup);

            // not enough energy for a shot -> cannot draw
            player.getInventory().setItem(9, new ItemStack(Items.ARROW, 4));
            ItemStack empty = tool(DEContent.BOW_WYVERN, player, false, DEModules.ITEM_WYVERN_ENERGY);
            player.holdMainHand(empty);
            InteractionResultHolder<ItemStack> noPower = empty.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            check(!noPower.getResult().consumesAction() && !player.isUsingItem(), () -> "bow without energy drew: " + noPower.getResult());
            check(player.getInventory().getItem(9).getCount() == 4, () -> "arrow consumed by an empty bow");
        }).run();
        helper.succeed();
    }

    /** Projectile modules: velocity module x(1 + 0.15) speed, damage module x(1 + 0.25) base damage; the shot energy follows the damage. */
    @GameTest(template = T_5, batch = BATCH_TOOLS, timeoutTicks = 40, required = REQUIRED)
    public static void bowProjectileModulesChangeArrow(GameTestHelper helper) {
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(2, 1, 2), "de_bow_mods");
        safe(() -> {
            player.getInventory().setItem(9, new ItemStack(Items.ARROW, 16));
            ItemStack fast = tool(DEContent.BOW_WYVERN, player, true, DEModules.ITEM_WYVERN_ENERGY, DEModules.ITEM_WYVERN_PROJ_VELOCITY);
            DraconicArrowEntity a = shoot(helper, player, fast, 20);
            check(Math.abs(a.getDeltaMovement().length() - 3.45) < 0.15, () -> "velocity module arrow speed " + a.getDeltaMovement().length() + ", expected 3.45");
            check(Math.abs(a.getBaseDamage() - 2.0) < 1e-6, () -> "velocity module changed damage: " + a.getBaseDamage());

            ItemStack strong = tool(DEContent.BOW_WYVERN, player, true, DEModules.ITEM_WYVERN_ENERGY, DEModules.ITEM_WYVERN_PROJ_DAMAGE);
            long before = energy(strong).getOPStored();
            DraconicArrowEntity b = shoot(helper, player, strong, 20);
            check(Math.abs(b.getBaseDamage() - 2.5) < 1e-5, () -> "damage module arrow base damage " + b.getBaseDamage() + ", expected 2.5");
            check(Math.abs(b.getDeltaMovement().length() - 3.0) < 0.15, () -> "damage module changed speed: " + b.getDeltaMovement().length());
            long used = before - energy(strong).getOPStored();
            long expected = (long) (EquipCfg.bowBaseEnergy * 2.5 * 1.0F * 3);
            check(used == expected, () -> "damage module shot used " + used + " OP, expected " + expected);
            check(ModularBow.calculateDamage(strong) > ModularBow.calculateDamage(new ItemStack(DEContent.BOW_WYVERN.get())), () -> "tooltip damage not raised by the damage module");
        }).run();
        helper.succeed();
    }

    // ==== axe ========================================================================================================

    /**
     * Tree harvest (wyvern, speed 5/tick): right-click the bottom log and hold. 5 logs go first, then (after a 4-tick wait)
     * the 5 leaves that decayed to distance 7; energyHarvest per block; on release the collected logs drop at the player.
     */
    @GameTest(template = T_11, batch = BATCH_TOOLS, timeoutTicks = 120, required = REQUIRED)
    public static void axeTreeHarvestFellsTree(GameTestHelper helper) {
        List<BlockPos> logs = new ArrayList<>();
        for (int y = 1; y <= 5; y++) logs.add(new BlockPos(5, y, 5));
        List<BlockPos> leaves = List.of(new BlockPos(4, 5, 5), new BlockPos(6, 5, 5), new BlockPos(5, 5, 4), new BlockPos(5, 5, 6), new BlockPos(5, 6, 5));
        logs.forEach(p -> helper.setBlock(p, Blocks.OAK_LOG));
        leaves.forEach(p -> helper.setBlock(p, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1)));
        BlockPos stray = new BlockPos(1, 1, 1);   // a log of "another tree", not connected: stays
        helper.setBlock(stray, Blocks.OAK_LOG);
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(5, 1, 3), "de_tree_tester");
        ItemStack[] axe = {ItemStack.EMPTY};
        long[] before = {0};
        helper.startSequence()
                .thenExecute(safe(() -> {
                    axe[0] = tool(DEContent.AXE_WYVERN, player, true, DEModules.ITEM_WYVERN_ENERGY, DEModules.ITEM_WYVERN_TREE_HARVEST);
                    before[0] = energy(axe[0]).getOPStored();
                    player.holdMainHand(axe[0]);
                    useItemOn(helper, player, logs.get(0), Direction.NORTH);
                    check(player.isUsingItem() && player.getUseItem() == axe[0], () -> "right-click on a log did not start the tree harvest (using " + player.getUseItem() + ")");
                }))
                .thenExecuteFor(40, safe(() -> ForgeEventFactory.onItemUseTick(player, player.getUseItem(), player.getUseItemRemainingTicks())))
                .thenExecute(safe(() -> {
                    player.releaseUsingItem();
                    for (BlockPos p : logs) checkAt(helper, helper.getBlockState(p).isAir(), p, () -> "log not harvested: " + helper.getBlockState(p));
                    for (BlockPos p : leaves) checkAt(helper, helper.getBlockState(p).isAir(), p, () -> "leaf not harvested: " + helper.getBlockState(p));
                    checkAt(helper, helper.getBlockState(stray).is(Blocks.OAK_LOG), stray, () -> "unconnected log was harvested");
                    int logItems = countItems(helper, Items.OAK_LOG, new BlockPos(0, 0, 0), new BlockPos(10, 8, 10));
                    check(logItems == 5, () -> "collected " + logItems + " oak logs, expected 5");
                    // leaves at distance 7 tick randomly and may decay on their own before the harvester reaches them (rare): count only what it broke
                    long used = before[0] - energy(axe[0]).getOPStored();
                    long blocks = used / EquipCfg.energyHarvest;
                    check(used % EquipCfg.energyHarvest == 0 && blocks >= 5 + 3 && blocks <= 10, () -> "tree harvest used " + used + " OP = " + blocks + " blocks, expected 5 logs + up to 5 leaves (x " + EquipCfg.energyHarvest + ")");
                    discardAll(helper, ItemEntity.class, new BlockPos(0, 0, 0), new BlockPos(10, 8, 10));
                }))
                .thenSucceed();
    }

    /** Axe AOE (draconium AOE, radius 1): breaking one log of a flat 5x5 layer clears the 3x3 around it, nothing else. */
    @GameTest(template = T_9, batch = BATCH_TOOLS, timeoutTicks = 40, required = REQUIRED)
    public static void axeAoeMinesLogs(GameTestHelper helper) {
        BlockPos center = new BlockPos(4, 1, 4);
        List<BlockPos> layer = square(center, 2);
        List<BlockPos> area = square(center, 1);
        layer.forEach(p -> helper.setBlock(p, Blocks.OAK_PLANKS));
        area.forEach(p -> helper.setBlock(p, Blocks.OAK_LOG));
        DETestPlayer player = standOnAndLookDown(helper, center, "de_axe_aoe");
        safe(() -> {
            ItemStack axe = tool(DEContent.AXE_WYVERN, player, true, DEModules.ITEM_WYVERN_ENERGY, DEModules.ITEM_DRACONIUM_AOE);
            player.holdMainHand(axe);
            player.gameMode.destroyBlock(helper.absolutePos(center));
            List<BlockPos> ring = layer.stream().filter(p -> !area.contains(p)).toList();
            checkMined(helper, area, ring);
            int n = countItems(helper, Items.OAK_LOG, new BlockPos(0, 0, 0), new BlockPos(8, 3, 8));
            check(n == 9, () -> "collected " + n + " logs, expected 9");
            discardAll(helper, ItemEntity.class, new BlockPos(0, 0, 0), new BlockPos(8, 3, 8));
        }).run();
        helper.succeed();
    }

    // ==== shovel =====================================================================================================

    static void fillDirtSand(GameTestHelper helper, List<BlockPos> layer) {
        for (int i = 0; i < layer.size(); i++) helper.setBlock(layer.get(i), i % 2 == 0 ? Blocks.DIRT : Blocks.SAND);
    }

    /** Shovel AOE (wyvern AOE, radius 2): a 5x5 of mixed dirt and sand on the floor, ring of a 7x7 layer and the floor below stay. */
    @GameTest(template = T_9, batch = BATCH_TOOLS, timeoutTicks = 40, required = REQUIRED)
    public static void shovelAoeDigsDirtAndSand(GameTestHelper helper) {
        BlockPos center = new BlockPos(4, 2, 4);   // on top of the floor (template floor = relative y 1)
        List<BlockPos> layer = square(center, 3);
        List<BlockPos> area = square(center, 2);
        fillDirtSand(helper, layer);
        DETestPlayer player = standOnAndLookDown(helper, center, "de_shovel_aoe");
        safe(() -> {
            ItemStack shovel = tool(DEContent.SHOVEL_WYVERN, player, true, DEModules.ITEM_WYVERN_ENERGY, DEModules.ITEM_WYVERN_AOE);
            player.holdMainHand(shovel);
            long before = energy(shovel).getOPStored();
            player.gameMode.destroyBlock(helper.absolutePos(center));
            List<BlockPos> keep = new ArrayList<>(layer.stream().filter(p -> !area.contains(p)).toList());
            area.forEach(p -> keep.add(p.below()));
            checkMined(helper, area, keep);
            long used = before - energy(shovel).getOPStored();
            check(used == 25L * EquipCfg.energyHarvest, () -> "5x5 dig used " + used + " OP, expected 25 x " + EquipCfg.energyHarvest);
            int dirt = countItems(helper, Items.DIRT, new BlockPos(0, 0, 0), new BlockPos(8, 4, 8));
            int sand = countItems(helper, Items.SAND, new BlockPos(0, 0, 0), new BlockPos(8, 3, 8));
            check(dirt + sand == 25 && sand > 0, () -> "collected " + dirt + " dirt + " + sand + " sand, expected 25");
            discardAll(helper, ItemEntity.class, new BlockPos(0, 0, 0), new BlockPos(8, 3, 8));
        }).run();
        helper.succeed();
    }

    /** The configurable mining_aoe property (range 0..module aoe) limits the area: wyvern AOE set to 1 digs only 3x3; set to 0 a single block. */
    @GameTest(template = T_9, batch = BATCH_TOOLS, timeoutTicks = 40, required = REQUIRED)
    public static void shovelAoeRespectsSizeSetting(GameTestHelper helper) {
        BlockPos center = new BlockPos(2, 1, 4);
        BlockPos single = new BlockPos(6, 1, 4);
        List<BlockPos> layer = square(center, 2);
        List<BlockPos> area = square(center, 1);
        List<BlockPos> layer2 = square(single, 1);
        fillDirtSand(helper, layer);
        layer2.forEach(p -> helper.setBlock(p, Blocks.DIRT));
        safe(() -> {
            DETestPlayer player = standOnAndLookDown(helper, center, "de_shovel_setting");
            ItemStack shovel = tool(DEContent.SHOVEL_WYVERN, player, true, DEModules.ITEM_WYVERN_ENERGY, DEModules.ITEM_WYVERN_AOE);
            int set = setInt(shovel, "mining_aoe", 1);
            check(set == 1, () -> "mining_aoe set to " + set);
            int clipped = setInt(shovel, "mining_aoe", 9);
            check(clipped == 2, () -> "mining_aoe above the module's aoe was not clipped to 2: " + clipped);
            setInt(shovel, "mining_aoe", 1);
            player.holdMainHand(shovel);
            player.gameMode.destroyBlock(helper.absolutePos(center));
            checkMined(helper, area, layer.stream().filter(p -> !area.contains(p)).toList());

            DETestPlayer p2 = standOnAndLookDown(helper, single, "de_shovel_setting0");
            setInt(shovel, "mining_aoe", 0);
            p2.holdMainHand(shovel);
            p2.gameMode.destroyBlock(helper.absolutePos(single));
            checkMined(helper, List.of(single), layer2.stream().filter(p -> !p.equals(single)).toList());
            discardAll(helper, ItemEntity.class, new BlockPos(0, 0, 0), new BlockPos(8, 3, 8));
        }).run();
        helper.succeed();
    }

    // ==== AOE size ===================================================================================================

    /** Chaotic AOE module (aoe 5) at its maximum: one break on the floor layer clears 11x11 = 121 blocks, the floor below stays. */
    @GameTest(template = T_11, batch = BATCH_TOOLS, timeoutTicks = 60, required = REQUIRED)
    public static void chaoticAoeMaxIs11x11(GameTestHelper helper) {
        BlockPos center = new BlockPos(5, 2, 5);   // on top of the floor (template floor = relative y 1)
        List<BlockPos> area = square(center, 5);
        area.forEach(p -> helper.setBlock(p, Blocks.STONE));
        DETestPlayer player = standOnAndLookDown(helper, center, "de_aoe_11");
        safe(() -> {
            ItemStack pick = tool(DEContent.PICKAXE_CHAOTIC, player, true, DEModules.ITEM_CHAOTIC_ENERGY, DEModules.ITEM_CHAOTIC_AOE);
            check(host(pick).getModuleData(ModuleTypes.AOE).aoe() == 5, () -> "chaotic AOE data " + host(pick).getModuleData(ModuleTypes.AOE));
            check(((PropertyProvider) host(pick)).getInt("mining_aoe").getValue() == 5, () -> "default mining_aoe " + ((PropertyProvider) host(pick)).getInt("mining_aoe").getValue());
            player.holdMainHand(pick);
            player.gameMode.destroyBlock(helper.absolutePos(center));
            checkMined(helper, area, area.stream().map(BlockPos::below).toList());
            int cobble = countItems(helper, Items.COBBLESTONE, new BlockPos(0, 0, 0), new BlockPos(10, 4, 10));
            check(cobble == 121, () -> "collected " + cobble + " cobblestone, expected 121 (11x11)");
            discardAll(helper, ItemEntity.class, new BlockPos(0, 0, 0), new BlockPos(10, 4, 10));
        }).run();
        helper.succeed();
    }

    /** The same chaotic pickaxe with mining_aoe set to 4 clears 9x9 (81) and leaves the outer ring of the 11x11 layer. */
    @GameTest(template = T_11, batch = BATCH_TOOLS, timeoutTicks = 60, required = REQUIRED)
    public static void aoeSetting4Gives9x9(GameTestHelper helper) {
        BlockPos center = new BlockPos(5, 1, 5);
        List<BlockPos> layer = square(center, 5);
        List<BlockPos> area = square(center, 4);
        layer.forEach(p -> helper.setBlock(p, Blocks.STONE));
        DETestPlayer player = standOnAndLookDown(helper, center, "de_aoe_9");
        safe(() -> {
            ItemStack pick = tool(DEContent.PICKAXE_CHAOTIC, player, true, DEModules.ITEM_CHAOTIC_ENERGY, DEModules.ITEM_CHAOTIC_AOE);
            setInt(pick, "mining_aoe", 4);
            player.holdMainHand(pick);
            long before = energy(pick).getOPStored();
            player.gameMode.destroyBlock(helper.absolutePos(center));
            checkMined(helper, area, layer.stream().filter(p -> !area.contains(p)).toList());
            int cobble = countItems(helper, Items.COBBLESTONE, new BlockPos(0, 0, 0), new BlockPos(10, 4, 10));
            check(cobble == 81, () -> "collected " + cobble + " cobblestone, expected 81 (9x9)");
            long used = before - energy(pick).getOPStored();
            check(used == 81L * EquipCfg.energyHarvest, () -> "9x9 used " + used + " OP, expected 81 x " + EquipCfg.energyHarvest);
            discardAll(helper, ItemEntity.class, new BlockPos(0, 0, 0), new BlockPos(10, 4, 10));
        }).run();
        helper.succeed();
    }

    // ==== tools without energy =======================================================================================

    /**
     * IModularMiningTool.onBlockStartBreak returns false below energyHarvest, so the break is vanilla: only the clicked block,
     * dropped in the world; IModularItem.getDestroySpeed is 1 (bare hand). With energy the tier speed applies.
     */
    @GameTest(template = T_9, batch = BATCH_TOOLS, timeoutTicks = 40, required = REQUIRED)
    public static void miningToolWithoutEnergyBreaksSingleBlock(GameTestHelper helper) {
        BlockPos center = new BlockPos(4, 1, 4);
        List<BlockPos> layer = square(center, 1);
        layer.forEach(p -> helper.setBlock(p, Blocks.STONE));
        DETestPlayer player = standOnAndLookDown(helper, center, "de_no_energy");
        safe(() -> {
            BlockState stone = Blocks.STONE.defaultBlockState();
            ItemStack charged = tool(DEContent.PICKAXE_WYVERN, player, true, DEModules.ITEM_WYVERN_ENERGY);
            float tierSpeed = ((TieredItem) charged.getItem()).getTier().getSpeed();
            check(Math.abs(charged.getDestroySpeed(stone) - tierSpeed) < 1e-4, () -> "charged pickaxe speed " + charged.getDestroySpeed(stone) + ", expected tier speed " + tierSpeed);
            ItemStack empty = tool(DEContent.PICKAXE_WYVERN, player, false, DEModules.ITEM_WYVERN_ENERGY, DEModules.ITEM_DRACONIUM_AOE);
            check(energy(empty).getOPStored() == 0, () -> "setup: pickaxe not empty");
            check(empty.getDestroySpeed(stone) == 1F, () -> "empty pickaxe speed " + empty.getDestroySpeed(stone) + ", expected 1 (bare hand)");
            player.holdMainHand(empty);
            boolean broke = player.gameMode.destroyBlock(helper.absolutePos(center));
            check(broke, () -> "empty pickaxe could not break stone at all");
            checkMined(helper, List.of(center), layer.stream().filter(p -> !p.equals(center)).toList());
            int cobble = countItems(helper, Items.COBBLESTONE, new BlockPos(0, 0, 0), new BlockPos(8, 3, 8));
            check(cobble == 1, () -> "empty pickaxe dropped " + cobble + " cobblestone, expected 1 (vanilla break, correct tool)");
            discardAll(helper, ItemEntity.class, new BlockPos(0, 0, 0), new BlockPos(8, 3, 8));
        }).run();
        helper.succeed();
    }

    // ==== staff ======================================================================================================

    /** Staff + draconium AOE: mines any block type (stone, dirt, planks, log, wool, bricks...) in the 3x3; melee hits for its attack damage. */
    @GameTest(template = T_9, batch = BATCH_TOOLS, timeoutTicks = 40, required = REQUIRED)
    public static void staffMinesAnyBlockAoeAndMelee(GameTestHelper helper) {
        BlockPos center = new BlockPos(4, 1, 4);
        List<BlockPos> layer = square(center, 2);
        List<BlockPos> area = square(center, 1);
        Block[] mix = {Blocks.STONE, Blocks.DIRT, Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.WHITE_WOOL, Blocks.BRICKS, Blocks.COBBLESTONE, Blocks.NETHERRACK, Blocks.CLAY};
        layer.forEach(p -> helper.setBlock(p, Blocks.STONE));
        for (int i = 0; i < area.size(); i++) helper.setBlock(area.get(i), mix[i]);
        helper.setBlock(center, Blocks.STONE);
        DETestPlayer player = standOnAndLookDown(helper, center, "de_staff_tester");
        Husk husk = dummy(helper, new BlockPos(7, 1, 7));
        safe(() -> {
            ItemStack staff = tool(DEContent.STAFF_DRACONIC, player, true, DEModules.ITEM_DRACONIC_ENERGY, DEModules.ITEM_DRACONIUM_AOE, DEModules.ITEM_DRACONIC_DAMAGE);
            player.holdMainHand(staff);
            player.gameMode.destroyBlock(helper.absolutePos(center));
            checkMined(helper, area, layer.stream().filter(p -> !area.contains(p)).toList());
            int items = entitiesIn(helper, ItemEntity.class, new BlockPos(0, 0, 0), new BlockPos(8, 3, 8)).stream().mapToInt(e -> e.getItem().getCount()).sum();
            check(items >= 9, () -> "staff AOE collected only " + items + " items from 9 mixed blocks");
            discardAll(helper, ItemEntity.class, new BlockPos(0, 0, 0), new BlockPos(8, 3, 8));

            player.refreshMainHandAttributes();
            double attr = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            check(attr > 8, () -> "staff attack damage " + attr + " with a draconic damage module (+8)");
            player.chargeAttack();
            player.attack(husk);
            check(Math.abs(lost(husk) - attr) < 1e-3, () -> "staff melee dealt " + lost(husk) + ", expected " + attr);
            husk.discard();
        }).run();
        helper.succeed();
    }

    /** Staff right-click (air and block) does nothing and spawns no projectile: upstream left staff shooting unimplemented, 1.12.2 never had it. */
    @GameTest(template = T_5, batch = BATCH_TOOLS, timeoutTicks = 40, required = REQUIRED)
    public static void staffUseDoesNotShootOrCrash(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.STONE);
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(2, 1, 1), "de_staff_use");
        safe(() -> {
            ItemStack staff = tool(DEContent.STAFF_CHAOTIC, player, true, DEModules.ITEM_CHAOTIC_ENERGY);
            player.holdMainHand(staff);
            long before = energy(staff).getOPStored();
            net.minecraft.world.InteractionResult r = player.gameMode.useItem(player, helper.getLevel(), staff, InteractionHand.MAIN_HAND);
            LOGGER.info("DEPORT-GATE gametest.staff_use={} using={}", r, player.isUsingItem());
            useItemOn(helper, player, new BlockPos(2, 1, 3), Direction.NORTH);
            if (player.isUsingItem()) player.releaseUsingItem();
            int projectiles = entitiesIn(helper, Projectile.class, new BlockPos(-1, 0, -1), new BlockPos(5, 4, 5)).size();
            check(projectiles == 0, () -> "staff use spawned " + projectiles + " projectiles");
            checkAt(helper, helper.getBlockState(new BlockPos(2, 1, 3)).is(Blocks.STONE), new BlockPos(2, 1, 3), () -> "staff right-click changed the block");
            check(energy(staff).getOPStored() == before, () -> "staff use consumed energy");
        }).run();
        helper.succeed();
    }

    // ==== hoe ========================================================================================================

    /** Hoe AOE (wyvern AOE, tool_aoe 2): one right-click tills a 5x5 of dirt; tool_aoe 1 tills 3x3 only. */
    @GameTest(template = T_9, batch = BATCH_TOOLS, timeoutTicks = 40, required = REQUIRED)
    public static void hoeAoeTillsArea(GameTestHelper helper) {
        BlockPos c1 = new BlockPos(2, 1, 4);
        BlockPos c2 = new BlockPos(6, 1, 4);
        List<BlockPos> l1 = square(c1, 2);
        List<BlockPos> l2 = square(c2, 2);
        l1.forEach(p -> helper.setBlock(p, Blocks.DIRT));
        l2.forEach(p -> { if (!l1.contains(p)) helper.setBlock(p, Blocks.GRASS_BLOCK); });
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(4, 1, 8), "de_hoe_tester");
        safe(() -> {
            ItemStack hoe = tool(DEContent.HOE_WYVERN, player, true, DEModules.ITEM_WYVERN_ENERGY, DEModules.ITEM_WYVERN_AOE);
            player.holdMainHand(hoe);
            useItemOn(helper, player, c1, Direction.UP);
            for (BlockPos p : l1) checkAt(helper, helper.getBlockState(p).is(Blocks.FARMLAND), p, () -> "not tilled by the 5x5 hoe: " + helper.getBlockState(p));

            setInt(hoe, "tool_aoe", 1);
            useItemOn(helper, player, c2, Direction.UP);
            List<BlockPos> a2 = square(c2, 1);
            for (BlockPos p : l2) {
                if (l1.contains(p)) continue;
                boolean farmland = helper.getBlockState(p).is(Blocks.FARMLAND);
                checkAt(helper, farmland == a2.contains(p), p, () -> (farmland ? "tilled outside" : "not tilled inside") + " the 3x3 (tool_aoe 1)");
            }
        }).run();
        helper.succeed();
    }

    // ==== tiers ======================================================================================================

    /**
     * Each tool tier uses its own harvest speed and damage (EquipCfg *HarvestSpeed / *Damage): wyvern < draconic < chaotic.
     * The 1.20.1 donor built DRACONIC_TIER from TechLevel.DRACONIUM (upstream fixed it on 1.21 in d6a3ceae).
     */
    @GameTest(template = T_3, batch = BATCH_TOOLS, timeoutTicks = 20, required = REQUIRED)
    public static void toolTiersUseTheirOwnStats(GameTestHelper helper) {
        safe(() -> {
            Object[][] tiers = {
                    {DEContent.PICKAXE_WYVERN.get(), TechLevel.WYVERN, EquipCfg.wyvernHarvestSpeed, EquipCfg.wyvernDamage},
                    {DEContent.PICKAXE_DRACONIC.get(), TechLevel.DRACONIC, EquipCfg.draconicHarvestSpeed, EquipCfg.draconicDamage},
                    {DEContent.PICKAXE_CHAOTIC.get(), TechLevel.CHAOTIC, EquipCfg.chaoticHarvestSpeed, EquipCfg.chaoticDamage},
                    {DEContent.SWORD_DRACONIC.get(), TechLevel.DRACONIC, EquipCfg.draconicHarvestSpeed, EquipCfg.draconicDamage},
                    {DEContent.AXE_DRACONIC.get(), TechLevel.DRACONIC, EquipCfg.draconicHarvestSpeed, EquipCfg.draconicDamage},
                    {DEContent.SHOVEL_DRACONIC.get(), TechLevel.DRACONIC, EquipCfg.draconicHarvestSpeed, EquipCfg.draconicDamage},
                    {DEContent.HOE_DRACONIC.get(), TechLevel.DRACONIC, EquipCfg.draconicHarvestSpeed, EquipCfg.draconicDamage},
                    {DEContent.STAFF_DRACONIC.get(), TechLevel.DRACONIC, EquipCfg.draconicHarvestSpeed, EquipCfg.draconicDamage},
            };
            for (Object[] t : tiers) {
                TieredItem item = (TieredItem) t[0];
                DETier tier = (DETier) item.getTier();
                double speed = (double) t[2];
                double damage = (double) t[3];
                check(Math.abs(tier.getSpeed() - speed) < 1e-4 && Math.abs(tier.getAttackDamageBonus() - damage) < 1e-4,
                        () -> item + " (" + t[1] + "): tier speed " + tier.getSpeed() + " damage " + tier.getAttackDamageBonus() + ", expected " + speed + " / " + damage);
                check(tier.getLevel() == ((TechLevel) t[1]).getHarvestLevel(), () -> item + " mining level " + tier.getLevel() + ", expected " + ((TechLevel) t[1]).getHarvestLevel());
            }
            float w = ((TieredItem) DEContent.PICKAXE_WYVERN.get()).getTier().getSpeed();
            float d = ((TieredItem) DEContent.PICKAXE_DRACONIC.get()).getTier().getSpeed();
            float c = ((TieredItem) DEContent.PICKAXE_CHAOTIC.get()).getTier().getSpeed();
            check(w < d && d < c, () -> "pickaxe speeds wyvern " + w + " draconic " + d + " chaotic " + c);
        }).run();
        helper.succeed();
    }
}
