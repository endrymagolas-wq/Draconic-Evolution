package com.brandon3055.draconicevolution.gametest;

// WP-31 - see DEGameTestUtil header. Spec: specs/WP-31-gametests.md, test G10.
// TileGenerator NORMAL mode: 40 OP/t, 10 OP per fuel unit -> 4 fuel/t; coal = 1600 fuel (AbstractFurnaceBlockEntity) = 400 ticks.

import com.brandon3055.draconicevolution.blocks.machines.Generator;
import com.brandon3055.draconicevolution.blocks.tileentity.TileGenerator;
import com.brandon3055.draconicevolution.init.DEContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.brandon3055.draconicevolution.gametest.DEGameTestUtil.*;

@GameTestHolder(DEGameTestUtil.MODID)
@PrefixGameTestTemplate(false)
public class GeneratorGameTests {
    static {
        DEGameTestUtil.installReporter();
    }

    private static final BlockPos GEN = new BlockPos(2, 2, 2);

    /** G10: one coal in slot 0 -> generator refuels, goes ACTIVE and stores OP at the NORMAL-mode rate. */
    @GameTest(template = T_5, batch = BATCH_MACHINES, timeoutTicks = 200)
    public static void generatorBurnsCoalIntoOP(GameTestHelper helper) {
        helper.setBlock(GEN, DEContent.GENERATOR.get());
        TileGenerator gen = tile(helper, GEN, TileGenerator.class);
        check(gen.mode.get() == TileGenerator.Mode.NORMAL, () -> "default mode " + gen.mode.get());
        gen.itemHandler.setStackInSlot(0, new ItemStack(Items.COAL));
        helper.startSequence()
                .thenIdle(41)   // tick 1 refuels (fuel 0 -> tryRefuel), then 40 producing ticks
                .thenExecute(() -> {
                    check(gen.itemHandler.getStackInSlot(0).isEmpty(), () -> "coal not consumed: " + gen.itemHandler.getStackInSlot(0));
                    check(gen.active.get(), () -> "generator not active, fuelRemaining=" + gen.fuelRemaining.get());
                    helper.assertBlockProperty(GEN, Generator.ACTIVE, true);
                    check(gen.productionRate.get() == TileGenerator.Mode.NORMAL.powerOutput, () -> "productionRate " + gen.productionRate.get());
                    long stored = gen.opStorage.getOPStored();
                    // 40 OP/t while stored < 75% of 100000 (genRate = min(1, 4*(1 - stored/max)))
                    check(stored >= 35L * 40L && stored <= 41L * 40L, () -> "stored " + stored + " OP after ~40 ticks, expected ~1600");
                    check(gen.fuelRemaining.get() < 1600 && gen.fuelRemaining.get() > 1600 - 41 * 4 - 4, () -> "fuelRemaining " + gen.fuelRemaining.get());
                })
                .thenSucceed();
    }
}
