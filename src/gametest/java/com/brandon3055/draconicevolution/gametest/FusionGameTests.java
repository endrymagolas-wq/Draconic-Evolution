package com.brandon3055.draconicevolution.gametest;

// WP-31 - see DEGameTestUtil header. Spec: specs/WP-31-gametests.md, test G9.
// Recipe: draconicevolution:wyvern_crafting_injector (cheapest fusion recipe, 32000 OP, tier DRACONIUM, 8 ingredients):
//   catalyst basic_crafting_injector; wyvern_core, 4x #forge:gems/diamond, 2x draconium_core, #forge:storage_blocks/draconium
// Layout (platform_9x4x9, helper y 2): core K=(4,2,4); 8 chaotic injectors I at Chebyshev distance 3 facing K;
// a creative OP capacitor P behind each injector (pushes 1e9 OP/t to all 6 neighbours).
//       z=0  . . . P . P . . .
//       z=1  . . . I . I . . .
//       z=3  P I . . . . . I P
//       z=4  . . . . K . . . .
//       z=5  P I . . . . . I P
//       z=7  . . . I . I . . .
//       z=8  . . . P . P . . .      (x = 0..8)

import com.brandon3055.draconicevolution.api.crafting.IFusionStateMachine;
import com.brandon3055.draconicevolution.blocks.machines.CraftingInjector;
import com.brandon3055.draconicevolution.blocks.tileentity.TileFusionCraftingCore;
import com.brandon3055.draconicevolution.blocks.tileentity.TileFusionCraftingInjector;
import com.brandon3055.draconicevolution.init.DEContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.brandon3055.draconicevolution.gametest.DEGameTestUtil.*;

@GameTestHolder(DEGameTestUtil.MODID)
@PrefixGameTestTemplate(false)
public class FusionGameTests {
    static {
        DEGameTestUtil.installReporter();
    }

    private static final BlockPos CORE = new BlockPos(4, 2, 4);

    /** injector position -> FACING (must point at the core: Direction.getNearest(injector - core) == FACING.getOpposite()). */
    private static Map<BlockPos, Direction> injectors() {
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

    /** G9: a complete fusion craft (START -> CHARGING -> CRAFTING -> complete) with real OP delivery through capabilities. */
    @GameTest(template = T_9, batch = BATCH_MACHINES, timeoutTicks = 600)
    public static void fusionCraftsWyvernInjector(GameTestHelper helper) {
        helper.setBlock(CORE, DEContent.CRAFTING_CORE.get());
        List<TileFusionCraftingInjector> inj = new ArrayList<>();
        for (Map.Entry<BlockPos, Direction> e : injectors().entrySet()) {
            BlockPos p = e.getKey();
            helper.setBlock(p, DEContent.CHAOTIC_CRAFTING_INJECTOR.get().defaultBlockState().setValue(CraftingInjector.FACING, e.getValue()));
            helper.setBlock(p.relative(e.getValue().getOpposite()), DEContent.CREATIVE_OP_CAPACITOR.get());   // behind the injector
            inj.add(tile(helper, p, TileFusionCraftingInjector.class));
        }
        List<ItemStack> ingredients = List.of(
                new ItemStack(DEContent.CORE_WYVERN.get()),
                new ItemStack(DEContent.CORE_DRACONIUM.get()), new ItemStack(DEContent.CORE_DRACONIUM.get()),
                new ItemStack(Items.DIAMOND), new ItemStack(Items.DIAMOND), new ItemStack(Items.DIAMOND), new ItemStack(Items.DIAMOND),
                new ItemStack(DEContent.DRACONIUM_BLOCK.get()));
        for (int i = 0; i < inj.size(); i++) inj.get(i).setInjectorStack(ingredients.get(i).copy());

        TileFusionCraftingCore core = tile(helper, CORE, TileFusionCraftingCore.class);
        core.itemHandler.setStackInSlot(0, new ItemStack(DEContent.BASIC_CRAFTING_INJECTOR.get()));   // catalyst slot 0
        core.startCraft();   // updateInjectors() + RecipeManager.getRecipeFor(FUSION_RECIPE_TYPE) + canStartCraft()
        check(core.isCrafting(), () -> "startCraft() did not start: injectors=" + core.getInjectors().size()
                + " status=" + (core.userStatus.get() == null ? "null" : core.userStatus.get().getString()));
        check(core.getInjectors().size() == 8, () -> "core sees " + core.getInjectors().size() + " injectors, expected 8");

        boolean[] sawCharging = {false};
        helper.onEachTick(() -> {
            if (core.isCrafting() && core.getFusionState() == IFusionStateMachine.FusionState.CHARGING) sawCharging[0] = true;
        });
        helper.succeedWhen(() -> {
            ItemStack out = core.getOutputStack();
            check(!core.isCrafting(), () -> "still crafting, state=" + core.getFusionState() + " progress=" + core.progress.get());
            check(out.is(DEContent.ITEM_WYVERN_CRAFTING_INJECTOR.get()) && out.getCount() == 1, () -> "output slot holds " + out);
            check(core.getCatalystStack().isEmpty(), () -> "catalyst not consumed: " + core.getCatalystStack());
            for (TileFusionCraftingInjector t : inj) {
                check(t.getInjectorStack().isEmpty(), () -> "injector at " + t.getBlockPos() + " still holds " + t.getInjectorStack());
            }
            check(sawCharging[0], () -> "CHARGING state never observed");
        });
    }
}
