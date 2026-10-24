package com.brandon3055.draconicevolution.gametest;

// WP-31 - see DEGameTestUtil header. Spec: specs/WP-31-gametests.md, tests G7-G8.
// Layout (template platform_5x4x5, floor = helper y 1, machines at helper y 2; StructureBlockEntity offset (0,1,0)):
//   core C = (2,2,2); stabilizers S at distance 2 around the Y axis:
//       z=0  . . S . .
//       z=2  S . C . S        (x = 0..4)
//       z=4  . . S . .

import com.brandon3055.brandonscore.multiblock.MultiBlockManager;
import com.brandon3055.draconicevolution.DEConfig;
import com.brandon3055.draconicevolution.blocks.machines.EnergyCore;
import com.brandon3055.draconicevolution.blocks.tileentity.TileEnergyCore;
import com.brandon3055.draconicevolution.blocks.tileentity.TileEnergyCoreStabilizer;
import com.brandon3055.draconicevolution.init.DEContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.brandon3055.draconicevolution.gametest.DEGameTestUtil.*;

@GameTestHolder(DEGameTestUtil.MODID)
@PrefixGameTestTemplate(false)
public class EnergyCoreGameTests {
    static {
        DEGameTestUtil.installReporter();
    }

    private static final BlockPos CORE = new BlockPos(2, 2, 2);
    private static final List<BlockPos> STABILIZERS = List.of(new BlockPos(0, 2, 2), new BlockPos(4, 2, 2), new BlockPos(2, 2, 0), new BlockPos(2, 2, 4));

    private static TileEnergyCore placeCoreAndStabilizers(GameTestHelper helper, boolean placeCoreBlock) {
        if (placeCoreBlock) helper.setBlock(CORE, DEContent.ENERGY_CORE.get());
        STABILIZERS.forEach(p -> helper.setBlock(p, DEContent.ENERGY_CORE_STABILIZER.get()));
        return tile(helper, CORE, TileEnergyCore.class);
    }

    /** G7: tier-1 core + 4 stabilizers validates, activates, stores OP, and deactivates when a stabilizer is removed. */
    @GameTest(template = T_5, batch = BATCH_MACHINES, timeoutTicks = 100)
    public static void tier1FormsValidatesAndStores(GameTestHelper helper) {
        TileEnergyCore core = placeCoreAndStabilizers(helper, true);
        helper.startSequence()
                .thenExecute(() -> {
                    check(MultiBlockManager.getDefinition(new ResourceLocation(MODID, "energy_core_1")) != null, () -> "energy_core_1 multiblock not loaded");
                    check(core.tier.get() == 1, () -> "default tier " + core.tier.get());
                    check(core.validateStructure(), () -> "validateStructure()=false coreValid=" + core.coreValid.get() + " stabilizersValid=" + core.stabilizersValid.get());
                    core.toggleActivation();
                    check(core.active.get(), () -> "core did not activate");
                    helper.assertBlockProperty(CORE, EnergyCore.ACTIVE, true);
                    for (BlockPos p : STABILIZERS) {
                        TileEnergyCoreStabilizer s = tile(helper, p, TileEnergyCoreStabilizer.class);
                        checkAt(helper, s.getCore() == core, p, () -> "stabilizer not bound to core");
                        checkAt(helper, s.isCoreActive.get(), p, () -> "stabilizer isCoreActive=false");
                    }
                    long cap = DEConfig.coreCapacity[0];
                    check(core.energy.getMaxOPStored() == cap, () -> "T1 capacity " + core.energy.getMaxOPStored() + " != " + cap);
                    long in = core.energy.receiveOP(1_000_000L, false);
                    check(in == 1_000_000L && core.energy.getOPStored() == 1_000_000L, () -> "receiveOP accepted " + in + ", stored " + core.energy.getOPStored());
                })
                .thenIdle(10)
                .thenExecute(() -> check(core.active.get() && core.isStructureValid(), () -> "core dropped out of the active state after 10 ticks"))
                .thenExecute(() -> {
                    helper.destroyBlock(STABILIZERS.get(0));
                    check(!core.validateStructure(), () -> "structure still valid with 3 stabilizers");
                    check(!core.active.get(), () -> "core still active with 3 stabilizers");
                    helper.assertBlockProperty(CORE, EnergyCore.ACTIVE, false);
                })
                .thenSucceed();
    }

    /**
     * G8: tier-2 core built with BC MultiBlockManager.placeCommand (exercises tag-based MultiBlockPart, the backported
     * CCL Rotation path and TileStructureBlock): activation swaps the 6 draconium blocks for structure blocks and
     * deactivation restores them.
     */
    @GameTest(template = T_5, batch = BATCH_MACHINES, timeoutTicks = 100)
    public static void tier2PlacedMultiblockFormsAndReverts(GameTestHelper helper) {
        int rc = MultiBlockManager.placeCommand(helper.getLevel(), helper.absolutePos(CORE), new ResourceLocation(MODID, "energy_core_2"), Vec3.ZERO);
        check(rc == 0, () -> "placeCommand(energy_core_2) returned " + rc);
        TileEnergyCore core = placeCoreAndStabilizers(helper, false);
        List<BlockPos> ring = List.of(CORE.above(), CORE.below(), CORE.north(), CORE.south(), CORE.east(), CORE.west());
        helper.startSequence()
                .thenExecute(() -> {
                    ring.forEach(p -> helper.assertBlockPresent(DEContent.DRACONIUM_BLOCK.get(), p));
                    core.tier.set((byte) 2);   // value listener calls validateStructure() (TileEnergyCore constructor)
                    check(core.validateStructure(), () -> "T2 invalid: coreValid=" + core.coreValid.get() + " stabilizersValid=" + core.stabilizersValid.get());
                    core.toggleActivation();
                    check(core.active.get(), () -> "T2 core did not activate");
                    ring.forEach(p -> helper.assertBlockPresent(DEContent.STRUCTURE_BLOCK.get(), p));
                    check(core.energy.getMaxOPStored() == DEConfig.coreCapacity[1], () -> "T2 capacity " + core.energy.getMaxOPStored());
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    core.toggleActivation();   // active -> deactivateCore() -> TileStructureBlock.revert()
                    check(!core.active.get(), () -> "T2 core did not deactivate");
                })
                // TileStructureBlock.revert() only schedules a block tick (level.scheduleTick(pos, STRUCTURE_BLOCK, 1));
                // StructureBlock.tick -> doRevert() restores the original block one tick later (same in 1.20.1).
                .thenIdle(2)
                .thenExecute(() -> ring.forEach(p -> helper.assertBlockPresent(DEContent.DRACONIUM_BLOCK.get(), p)))
                .thenSucceed();
    }
}
