package com.brandon3055.draconicevolution.gametest;

// WP-31b - fusion crafting across tiers (PORTING_PLAN §8.3 "Fusion"). Same layout as G9 (FusionGameTests): core K at
// (4,2,4) of platform_9x4x9, 8 injectors at Chebyshev distance 3 facing K, a creative OP capacitor behind each one.
// Recipes (donors/de-1.20/src/main/generated/data/draconicevolution/recipes, identical in the port):
//   awakened_draconium_block   WYVERN    50 000 000 OP, catalyst ingredient_stack 4x #forge:storage_blocks/draconium -> 4 blocks
//   tools/draconic_pickaxe     DRACONIC  32 000 000 OP, catalyst wyvern_pickaxe (modules + energy carried over, IFusionDataTransfer)
//   tools/chaotic_sword        CHAOTIC  128 000 000 OP, catalyst draconic_sword
//   machines/wyvern_crafting_injector DRACONIUM 32 000 OP (energy-starved run: no capacitors)
// Tier rule (IFusionRecipe.canStartCraft): every non-empty injector's tier index >= recipe tier index, else "tier_low".
// Times (DEConfig.fusionChargeTime/fusionCraftTime = 300/220/140/60 per injector tier index 0..3).

import com.brandon3055.draconicevolution.api.crafting.IFusionStateMachine.FusionState;
import com.brandon3055.draconicevolution.api.modules.ModuleTypes;
import com.brandon3055.draconicevolution.blocks.machines.CraftingInjector;
import com.brandon3055.draconicevolution.blocks.tileentity.TileFusionCraftingCore;
import com.brandon3055.draconicevolution.blocks.tileentity.TileFusionCraftingInjector;
import com.brandon3055.draconicevolution.init.DEContent;
import com.brandon3055.draconicevolution.init.DEModules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.brandon3055.draconicevolution.gametest.DEGameTestUtil.*;

@GameTestHolder(DEGameTestUtil.MODID)
@PrefixGameTestTemplate(false)
public class FusionTierGameTests {
    static {
        DEGameTestUtil.installReporter();
    }

    static final boolean REQUIRED = false;
    static final BlockPos CORE = new BlockPos(4, 2, 4);

    static Map<BlockPos, Direction> injectorLayout() {
        Map<BlockPos, Direction> m = new LinkedHashMap<>();
        m.put(new BlockPos(7, 2, 3), Direction.WEST);
        m.put(new BlockPos(7, 2, 5), Direction.WEST);
        m.put(new BlockPos(1, 2, 3), Direction.EAST);
        m.put(new BlockPos(1, 2, 5), Direction.EAST);
        m.put(new BlockPos(3, 2, 7), Direction.NORTH);
        m.put(new BlockPos(5, 2, 7), Direction.NORTH);
        m.put(new BlockPos(3, 2, 1), Direction.SOUTH);
        m.put(new BlockPos(5, 2, 1), Direction.SOUTH);
        return m;
    }

    /** Places the core and 8 injectors of the given block (optionally with capacitors) and loads the ingredients (null = empty injector). */
    static List<TileFusionCraftingInjector> build(GameTestHelper helper, Supplier<? extends Block> injector, boolean powered, List<ItemStack> ingredients) {
        if (helper.getBlockEntity(CORE) == null) helper.setBlock(CORE, DEContent.CRAFTING_CORE.get());
        List<TileFusionCraftingInjector> list = new ArrayList<>();
        int i = 0;
        for (Map.Entry<BlockPos, Direction> e : injectorLayout().entrySet()) {
            helper.setBlock(e.getKey(), injector.get().defaultBlockState().setValue(CraftingInjector.FACING, e.getValue()));
            if (powered) helper.setBlock(e.getKey().relative(e.getValue().getOpposite()), DEContent.CREATIVE_OP_CAPACITOR.get());
            TileFusionCraftingInjector tile = tile(helper, e.getKey(), TileFusionCraftingInjector.class);
            ItemStack stack = i < ingredients.size() && ingredients.get(i) != null ? ingredients.get(i).copy() : ItemStack.EMPTY;
            tile.setInjectorStack(stack);
            list.add(tile);
            i++;
        }
        return list;
    }

    /**
     * Each fusion test runs in its own batch and removes its blocks when it passes: TileFusionCraftingCore.updateInjectors
     * scans 16 blocks along three 3-wide crosses and calls setCore(null) on every injector it finds facing it but
     * obstructed, which cancels that injector's own craft (setCore -> oldCore.cancelCraft()). Fusion setups of
     * concurrent tests 13 blocks apart (run-02) cancelled each other; that is DE behaviour (same in 1.20.1), not a port bug.
     */
    static void teardown(GameTestHelper helper) {
        if (helper.getBlockEntity(CORE) instanceof TileFusionCraftingCore core) {
            core.itemHandler.setStackInSlot(0, ItemStack.EMPTY);
            core.itemHandler.setStackInSlot(1, ItemStack.EMPTY);
        }
        for (Map.Entry<BlockPos, Direction> e : injectorLayout().entrySet()) {
            if (helper.getBlockEntity(e.getKey()) instanceof TileFusionCraftingInjector t) t.setInjectorStack(ItemStack.EMPTY);
            helper.setBlock(e.getKey(), net.minecraft.world.level.block.Blocks.AIR);
            helper.setBlock(e.getKey().relative(e.getValue().getOpposite()), net.minecraft.world.level.block.Blocks.AIR);
        }
        helper.setBlock(CORE, net.minecraft.world.level.block.Blocks.AIR);
    }

    static List<ItemStack> stacks(Object... items) {
        List<ItemStack> list = new ArrayList<>();
        for (Object o : items) {
            if (o instanceof ItemStack s) list.add(s);
            else if (o instanceof Supplier<?> s) list.add(new ItemStack((net.minecraft.world.level.ItemLike) s.get()));
            else if (o instanceof net.minecraft.world.level.ItemLike l) list.add(new ItemStack(l));
            else list.add(null);
        }
        return list;
    }

    static String status(TileFusionCraftingCore core) {
        Component c = core.userStatus.get();
        if (c == null) return "null";
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    /** Waits for a complete craft and checks the output, catalyst and injector state. */
    static void awaitCompletion(GameTestHelper helper, TileFusionCraftingCore core, List<TileFusionCraftingInjector> injectors, java.util.function.Consumer<ItemStack> outputCheck, boolean[] sawCharging) {
        String[] last = {""};
        helper.onEachTick(() -> {
            if (core.isCrafting() && core.getFusionState() == FusionState.CHARGING) sawCharging[0] = true;
            String now = core.isCrafting() + "/" + core.getFusionState() + "/" + status(core);
            if (!now.equals(last[0])) {
                LOGGER.info("DEPORT-GATE fusion.trace pos={} tick={} crafting/state/status={} charge={} output={} catalyst={}", core.getBlockPos(), helper.getTick(), now,
                        injectors.stream().mapToLong(TileFusionCraftingInjector::getInjectorEnergy).sum(), core.getOutputStack(), core.getCatalystStack());
                last[0] = now;
            }
        });
        helper.succeedWhen(safe(() -> {
            check(!core.isCrafting(), () -> "still crafting, state=" + core.getFusionState() + " progress=" + core.progress.get());
            ItemStack out = core.getOutputStack();
            check(!out.isEmpty(), () -> "craft ended without output: status=" + status(core) + " catalyst=" + core.getCatalystStack()
                    + " injectors=" + injectors.stream().map(t -> t.getInjectorStack().toString()).toList());
            outputCheck.accept(out);
            check(core.getCatalystStack().isEmpty(), () -> "catalyst left: " + core.getCatalystStack());
            for (TileFusionCraftingInjector t : injectors) {
                check(t.getInjectorStack().isEmpty(), () -> "injector at " + t.getBlockPos() + " still holds " + t.getInjectorStack());
            }
            check(sawCharging[0], () -> "CHARGING state never observed");
            teardown(helper);
        }));
    }

    /** WYVERN recipe with an IngredientStack catalyst (4 draconium blocks) and 7 of 8 injectors loaded: 4 awakened blocks out. */
    @GameTest(template = T_9, batch = BATCH_FUSION + "_a", timeoutTicks = 600, required = REQUIRED)
    public static void fusionAwakenedBlocksFromCatalystStack(GameTestHelper helper) {
        List<TileFusionCraftingInjector> inj = build(helper, DEContent.CHAOTIC_CRAFTING_INJECTOR, true, stacks(
                DEContent.CORE_DRACONIUM, DEContent.CORE_DRACONIUM, DEContent.CORE_DRACONIUM, DEContent.DRAGON_HEART,
                DEContent.CORE_DRACONIUM, DEContent.CORE_DRACONIUM, DEContent.CORE_DRACONIUM, null));
        TileFusionCraftingCore core = tile(helper, CORE, TileFusionCraftingCore.class);
        core.itemHandler.setStackInSlot(0, new ItemStack(DEContent.DRACONIUM_BLOCK.get(), 4));
        safe(() -> {
            core.startCraft();
            check(core.isCrafting(), () -> "did not start: status=" + status(core) + " injectors=" + core.getInjectors().size());
        }).run();
        awaitCompletion(helper, core, inj, out -> check(out.is(DEContent.ITEM_AWAKENED_DRACONIUM_BLOCK.get()) && out.getCount() == 4, () -> "output " + out), new boolean[1]);
    }

    /**
     * DRACONIC recipe (draconic_pickaxe): WYVERN injectors are rejected with "tier_low"; AWAKENED (DRACONIC) injectors craft it,
     * and the catalyst's modules and energy move to the result (IFusionDataTransfer.transferIngredientData).
     */
    @GameTest(template = T_9, batch = BATCH_FUSION + "_b", timeoutTicks = 900, required = REQUIRED)
    public static void fusionDraconicTierNeedsDraconicInjectors(GameTestHelper helper) {
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(0, 2, 0), "de_fusion_tester");
        List<ItemStack> ingredients = stacks(
                Items.NETHERITE_INGOT, Items.NETHERITE_INGOT, Items.NETHERITE_INGOT, Items.NETHERITE_INGOT,
                DEContent.CORE_WYVERN, DEContent.INGOT_DRACONIUM_AWAKENED, DEContent.INGOT_DRACONIUM_AWAKENED, DEContent.ENERGY_CORE_DRACONIC);
        ItemStack catalyst = new ItemStack(DEContent.PICKAXE_WYVERN.get());
        long[] catalystEnergy = {0};
        List<List<TileFusionCraftingInjector>> current = new ArrayList<>();
        helper.startSequence()
                .thenExecute(safe(() -> {
                    ModularTestUtil.install(catalyst, player, DEModules.ITEM_WYVERN_ENERGY);   // tools have no base energy (EquipCfg)
                    ModularTestUtil.install(catalyst, player, DEModules.ITEM_DRACONIUM_AOE);
                    catalystEnergy[0] = ModularTestUtil.energy(catalyst).receiveOP(100_000L, false);
                    check(catalystEnergy[0] > 0, () -> "wyvern pickaxe accepted no energy");
                    List<TileFusionCraftingInjector> low = build(helper, DEContent.WYVERN_CRAFTING_INJECTOR, true, ingredients);
                    TileFusionCraftingCore core = tile(helper, CORE, TileFusionCraftingCore.class);
                    core.itemHandler.setStackInSlot(0, catalyst);
                    core.startCraft();
                    check(!core.isCrafting(), () -> "DRACONIC recipe started with WYVERN injectors");
                    check(status(core).equals("fusion_status.draconicevolution.tier_low"), () -> "status with WYVERN injectors: " + status(core));
                    // swap in AWAKENED (DRACONIC tier) injectors: empty the old ones first so nothing drops
                    low.forEach(t -> t.setInjectorStack(ItemStack.EMPTY));
                    current.add(build(helper, DEContent.AWAKENED_CRAFTING_INJECTOR, true, ingredients));
                    core.startCraft();
                    check(core.isCrafting(), () -> "did not start with AWAKENED injectors: status=" + status(core));
                }))
                .thenWaitUntil(safe(() -> {
                    TileFusionCraftingCore core = tile(helper, CORE, TileFusionCraftingCore.class);
                    check(!core.isCrafting(), () -> "still crafting, state=" + core.getFusionState() + " progress=" + core.progress.get());
                }))
                .thenExecute(safe(() -> {
                    TileFusionCraftingCore core = tile(helper, CORE, TileFusionCraftingCore.class);
                    ItemStack out = core.getOutputStack();
                    check(out.is(DEContent.PICKAXE_DRACONIC.get()) && out.getCount() == 1, () -> "output " + out);
                    check(core.getCatalystStack().isEmpty(), () -> "catalyst left: " + core.getCatalystStack());
                    current.get(0).forEach(t -> check(t.getInjectorStack().isEmpty(), () -> "injector still holds " + t.getInjectorStack()));
                    check(ModularTestUtil.host(out).getEntitiesByType(ModuleTypes.AOE).count() == 1 && ModularTestUtil.host(out).getEntitiesByType(ModuleTypes.ENERGY_STORAGE).count() == 1,
                            () -> "modules not transferred: " + ModularTestUtil.host(out).getModuleEntities());
                    long e = ModularTestUtil.energy(out).getOPStored();
                    check(e == catalystEnergy[0], () -> "energy carried over " + e + ", expected " + catalystEnergy[0]);
                    teardown(helper);
                }))
                .thenSucceed();
    }

    /** CHAOTIC recipe (chaotic_sword) with chaotic injectors. */
    @GameTest(template = T_9, batch = BATCH_FUSION + "_c", timeoutTicks = 600, required = REQUIRED)
    public static void fusionChaoticSword(GameTestHelper helper) {
        List<TileFusionCraftingInjector> inj = build(helper, DEContent.CHAOTIC_CRAFTING_INJECTOR, true, stacks(
                DEContent.INGOT_DRACONIUM_AWAKENED, DEContent.CORE_CHAOTIC, DEContent.INGOT_DRACONIUM_AWAKENED, DEContent.INGOT_DRACONIUM_AWAKENED,
                DEContent.INGOT_DRACONIUM_AWAKENED, DEContent.INGOT_DRACONIUM_AWAKENED, DEContent.ENERGY_CORE_CHAOTIC, DEContent.INGOT_DRACONIUM_AWAKENED));
        TileFusionCraftingCore core = tile(helper, CORE, TileFusionCraftingCore.class);
        core.itemHandler.setStackInSlot(0, new ItemStack(DEContent.SWORD_DRACONIC.get()));
        safe(() -> {
            core.startCraft();
            check(core.isCrafting(), () -> "did not start: status=" + status(core));
        }).run();
        awaitCompletion(helper, core, inj, out -> check(out.is(DEContent.SWORD_CHAOTIC.get()) && out.getCount() == 1, () -> "output " + out), new boolean[1]);
    }

    /** No power: the craft starts, stays in CHARGING, never completes, and cancelling it (catalyst removed) consumes nothing. */
    @GameTest(template = T_9, batch = BATCH_FUSION + "_d", timeoutTicks = 400, required = REQUIRED)
    public static void fusionEnergyStarvedNeverCompletes(GameTestHelper helper) {
        List<ItemStack> ingredients = stacks(DEContent.CORE_WYVERN, DEContent.CORE_DRACONIUM, DEContent.CORE_DRACONIUM,
                Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, DEContent.DRACONIUM_BLOCK);
        List<TileFusionCraftingInjector> inj = build(helper, DEContent.CHAOTIC_CRAFTING_INJECTOR, false, ingredients);
        TileFusionCraftingCore core = tile(helper, CORE, TileFusionCraftingCore.class);
        core.itemHandler.setStackInSlot(0, new ItemStack(DEContent.BASIC_CRAFTING_INJECTOR.get()));
        helper.startSequence()
                .thenExecute(safe(() -> {
                    core.startCraft();
                    check(core.isCrafting(), () -> "did not start: status=" + status(core));
                }))
                .thenIdle(250)   // > 2x the powered chaotic-tier duration (60 + 60 ticks)
                .thenExecute(safe(() -> {
                    check(core.isCrafting() && core.getFusionState() == FusionState.CHARGING, () -> "starved craft state " + core.getFusionState() + " crafting=" + core.isCrafting());
                    check(core.getOutputStack().isEmpty(), () -> "starved craft produced " + core.getOutputStack());
                    long charge = inj.stream().mapToLong(TileFusionCraftingInjector::getInjectorEnergy).sum();
                    check(charge == 0, () -> "injectors charged " + charge + " OP without a power source");
                    core.itemHandler.setStackInSlot(0, ItemStack.EMPTY);   // inventoryChanged -> recipe no longer matches -> cancelCraft
                }))
                .thenIdle(1)
                .thenExecute(safe(() -> {
                    check(!core.isCrafting(), () -> "craft not cancelled after removing the catalyst");
                    check(status(core).equals("fusion_status.draconicevolution.canceled") || status(core).equals("fusion_status.draconicevolution.no_recipe"), () -> "status after cancel: " + status(core));
                    for (int i = 0; i < inj.size(); i++) {
                        ItemStack expected = ingredients.get(i);
                        ItemStack actual = inj.get(i).getInjectorStack();
                        check(actual.sameItem(expected) && actual.getCount() == expected.getCount(), () -> "ingredient consumed: " + actual + " (expected " + expected + ")");
                    }
                    teardown(helper);
                }))
                .thenSucceed();
    }
}
