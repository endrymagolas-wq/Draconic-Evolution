package com.brandon3055.draconicevolution.gametest;

// WP-31b - energy network GameTests (PORTING_PLAN §8.3 "Енергія": crystals relay/IO/wireless + Crystal Binder, flux and
// fluid gates, core T3/T4, pylons). Server-side only; a DETestPlayer performs the clicks through the real item/event
// paths (BlockItem.useOn -> ItemBlockBCore.placeBlock -> ITilePlaceListener.onTilePlaced; ServerPlayerGameMode.useItemOn ->
// PlayerInteractEvent.RightClickBlock -> DEEventHandler.rightClickBlock -> BinderHandler.onBinderUse).
// Expected values are read from the 1.20.1 donor code, which these classes keep unchanged:
//   donors/de-1.20/.../blocks/energynet/tileentity/TileCrystalBase.java (capacity 4M basic, links, balanceTransfer),
//   TileCrystalWirelessIO.java (getMaxWirelessTransfer 32000 basic), flowgate/TileFluxGate.java + TileFlowGate.getFlow(),
//   flowgate/TileFluidGate.java, TileEnergyPylon.java, TileEnergyCore.java, DEConfig.coreCapacity.

import com.brandon3055.brandonscore.api.power.IOPStorage;
import com.brandon3055.brandonscore.multiblock.MultiBlockManager;
import com.brandon3055.draconicevolution.DEConfig;
import com.brandon3055.draconicevolution.blocks.energynet.tileentity.TileCrystalBase;
import com.brandon3055.draconicevolution.blocks.energynet.tileentity.TileCrystalDirectIO;
import com.brandon3055.draconicevolution.blocks.energynet.tileentity.TileCrystalRelay;
import com.brandon3055.draconicevolution.blocks.energynet.tileentity.TileCrystalWirelessIO;
import com.brandon3055.draconicevolution.blocks.machines.EnergyPylon;
import com.brandon3055.draconicevolution.blocks.machines.FlowGate;
import com.brandon3055.draconicevolution.blocks.tileentity.TileCelestialManipulator;
import com.brandon3055.draconicevolution.blocks.tileentity.TileEnergyCore;
import com.brandon3055.draconicevolution.blocks.tileentity.TileEnergyPylon;
import com.brandon3055.draconicevolution.blocks.tileentity.flowgate.TileFluidGate;
import com.brandon3055.draconicevolution.blocks.tileentity.flowgate.TileFluxGate;
import com.brandon3055.draconicevolution.init.DEContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.brandon3055.draconicevolution.gametest.DEGameTestUtil.*;

@GameTestHolder(DEGameTestUtil.MODID)
@PrefixGameTestTemplate(false)
public class EnergyNetGameTests {
    static {
        DEGameTestUtil.installReporter();
    }

    /** required since WP-31b run-03: every test of this class passed in run-01..03 or after its test-bug fix (logs/WP-31b.md). */
    static final boolean REQUIRED = true;
    static final long BASIC_CRYSTAL_CAPACITY = 4_000_000L;   // TileCrystalBase.getCapacityForTier(0)
    static final int BASIC_WIRELESS_RATE = 32_000;           // TileCrystalWirelessIO.getMaxWirelessTransfer() tier 0

    private static TileCelestialManipulator consumer(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, DEContent.CELESTIAL_MANIPULATOR.get());
        return tile(helper, pos, TileCelestialManipulator.class);
    }

    private static void bind(GameTestHelper helper, DETestPlayer player, BlockPos from, BlockPos to, Direction face) {
        player.setShiftKeyDown(true);
        useItemOn(helper, player, from, Direction.UP);   // BinderHandler: sneak + ICrystalLink -> position saved to the tool
        player.setShiftKeyDown(false);
        useItemOn(helper, player, to, face);             // bound ICrystalLink#binderUsed(player, target, side)
    }

    /**
     * Creative capacitor -> basic IO crystal (input) -> basic relay -> basic IO crystal (output) -> celestial manipulator.
     * Placement and both links are made with the real items; links, capacities, energy arrival and unlinking are asserted.
     */
    @GameTest(template = T_9, batch = BATCH_ENERGY, timeoutTicks = 300, required = REQUIRED)
    public static void crystalRelayNetworkTransfersEnergy(GameTestHelper helper) {
        BlockPos cap = new BlockPos(1, 2, 1), ioIn = cap.above(), relay = new BlockPos(4, 2, 4), sink = new BlockPos(7, 2, 7), ioOut = sink.above();
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(4, 2, 2), "de_crystal_tester");
        TileCelestialManipulator manip = consumer(helper, sink);
        helper.startSequence()
                .thenExecute(safe(() -> {
                    helper.setBlock(cap, DEContent.CREATIVE_OP_CAPACITOR.get());
                    placeWithItem(helper, player, new ItemStack(DEContent.ITEM_BASIC_IO_CRYSTAL.get()), cap, Direction.UP);
                    placeWithItem(helper, player, new ItemStack(DEContent.ITEM_BASIC_RELAY_CRYSTAL.get()), relay.below(), Direction.UP);
                    placeWithItem(helper, player, new ItemStack(DEContent.ITEM_BASIC_IO_CRYSTAL.get()), sink, Direction.UP);
                    TileCrystalDirectIO a = tile(helper, ioIn, TileCrystalDirectIO.class);
                    TileCrystalRelay r = tile(helper, relay, TileCrystalRelay.class);
                    TileCrystalDirectIO b = tile(helper, ioOut, TileCrystalDirectIO.class);
                    check(a.facing.get() == Direction.DOWN && b.facing.get() == Direction.DOWN, () -> "IO crystal facing " + a.facing.get() + "/" + b.facing.get() + ", expected DOWN (clicked face UP)");
                    for (TileCrystalBase c : List.of(a, r, b)) {
                        check(c.getMaxEnergyStored() == BASIC_CRYSTAL_CAPACITY, () -> c.getClass().getSimpleName() + " capacity " + c.getMaxEnergyStored() + " (onTilePlaced not applied?)");
                    }
                    // shift + right-click toggles the output mode (TileCrystalDirectIO.onBlockActivated)
                    player.setShiftKeyDown(true);
                    player.holdMainHand(ItemStack.EMPTY);
                    b.onBlockActivated(helper.getBlockState(ioOut), player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(ioOut)), Direction.UP, helper.absolutePos(ioOut), false));
                    player.setShiftKeyDown(false);
                    check(b.outputMode.get() && !a.outputMode.get(), () -> "IO modes: in.output=" + a.outputMode.get() + " out.output=" + b.outputMode.get());

                    player.holdMainHand(new ItemStack(DEContent.CRYSTAL_BINDER.get()));
                    bind(helper, player, ioIn, relay, Direction.UP);
                    bind(helper, player, relay, ioOut, Direction.UP);
                    BlockPos aAbs = helper.absolutePos(ioIn), rAbs = helper.absolutePos(relay), bAbs = helper.absolutePos(ioOut);
                    check(a.getLinks().equals(List.of(rAbs)), () -> "IO-in links " + a.getLinks());
                    check(r.getLinks().size() == 2 && r.getLinks().contains(aAbs) && r.getLinks().contains(bAbs), () -> "relay links " + r.getLinks());
                    check(b.getLinks().equals(List.of(rAbs)), () -> "IO-out links " + b.getLinks());
                    check(r.maxLinks() == 8 && a.maxLinks() == 2, () -> "max links relay=" + r.maxLinks() + " io=" + a.maxLinks());
                }))
                .thenWaitUntil(safe(() -> check(manip.opStorage.getOPStored() >= 1_000_000L, () -> "manipulator holds " + manip.opStorage.getOPStored() + " OP")))
                .thenExecute(safe(() -> {
                    TileCrystalDirectIO a = tile(helper, ioIn, TileCrystalDirectIO.class);
                    TileCrystalRelay r = tile(helper, relay, TileCrystalRelay.class);
                    check(r.getEnergyStored() > 0, () -> "relay never carried energy");
                    bind(helper, player, ioIn, relay, Direction.UP);   // binding an existing link again breaks it
                    check(a.getLinks().isEmpty() && r.getLinks().size() == 1, () -> "after unlink: io-in " + a.getLinks() + " relay " + r.getLinks());
                }))
                .thenSucceed();
    }

    /** Creative capacitor -> IO crystal -> wireless crystal --(binder, wireless receiver)--> celestial manipulator, 32000 OP/t cap. */
    @GameTest(template = T_9, batch = BATCH_ENERGY, timeoutTicks = 300, required = REQUIRED)
    public static void wirelessCrystalPowersBoundReceiver(GameTestHelper helper) {
        BlockPos cap = new BlockPos(1, 2, 1), ioIn = cap.above(), wireless = new BlockPos(4, 2, 4), sink = new BlockPos(7, 2, 7);
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(4, 2, 2), "de_wireless_tester");
        TileCelestialManipulator manip = consumer(helper, sink);
        long[] last = {-1};
        helper.startSequence()
                .thenExecute(safe(() -> {
                    helper.setBlock(cap, DEContent.CREATIVE_OP_CAPACITOR.get());
                    placeWithItem(helper, player, new ItemStack(DEContent.ITEM_BASIC_IO_CRYSTAL.get()), cap, Direction.UP);
                    placeWithItem(helper, player, new ItemStack(DEContent.ITEM_BASIC_WIRELESS_CRYSTAL.get()), wireless.below(), Direction.UP);
                    TileCrystalWirelessIO w = tile(helper, wireless, TileCrystalWirelessIO.class);
                    check(!w.inputMode.get(), () -> "wireless crystal starts in input mode");
                    player.holdMainHand(new ItemStack(DEContent.CRYSTAL_BINDER.get()));
                    bind(helper, player, ioIn, wireless, Direction.UP);              // crystal <-> crystal link
                    bind(helper, player, wireless, sink, Direction.UP);              // wireless -> receiver (non-crystal tile)
                    check(w.getLinks().equals(List.of(helper.absolutePos(ioIn))), () -> "wireless crystal links " + w.getLinks());
                    check(w.getReceivers().equals(List.of(helper.absolutePos(sink))), () -> "wireless receivers " + w.getReceivers());
                }))
                .thenWaitUntil(safe(() -> check(manip.opStorage.getOPStored() > 0, () -> "receiver got nothing")))
                .thenExecute(safe(() -> last[0] = manip.opStorage.getOPStored()))
                .thenIdle(1)
                .thenExecute(safe(() -> {
                    long delta = manip.opStorage.getOPStored() - last[0];
                    check(delta > 0 && delta <= BASIC_WIRELESS_RATE, () -> "wireless delivered " + delta + " OP in one tick, expected 1.." + BASIC_WIRELESS_RATE);
                }))
                .thenSucceed();
    }

    /** Flux gate: min flow without redstone, max flow with a full signal (TileFlowGate.getFlow), measured per tick at the sink. */
    @GameTest(template = T_5, batch = BATCH_ENERGY, timeoutTicks = 100, required = REQUIRED)
    public static void fluxGateRegulatesFlowByRedstone(GameTestHelper helper) {
        BlockPos cap = new BlockPos(1, 2, 2), gatePos = new BlockPos(2, 2, 2), sink = new BlockPos(3, 2, 2);
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(2, 2, 0), "de_flux_tester");
        TileCelestialManipulator manip = consumer(helper, sink);
        helper.setBlock(gatePos, DEContent.FLUX_GATE.get().defaultBlockState().setValue(FlowGate.FACING, Direction.EAST));
        TileFluxGate gate = tile(helper, gatePos, TileFluxGate.class);
        long[] s = {0};
        helper.startSequence()
                .thenExecute(safe(() -> {
                    gate.receivePacketFromClient(packet(b -> b.writeString("1000")), player, 0);   // GUI "min" field
                    gate.receivePacketFromClient(packet(b -> b.writeString("5000")), player, 1);   // GUI "max" field
                    check(gate.minFlow.get() == 1000 && gate.maxFlow.get() == 5000, () -> "flow limits " + gate.minFlow.get() + "/" + gate.maxFlow.get());
                    helper.setBlock(cap, DEContent.CREATIVE_OP_CAPACITOR.get());
                }))
                .thenIdle(3)
                .thenExecute(safe(() -> s[0] = manip.opStorage.getOPStored()))
                .thenIdle(1)
                .thenExecute(safe(() -> {
                    long d = manip.opStorage.getOPStored() - s[0];
                    check(d == 1000, () -> "no redstone: " + d + " OP/t through the gate, expected min flow 1000 (flow=" + gate.getFlow() + ")");
                    helper.setBlock(gatePos.above(), Blocks.REDSTONE_BLOCK);
                }))
                .thenIdle(2)
                .thenExecute(safe(() -> {
                    check(gate.rsSignal.get() == 15, () -> "gate rsSignal " + gate.rsSignal.get());
                    s[0] = manip.opStorage.getOPStored();
                }))
                .thenIdle(1)
                .thenExecute(safe(() -> {
                    long d = manip.opStorage.getOPStored() - s[0];
                    check(d == 5000, () -> "signal 15: " + d + " OP/t, expected max flow 5000");
                }))
                .thenSucceed();
    }

    /** Fluid gate between two test tanks (barrels with a GameTestFluidTanks capability): fill and drain are capped by the flow. */
    @GameTest(template = T_5, batch = BATCH_ENERGY, timeoutTicks = 100, required = REQUIRED)
    public static void fluidGateRegulatesFlowByRedstone(GameTestHelper helper) {
        BlockPos srcPos = new BlockPos(1, 2, 2), gatePos = new BlockPos(2, 2, 2), dstPos = new BlockPos(3, 2, 2);
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(2, 2, 0), "de_fluid_tester");
        helper.setBlock(srcPos, Blocks.BARREL);
        helper.setBlock(dstPos, Blocks.BARREL);
        helper.setBlock(gatePos, DEContent.FLUID_GATE.get().defaultBlockState().setValue(FlowGate.FACING, Direction.EAST));
        helper.startSequence()
                .thenExecute(safe(() -> {
                    TileFluidGate gate = tile(helper, gatePos, TileFluidGate.class);
                    IFluidHandler src = GameTestFluidTanks.tank(helper, srcPos);
                    IFluidHandler dst = GameTestFluidTanks.tank(helper, dstPos);
                    gate.receivePacketFromClient(packet(b -> b.writeString("250")), player, 0);
                    gate.receivePacketFromClient(packet(b -> b.writeString("1000")), player, 1);
                    IFluidHandler in = gate.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.WEST).orElse(null);
                    IFluidHandler out = gate.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.EAST).orElse(null);
                    check(in != null && out != null, () -> "fluid gate exposes no handler on its axis");
                    check(gate.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).resolve().isEmpty(), () -> "fluid gate exposes a handler off-axis");

                    int filled = in.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
                    check(filled == 250 && dst.getFluidInTank(0).getAmount() == 250, () -> "input side passed " + filled + " mB, target holds " + dst.getFluidInTank(0).getAmount());
                    src.fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE);
                    FluidStack drained = out.drain(1000, IFluidHandler.FluidAction.EXECUTE);
                    check(drained.getAmount() == 250 && src.getFluidInTank(0).getAmount() == 1750, () -> "output side drained " + drained.getAmount() + " mB, source left " + src.getFluidInTank(0).getAmount());
                    helper.setBlock(gatePos.above(), Blocks.REDSTONE_BLOCK);
                }))
                .thenIdle(1)
                .thenExecute(safe(() -> {
                    TileFluidGate gate = tile(helper, gatePos, TileFluidGate.class);
                    IFluidHandler in = gate.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.WEST).orElseThrow(IllegalStateException::new);
                    int filled = in.fill(new FluidStack(Fluids.WATER, 3000), IFluidHandler.FluidAction.EXECUTE);
                    check(filled == 1000, () -> "signal 15: input side passed " + filled + " mB, expected max flow 1000 (rs=" + gate.rsSignal.get() + ")");
                }))
                .thenSucceed();
    }

    // ---- energy core T3/T4 ----------------------------------------------------------------------------------------

    private static void coreTierFormsAndStores(GameTestHelper helper, int tier) {
        BlockPos core = new BlockPos(5, 4, 5);
        List<BlockPos> stabs = List.of(core.west(3), core.east(3), core.north(3), core.south(3));
        int radius = tier == 3 ? 1 : 2;
        int[] parts = {0};
        helper.startSequence()
                .thenExecute(safe(() -> {
                    int rc = MultiBlockManager.placeCommand(helper.getLevel(), helper.absolutePos(core), new ResourceLocation(MODID, "energy_core_" + tier), Vec3.ZERO);
                    check(rc == 0, () -> "placeCommand(energy_core_" + tier + ") returned " + rc);
                    stabs.forEach(p -> helper.setBlock(p, DEContent.ENERGY_CORE_STABILIZER.get()));
                    BlockPos.betweenClosed(core.offset(-radius, -radius, -radius), core.offset(radius, radius, radius)).forEach(p -> {
                        if (!p.equals(core) && !helper.getBlockState(p).isAir()) parts[0]++;
                    });
                    int expected = tier == 3 ? 26 : 80;   // energy_core_3.json 3x3x3 full; energy_core_4.json 9+21+20+21+9
                    check(parts[0] == expected, () -> "placeCommand placed " + parts[0] + " blocks around the core, expected " + expected);
                    TileEnergyCore tile = tile(helper, core, TileEnergyCore.class);
                    tile.tier.set((byte) tier);
                    check(tile.validateStructure(), () -> "T" + tier + " invalid: coreValid=" + tile.coreValid.get() + " stabilizersValid=" + tile.stabilizersValid.get());
                    tile.toggleActivation();
                    check(tile.active.get(), () -> "T" + tier + " core did not activate");
                    long structure = BlockPos.betweenClosedStream(core.offset(-radius, -radius, -radius), core.offset(radius, radius, radius))
                            .filter(p -> helper.getBlockState(p).is(DEContent.STRUCTURE_BLOCK.get())).count();
                    check(structure == parts[0], () -> "activation turned " + structure + " of " + parts[0] + " blocks into structure blocks");
                    long capacity = DEConfig.coreCapacity[tier - 1];
                    check(tile.energy.getMaxOPStored() == capacity, () -> "T" + tier + " capacity " + tile.energy.getMaxOPStored() + " != " + capacity);
                    long in = tile.energy.receiveOP(capacity / 2, false);
                    check(in == capacity / 2, () -> "receiveOP accepted " + in);
                }))
                .thenIdle(5)
                .thenExecute(safe(() -> {
                    TileEnergyCore tile = tile(helper, core, TileEnergyCore.class);
                    check(tile.active.get() && tile.isStructureValid(), () -> "T" + tier + " core dropped out of the active state");
                    tile.toggleActivation();
                }))
                .thenIdle(2)   // TileStructureBlock.revert() restores on the next block tick (same as G8)
                .thenExecute(safe(() -> {
                    long restored = BlockPos.betweenClosedStream(core.offset(-radius, -radius, -radius), core.offset(radius, radius, radius))
                            .filter(p -> !p.equals(core) && !helper.getBlockState(p).isAir() && !helper.getBlockState(p).is(DEContent.STRUCTURE_BLOCK.get())).count();
                    check(restored == parts[0], () -> "deactivation restored " + restored + " of " + parts[0] + " blocks");
                }))
                .thenSucceed();
    }

    @GameTest(template = T_11, batch = BATCH_ENERGY, timeoutTicks = 100, required = REQUIRED)
    public static void energyCoreTier3FormsAndStores(GameTestHelper helper) {
        coreTierFormsAndStores(helper, 3);
    }

    @GameTest(template = T_11, batch = BATCH_ENERGY, timeoutTicks = 100, required = REQUIRED)
    public static void energyCoreTier4FormsAndStores(GameTestHelper helper) {
        coreTierFormsAndStores(helper, 4);
    }

    /** T1 core with an INPUT pylon (fed by a creative capacitor) and an OUTPUT pylon (feeding a celestial manipulator). */
    @GameTest(template = T_9, batch = BATCH_ENERGY, timeoutTicks = 200, required = REQUIRED)
    public static void energyPylonsMoveEnergyThroughCore(GameTestHelper helper) {
        BlockPos core = new BlockPos(4, 2, 4);
        List<BlockPos> stabs = List.of(core.west(2), core.east(2), core.north(2), core.south(2));
        BlockPos pIn = new BlockPos(1, 2, 1), pOut = new BlockPos(7, 2, 7), cap = new BlockPos(0, 2, 1), sink = new BlockPos(8, 2, 7);
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(1, 2, 3), "de_pylon_tester");
        helper.startSequence()
                .thenExecute(safe(() -> {
                    helper.setBlock(core, DEContent.ENERGY_CORE.get());
                    stabs.forEach(p -> helper.setBlock(p, DEContent.ENERGY_CORE_STABILIZER.get()));
                    TileEnergyCore tile = tile(helper, core, TileEnergyCore.class);
                    check(tile.validateStructure(), () -> "T1 core invalid");
                    tile.toggleActivation();
                    check(tile.active.get(), () -> "T1 core did not activate");
                    for (BlockPos p : List.of(pIn, pOut)) {
                        helper.setBlock(p, DEContent.ENERGY_PYLON.get());
                        helper.setBlock(p.above(), Blocks.GLASS);                       // neighborChanged -> validateStructure (glass -> structure block)
                        player.holdMainHand(ItemStack.EMPTY);
                        useItemOn(helper, player, p, Direction.NORTH);                 // EnergyPylon.use -> validateStructure -> selectNextCore
                        TileEnergyPylon pylon = tile(helper, p, TileEnergyPylon.class);
                        // The world keeps the structures of earlier tests; an active core of another test inside the pylon's
                        // 37-block search box can be selected first. Sneak-use cycles cores (EnergyPylon.use -> selectNextCore).
                        for (int i = 0; i < 8 && pylon.getCore() != tile; i++) {
                            player.setShiftKeyDown(true);
                            useItemOn(helper, player, p, Direction.NORTH);
                            player.setShiftKeyDown(false);
                        }
                        checkAt(helper, pylon.structureValid.get(), p, () -> "pylon structure invalid");
                        checkAt(helper, helper.getBlockState(p.above()).is(DEContent.STRUCTURE_BLOCK.get()), p.above(), () -> "glass not converted into a structure block");
                        checkAt(helper, pylon.coreOffset.notNull() && pylon.getCore() == tile, p, () -> "pylon not linked to the core (offset " + pylon.coreOffset.get() + ")");
                        checkAt(helper, pylon.direction.get() == Direction.UP, p, () -> "pylon direction " + pylon.direction.get());
                    }
                    TileEnergyPylon in = tile(helper, pIn, TileEnergyPylon.class);
                    in.handleRemoteClick(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pIn)), Direction.UP, helper.absolutePos(pIn), false));
                    check(in.ioMode.get() == EnergyPylon.Mode.INPUT, () -> "input pylon mode " + in.ioMode.get());
                    helper.assertBlockProperty(pIn, EnergyPylon.MODE, EnergyPylon.Mode.INPUT);
                    helper.setBlock(cap, DEContent.CREATIVE_OP_CAPACITOR.get());
                    consumer(helper, sink);
                }))
                .thenWaitUntil(safe(() -> {
                    TileEnergyCore tile = tile(helper, core, TileEnergyCore.class);
                    TileCelestialManipulator manip = tile(helper, sink, TileCelestialManipulator.class);
                    check(tile.energy.getOPStored() > 0, () -> "core received nothing through the input pylon");
                    check(manip.opStorage.getOPStored() >= 4_000_000L, () -> "output pylon delivered " + manip.opStorage.getOPStored() + " OP");
                }))
                .thenExecute(safe(() -> {
                    IOPStorage viaPylon = op(helper, pOut, Direction.EAST);
                    TileEnergyCore tile = tile(helper, core, TileEnergyCore.class);
                    check(viaPylon.getMaxOPStored() == tile.energy.getMaxOPStored(), () -> "pylon reports capacity " + viaPylon.getMaxOPStored());
                    check(viaPylon.receiveOP(1000, true) == 0, () -> "output pylon accepts energy");
                }))
                .thenSucceed();
    }

    /**
     * Energy Transfuser (§8.3 "Transfuser"; donor blocks/tileentity/TileEnergyTransfuser.java): a creative capacitor on the
     * top face charges the CHARGE-slot item; a DISCHARGE-slot modular item (extract override for DE equipment) feeds
     * the other CHARGE slot, energy conserved.
     */
    @GameTest(template = T_5, batch = BATCH_ENERGY, timeoutTicks = 200, required = REQUIRED)
    public static void energyTransfuserChargesAndDischargesItems(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        DETestPlayer player = DETestPlayer.create(helper, new BlockPos(0, 2, 0), "de_transfuser_tester");
        helper.setBlock(pos, DEContent.ENERGY_TRANSFUSER.get());
        com.brandon3055.draconicevolution.blocks.tileentity.TileEnergyTransfuser tile = tile(helper, pos, com.brandon3055.draconicevolution.blocks.tileentity.TileEnergyTransfuser.class);
        ItemStack[] items = new ItemStack[3];
        long[] total = {0};
        helper.startSequence()
                .thenExecute(safe(() -> {
                    for (int i = 0; i < 3; i++) {
                        items[i] = new ItemStack(DEContent.CHESTPIECE_WYVERN.get());
                        ModularTestUtil.install(items[i], player, com.brandon3055.draconicevolution.init.DEModules.ITEM_DRACONIUM_ENERGY);
                    }
                    IOPStorage source = ModularTestUtil.energy(items[2]);
                    for (int i = 0; i < 200 && source.getOPStored() < source.getMaxOPStored(); i++) source.receiveOP(source.getMaxOPStored(), false);
                    total[0] = source.getOPStored();
                    check(total[0] == 1_000_000L, () -> "discharge source holds " + total[0]);
                    tile.itemNorth.setStackInSlot(0, items[0]);   // ioModes[0] = CHARGE (default)
                    tile.itemEast.setStackInSlot(0, items[1]);    // ioModes[1] = CHARGE
                    tile.itemSouth.setStackInSlot(0, items[2]);
                    tile.ioModes[2].set(com.brandon3055.draconicevolution.blocks.tileentity.TileEnergyTransfuser.ItemIOMode.DISCHARGE);
                    tile.ioModes[3].set(com.brandon3055.draconicevolution.blocks.tileentity.TileEnergyTransfuser.ItemIOMode.DISCHARGE);
                }))
                .thenIdle(20)
                .thenExecute(safe(() -> {
                    long a = ModularTestUtil.energy(items[0]).getOPStored(), b = ModularTestUtil.energy(items[1]).getOPStored(), c = ModularTestUtil.energy(items[2]).getOPStored();
                    check(c < total[0] && a + b > 0, () -> "no item-to-item transfer: charge " + a + "/" + b + ", discharge left " + c);
                    check(a + b + c == total[0], () -> "energy not conserved: " + a + " + " + b + " + " + c + " != " + total[0]);
                    helper.setBlock(pos.above(), DEContent.CREATIVE_OP_CAPACITOR.get());   // feeds the OP adapter on the UP face
                }))
                .thenIdle(90)   // item receive limit 16 000 OP/t per insert (draconium energy module transfer)
                .thenExecute(safe(() -> {
                    IOPStorage a = ModularTestUtil.energy(items[0]);
                    check(a.getOPStored() == a.getMaxOPStored(), () -> "CHARGE item not filled from the capacitor: " + a.getOPStored() + "/" + a.getMaxOPStored());
                    helper.setBlock(pos.above(), net.minecraft.world.level.block.Blocks.AIR);
                }))
                .thenSucceed();
    }
}
