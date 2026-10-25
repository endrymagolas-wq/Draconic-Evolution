package com.brandon3055.draconicevolution.gametest;

// WP-31b - machines (PORTING_PLAN §8.3 "Машини"): Grinder, Disenchanter, Entity Detector, Draconium Chest smelting,
// Stabilized Spawner, Generator fuel types/modes; Celestial Manipulator in its own batch (it changes world time/weather).
// Expected behaviour = the 1.20.1 donor code of each tile (donors/de-1.20/.../blocks/tileentity/*), which the port keeps.
// GUI actions are sent the way the client would (receivePacketFromClient with a CCL MCDataByteBuf payload), from a
// DETestPlayer standing in the test area.

import com.brandon3055.draconicevolution.blocks.machines.Grinder;
import com.brandon3055.draconicevolution.blocks.tileentity.TileCelestialManipulator;
import com.brandon3055.draconicevolution.blocks.tileentity.TileDisenchanter;
import com.brandon3055.draconicevolution.blocks.tileentity.TileEntityDetector;
import com.brandon3055.draconicevolution.blocks.tileentity.TileGenerator;
import com.brandon3055.draconicevolution.blocks.tileentity.TileGrinder;
import com.brandon3055.draconicevolution.blocks.tileentity.TileStabilizedSpawner;
import com.brandon3055.draconicevolution.blocks.tileentity.chest.TileDraconiumChest;
import com.brandon3055.draconicevolution.init.DEContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

import static com.brandon3055.draconicevolution.gametest.DEGameTestUtil.*;

@GameTestHolder(DEGameTestUtil.MODID)
@PrefixGameTestTemplate(false)
public class MachineExtGameTests {
    static {
        DEGameTestUtil.installReporter();
    }

    /** required since WP-31b run-03: every test of this class passed in run-01..03 or after its test-bug fix (logs/WP-31b.md). */
    static final boolean REQUIRED = true;

    private static BlockHitResult hitOn(GameTestHelper helper, BlockPos rel) {
        BlockPos abs = helper.absolutePos(rel);
        return new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
    }

    private static int count(net.minecraftforge.items.IItemHandler inv, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < inv.getSlots(); i++) {
            if (inv.getStackInSlot(i).is(item)) n += inv.getStackInSlot(i).getCount();
        }
        return n;
    }

    /** Grinder (AOE 1 = the block in front) kills a cow, stores the XP orbs and pushes the drops into an adjacent chest. */
    @GameTest(template = T_5, batch = BATCH_MACHINES_EXT, timeoutTicks = 200, required = REQUIRED)
    public static void grinderKillsMobCollectsXpAndDrops(GameTestHelper helper) {
        BlockPos grinderPos = new BlockPos(2, 2, 2), chestPos = new BlockPos(3, 2, 2), mobPos = grinderPos.north();
        helper.setBlock(grinderPos, DEContent.GRINDER.get().defaultBlockState().setValue(Grinder.FACING, Direction.NORTH));
        helper.setBlock(chestPos, Blocks.CHEST);
        TileGrinder grinder = tile(helper, grinderPos, TileGrinder.class);
        Cow[] cow = {null};
        safe(() -> {
            grinder.aoe.set((byte) 1);   // ManagedByte validator clips to 1..4; listener resets the kill zone
            check(grinder.aoe.get() == 1, () -> "aoe " + grinder.aoe.get());
            grinder.opStorage.modifyEnergyStored(1_000_000L);
            cow[0] = helper.spawnWithNoFreeWill(EntityType.COW, mobPos);
        }).run();
        helper.succeedWhen(safe(() -> {
            check(!cow[0].isAlive(), () -> "cow still alive, grinder active=" + grinder.active.get() + " stored=" + grinder.opStorage.getOPStored());
            helper.assertBlockProperty(grinderPos, Grinder.ACTIVE, true);
            check(grinder.storedXP.get() > 0, () -> "no XP collected");
            ChestBlockEntity chest = tile(helper, chestPos, ChestBlockEntity.class);
            int beef = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(Items.BEEF)) beef += chest.getItem(i).getCount();
            int finalBeef = beef;
            check(finalBeef >= 1 && finalBeef <= 3, () -> "chest holds " + finalBeef + " raw beef (cow loot: 1-3)");
            check(grinder.opStorage.getOPStored() < 1_000_000L, () -> "kill cost no energy");
        }));
    }

    /** Disenchanter: the GUI request for one enchantment moves it onto a book, keeps the others and costs XP levels. */
    @GameTest(template = T_3, batch = BATCH_MACHINES_EXT, timeoutTicks = 60, required = REQUIRED)
    public static void disenchanterMovesChosenEnchantmentToBook(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, DEContent.DISENCHANTER.get());
        TileDisenchanter tile = tile(helper, pos, TileDisenchanter.class);
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(0, 2, 0), "de_disenchant_tester");
        safe(() -> {
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.enchant(Enchantments.SHARPNESS, 5);
            sword.enchant(Enchantments.UNBREAKING, 3);
            check(tile.itemHandler.insertItem(0, sword, false).isEmpty(), () -> "enchanted sword rejected by slot 0");
            check(!tile.itemHandler.insertItem(0, new ItemStack(Items.DIAMOND_SWORD), true).isEmpty(), () -> "slot 0 accepts an unenchanted item");
            check(tile.itemHandler.insertItem(1, new ItemStack(Items.BOOK, 2), false).isEmpty(), () -> "books rejected by slot 1");
            player.giveExperienceLevels(100);
            int cost = tile.getCostInLevels(Enchantments.SHARPNESS, 5);
            check(cost > 0, () -> "cost " + cost);
            tile.receivePacketFromClient(packet(b -> b.writeString("minecraft:sharpness")), player, 0);
            ItemStack book = tile.itemHandler.getStackInSlot(2);
            check(book.is(Items.ENCHANTED_BOOK), () -> "output slot holds " + book);
            Map<net.minecraft.world.item.enchantment.Enchantment, Integer> onBook = EnchantmentHelper.getEnchantments(book);
            check(onBook.size() == 1 && onBook.getOrDefault(Enchantments.SHARPNESS, 0) == 5, () -> "book enchantments " + onBook + " / " + EnchantedBookItem.getEnchantments(book));
            Map<net.minecraft.world.item.enchantment.Enchantment, Integer> left = EnchantmentHelper.getEnchantments(tile.itemHandler.getStackInSlot(0));
            check(left.size() == 1 && left.getOrDefault(Enchantments.UNBREAKING, 0) == 3, () -> "sword keeps " + left);
            check(tile.itemHandler.getStackInSlot(1).getCount() == 1, () -> "books left " + tile.itemHandler.getStackInSlot(1));
            check(player.experienceLevel == 100 - cost, () -> "levels " + player.experienceLevel + ", expected " + (100 - cost));
        }).run();
        helper.succeed();
    }

    /** Entity detector (basic, range 1): redstone 15 while a mob is in range, 0 after it leaves; each pulse costs energy. */
    @GameTest(template = T_5, batch = BATCH_MACHINES_EXT, timeoutTicks = 200, required = REQUIRED)
    public static void entityDetectorEmitsRedstoneForMobsInRange(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2), probe = pos.above(), mobPos = pos.east();
        helper.setBlock(pos, DEContent.ENTITY_DETECTOR.get());
        TileEntityDetector det = tile(helper, pos, TileEntityDetector.class);
        Cow[] cow = {null};
        long[] energy = {0};
        helper.startSequence()
                .thenExecute(safe(() -> {
                    det.range.set((short) 1);
                    det.opStorage.modifyEnergyStored(100_000L);
                    energy[0] = det.opStorage.getOPStored();
                    cow[0] = helper.spawnWithNoFreeWill(EntityType.COW, mobPos);
                }))
                .thenWaitUntil(safe(() -> check(helper.getLevel().getBestNeighborSignal(helper.absolutePos(probe)) == 15,
                        () -> "signal " + helper.getLevel().getBestNeighborSignal(helper.absolutePos(probe)) + " outputStrength=" + det.outputStrength.get())))
                .thenExecute(safe(() -> {
                    check(det.opStorage.getOPStored() == energy[0] - det.getPulseCost(), () -> "pulse cost: stored " + det.opStorage.getOPStored() + " from " + energy[0] + ", cost " + det.getPulseCost());
                    cow[0].discard();
                }))
                .thenWaitUntil(safe(() -> check(helper.getLevel().getBestNeighborSignal(helper.absolutePos(probe)) == 0, () -> "signal still " + det.outputStrength.get())))
                .thenSucceed();
    }

    /** Draconium chest furnace: raw iron in the furnace slots, energy in the chest -> iron ingots in the main inventory. */
    @GameTest(template = T_3, batch = BATCH_MACHINES_EXT, timeoutTicks = 400, required = REQUIRED)
    public static void draconiumChestSmeltsWithEnergy(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, DEContent.DRACONIUM_CHEST.get());
        TileDraconiumChest chest = tile(helper, pos, TileDraconiumChest.class);
        safe(() -> {
            check(chest.furnaceItems.insertItem(0, new ItemStack(Items.RAW_IRON, 2), false).isEmpty(), () -> "furnace slot rejected raw iron");
            check(!chest.furnaceItems.insertItem(1, new ItemStack(Items.STICK), true).isEmpty(), () -> "furnace slot accepts an unsmeltable item");
            chest.opStorage.modifyEnergyStored(1_000_000L);
        }).run();
        helper.succeedWhen(safe(() -> {
            int ingots = count(chest.mainInventory, Items.IRON_INGOT);
            check(ingots == 2, () -> "main inventory holds " + ingots + " iron ingots, furnace " + chest.furnaceItems.getStackInSlot(0) + ", progress " + chest.smeltingLogic.smeltProgress.get());
            check(chest.furnaceItems.getStackInSlot(0).isEmpty(), () -> "raw iron left " + chest.furnaceItems.getStackInSlot(0));
            check(chest.opStorage.getOPStored() < 1_000_000L, () -> "smelting used no energy");
        }));
    }

    /** Stabilized spawner: soul + awakened core (DRACONIC tier: no player, no spawn rules) -> persistent pigs after the delay. */
    @GameTest(template = T_9, batch = BATCH_MACHINES_EXT, timeoutTicks = 300, required = REQUIRED)
    public static void stabilizedSpawnerSpawnsFromMobSoul(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 2, 4);
        helper.setBlock(pos, DEContent.STABILIZED_SPAWNER.get());
        TileStabilizedSpawner spawner = tile(helper, pos, TileStabilizedSpawner.class);
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(0, 2, 0), "de_spawner_tester");
        helper.startSequence()
                .thenExecute(safe(() -> {
                    ItemStack soul = new ItemStack(DEContent.MOB_SOUL.get());
                    DEContent.MOB_SOUL.get().setEntity(new ResourceLocation("minecraft:pig"), soul);
                    player.holdMainHand(soul);
                    spawner.onBlockActivated(helper.getBlockState(pos), player, InteractionHand.MAIN_HAND, hitOn(helper, pos));
                    check(!spawner.mobSoul.get().isEmpty(), () -> "soul not accepted");
                    check(player.getMainHandItem().isEmpty(), () -> "soul not consumed from the hand: " + player.getMainHandItem());
                    player.holdMainHand(new ItemStack(DEContent.CORE_AWAKENED.get()));
                    spawner.onBlockActivated(helper.getBlockState(pos), player, InteractionHand.MAIN_HAND, hitOn(helper, pos));
                    check(spawner.spawnerTier.get() == TileStabilizedSpawner.SpawnerTier.DRACONIC, () -> "tier " + spawner.spawnerTier.get());
                    check(spawner.isActive(), () -> "spawner inactive");
                }))
                .thenWaitUntil(safe(() -> {
                    List<Pig> pigs = entitiesIn(helper, Pig.class, new BlockPos(-1, 0, -1), new BlockPos(9, 4, 9));
                    check(!pigs.isEmpty(), () -> "no pig spawned yet, delay=" + spawner.spawnDelay.get());
                    for (Pig pig : pigs) {
                        check(pig.isPersistenceRequired() && pig.getPersistentData().contains("DESpawnedMob"), () -> "spawned pig not marked persistent/DESpawnedMob");
                    }
                    check(pigs.size() <= TileStabilizedSpawner.SpawnerTier.DRACONIC.getSpawnCount(), () -> pigs.size() + " pigs from one cycle");
                }))
                .thenExecute(safe(() -> {
                    discardAll(helper, Pig.class, new BlockPos(-2, 0, -2), new BlockPos(10, 5, 10));
                    discardAll(helper, ItemEntity.class, new BlockPos(-2, 0, -2), new BlockPos(10, 5, 10));   // the replaced draconium core
                    helper.setBlock(pos, Blocks.AIR);
                }))
                .thenSucceed();
    }

    /** Generator: non-fuel rejected; stick then lava bucket (bucket left behind); PERFORMANCE_PLUS 300 OP/t, ECO_PLUS 5 OP/t. */
    @GameTest(template = T_5, batch = BATCH_MACHINES_EXT, timeoutTicks = 100, required = REQUIRED)
    public static void generatorFuelTypesAndModes(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, DEContent.GENERATOR.get());
        TileGenerator gen = tile(helper, pos, TileGenerator.class);
        helper.startSequence()
                .thenExecute(safe(() -> {
                    ItemStack cobble = new ItemStack(Blocks.COBBLESTONE);
                    check(gen.itemHandler.insertItem(0, cobble, false).getCount() == 1, () -> "generator accepted cobblestone as fuel");
                    check(gen.itemHandler.insertItem(0, new ItemStack(Items.STICK), false).isEmpty(), () -> "stick rejected");
                    check(gen.itemHandler.insertItem(1, new ItemStack(Items.LAVA_BUCKET), false).isEmpty(), () -> "lava bucket rejected");
                    gen.mode.set(TileGenerator.Mode.PERFORMANCE_PLUS);
                }))
                .thenIdle(10)
                .thenExecute(safe(() -> {
                    check(gen.itemHandler.getStackInSlot(0).isEmpty(), () -> "stick not burnt: " + gen.itemHandler.getStackInSlot(0));
                    check(gen.itemHandler.getStackInSlot(1).is(Items.BUCKET), () -> "slot 1 after the lava bucket burnt: " + gen.itemHandler.getStackInSlot(1));
                    check(gen.fuelValue.get() == 20000, () -> "last fuel value " + gen.fuelValue.get() + ", expected 20000 (lava bucket)");
                    check(gen.productionRate.get() == TileGenerator.Mode.PERFORMANCE_PLUS.powerOutput, () -> "PERFORMANCE_PLUS rate " + gen.productionRate.get());
                    check(gen.fuelRemaining.get() > 20000 - 25 * 60 && gen.fuelRemaining.get() < 20100, () -> "fuel remaining " + gen.fuelRemaining.get());
                    gen.mode.set(TileGenerator.Mode.ECO_PLUS);
                }))
                .thenIdle(2)
                .thenExecute(safe(() -> check(gen.productionRate.get() == TileGenerator.Mode.ECO_PLUS.powerOutput, () -> "ECO_PLUS rate " + gen.productionRate.get())))
                .thenSucceed();
    }

    // ---- celestial manipulator (own batch: world time and weather are global) -------------------------------------

    @AfterBatch(batch = BATCH_CELESTIAL)
    public static void afterCelestial(ServerLevel level) {
        level.setWeatherParameters(6000, 0, false, false);
    }

    @GameTest(template = T_3, batch = BATCH_CELESTIAL, timeoutTicks = 700, required = REQUIRED)
    public static void celestialManipulatorTogglesRain(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, DEContent.CELESTIAL_MANIPULATOR.get());
        TileCelestialManipulator tile = tile(helper, pos, TileCelestialManipulator.class);
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(0, 2, 0), "de_celestial_tester");
        ServerLevel level = helper.getLevel();
        long[] e = {0};
        helper.startSequence()
                .thenExecute(safe(() -> {
                    level.setWeatherParameters(6000, 0, false, false);
                    tile.opStorage.modifyEnergyStored(8_000_000L);
                    e[0] = tile.opStorage.getOPStored();
                    tile.handleInteract("START_RAIN", player);
                    check(tile.weatherToggleRunning.get() && tile.active.get(), () -> "START_RAIN did not start (stored " + tile.opStorage.getOPStored() + ")");
                    check(tile.opStorage.getOPStored() == e[0] - 256000, () -> "START_RAIN cost " + (e[0] - tile.opStorage.getOPStored()));
                }))
                .thenWaitUntil(safe(() -> check(level.getLevelData().isRaining() && !tile.weatherToggleRunning.get(), () -> "not raining yet, timer " + tile.timer)))
                // Level.isRaining() (checked by STOP_RAIN) is the rain level > 0.2, which ramps 0.01/tick after setRaining(true)
                .thenWaitUntil(safe(() -> check(level.isRaining(), () -> "rain level " + level.getRainLevel(1F))))
                .thenExecute(safe(() -> {
                    tile.handleInteract("STOP_RAIN", player);
                    check(tile.weatherToggleRunning.get(), () -> "STOP_RAIN did not start");
                }))
                .thenWaitUntil(safe(() -> check(!level.getLevelData().isRaining() && !tile.weatherToggleRunning.get(), () -> "still raining")))
                .thenSucceed();
    }

    @GameTest(template = T_3, batch = BATCH_CELESTIAL, timeoutTicks = 500, required = REQUIRED)
    public static void celestialManipulatorWarpsTimeToMidday(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, DEContent.CELESTIAL_MANIPULATOR.get());
        TileCelestialManipulator tile = tile(helper, pos, TileCelestialManipulator.class);
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(0, 2, 0), "de_time_tester");
        ServerLevel level = helper.getLevel();
        long[] start = {0};
        helper.startSequence()
                .thenExecute(safe(() -> {
                    long day = level.getDayTime() - (level.getDayTime() % 24000);
                    level.setDayTime(day + 1000);
                    start[0] = level.getDayTime();
                    tile.opStorage.modifyEnergyStored(8_000_000L);
                    tile.handleInteract("MID_DAY", player);
                    check(tile.timeWarpRunning.get() && tile.targetTime == day + 5900, () -> "MID_DAY target " + tile.targetTime + " running=" + tile.timeWarpRunning.get());
                }))
                .thenWaitUntil(safe(() -> check(!tile.timeWarpRunning.get(), () -> "warp still running, time " + level.getDayTime() % 24000)))
                .thenExecute(safe(() -> {
                    long t = level.getDayTime() % 24000;
                    check(t >= 5900 && t < 6100, () -> "time of day after the warp " + t + ", expected ~5900");
                    long used = 8_000_000L - tile.opStorage.getOPStored();
                    check(used > 0 && used % 320 == 0, () -> "warp used " + used + " OP (320 OP per tick of time)");
                }))
                .thenSucceed();
    }
}
