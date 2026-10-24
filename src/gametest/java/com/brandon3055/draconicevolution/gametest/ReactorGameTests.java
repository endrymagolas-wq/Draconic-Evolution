package com.brandon3055.draconicevolution.gametest;

// WP-31 - see DEGameTestUtil header. Spec: specs/WP-31-gametests.md, test G11 (+ optional G12 in ReactorRunGameTests).
// Layout (platform_11x9x11, floor helper y 1): core R=(5,3,5) (one block above the floor so the intrusion check,
// radius ~1 at 1296 fuel / 2500 C, never touches the floor); 4 stabilizers at distance 5 around the Y axis facing R;
// injector at (5,8,5) facing DOWN; creative OP capacitor at (5,9,5) on the injector's input face (UP).
// Safety: the test never leaves WARMING_UP (no reaction, no shield drain, no BEYOND_HOPE), and the teardown forces COLD
// before removing the blocks. TileReactorCore.updateCriticalState()/explosion paths are unreachable from WARMING_UP.

import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorComponent;
import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorCore;
import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorCore.ReactorState;
import com.brandon3055.draconicevolution.init.DEContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.brandon3055.draconicevolution.gametest.DEGameTestUtil.*;

@GameTestHolder(DEGameTestUtil.MODID)
@PrefixGameTestTemplate(false)
public class ReactorGameTests {
    static {
        DEGameTestUtil.installReporter();
    }

    static final BlockPos CORE = new BlockPos(5, 3, 5);
    static final BlockPos INJECTOR = CORE.above(5);
    static final BlockPos CAPACITOR = INJECTOR.above();
    static final double FUEL = 1296D;   // one awakened draconium block (ReactorMenu.getFuelValue)

    /** component position -> facing (TileReactorCore.findComponents requires facing == search direction opposite). */
    static Map<BlockPos, Direction> components() {
        Map<BlockPos, Direction> m = new LinkedHashMap<>();
        m.put(CORE.north(5), Direction.SOUTH);
        m.put(CORE.south(5), Direction.NORTH);
        m.put(CORE.east(5), Direction.WEST);
        m.put(CORE.west(5), Direction.EAST);
        return m;
    }

    /** Every core built by G11/G12, so that @AfterBatch can cool down a reactor whose test failed or timed out. */
    static final List<TileReactorCore> BUILT = new ArrayList<>();

    static TileReactorCore build(GameTestHelper helper) {
        helper.setBlock(CORE, DEContent.REACTOR_CORE.get());
        components().forEach((p, f) -> helper.setBlock(p, DEContent.REACTOR_STABILIZER.get()));
        helper.setBlock(INJECTOR, DEContent.REACTOR_INJECTOR.get());
        TileReactorCore core = tile(helper, CORE, TileReactorCore.class);
        synchronized (BUILT) {
            BUILT.add(core);
        }
        // ReactorComponent.setPlacedBy() normally sets facing from the placer and then calls onPlaced(); here all facings
        // are set first (as if every block had been placed by a player), then each component pokes the core.
        Map<BlockPos, Direction> all = new LinkedHashMap<>(components());
        all.put(INJECTOR, Direction.DOWN);
        all.forEach((p, f) -> tile(helper, p, TileReactorComponent.class).facing.set(f));
        all.keySet().forEach(p -> tile(helper, p, TileReactorComponent.class).onPlaced());
        return core;
    }

    /**
     * Failure path of G11/G12 (spec G11 verifier note): succeedWhen never ran the teardown, so a 2500 C WARMING_UP core may
     * still sit in the test world. Force COLD + 20 C on every core built in this run, before anything else can break a
     * stabilizer (TileReactorCore.componentBroken -> BEYOND_HOPE / minimalBoom when not COLD).
     */
    static void coolDownAll() {
        synchronized (BUILT) {
            for (TileReactorCore core : BUILT) {
                if (core.isRemoved()) continue;
                core.reactorState.set(ReactorState.COLD);
                core.temperature.set(20D);
                core.shieldCharge.set(0D);
                core.saturation.set(0L);
                core.reactableFuel.set(0D);
                core.convertedFuel.set(0D);
            }
            BUILT.clear();
        }
    }

    @AfterBatch(batch = BATCH_REACTOR)
    public static void afterReactorBatch(ServerLevel level) {
        coolDownAll();
    }

    static void forceSafeTeardown(GameTestHelper helper, TileReactorCore core) {
        helper.setBlock(CAPACITOR, Blocks.AIR);
        core.reactorState.set(ReactorState.COLD);
        core.temperature.set(20D);
        core.shieldCharge.set(0D);
        core.saturation.set(0L);
        core.reactableFuel.set(0D);
        core.convertedFuel.set(0D);
        helper.setBlock(CORE, Blocks.AIR);
    }

    /** G11: structure assembles (INVALID -> COLD), accepts fuel, charges (COLD -> WARMING_UP) and reaches canActivate(). */
    @GameTest(template = T_11, batch = BATCH_REACTOR, timeoutTicks = 400)
    public static void reactorAssemblesAndCharges(GameTestHelper helper) {
        TileReactorCore core = build(helper);
        check(core.structureValid.get(), () -> "structure invalid: " + core.structureError.get());
        check(core.reactorState.get() == ReactorState.COLD, () -> "state after assembly " + core.reactorState.get());
        TileReactorComponent inj = tile(helper, INJECTOR, TileReactorComponent.class);
        checkAt(helper, inj.isBound.get(), INJECTOR, () -> "injector not bound to the core");

        check(!core.canCharge(), () -> "canCharge() true without fuel");
        core.reactableFuel.set(FUEL);
        check(core.canCharge(), () -> "canCharge() false with " + FUEL + " fuel");
        core.chargeReactor();                                   // server thread -> WARMING_UP
        check(core.reactorState.get() == ReactorState.WARMING_UP, () -> "state after chargeReactor " + core.reactorState.get());
        helper.setBlock(CAPACITOR, DEContent.CREATIVE_OP_CAPACITOR.get());   // feeds the injector's UP face

        helper.succeedWhen(() -> {
            check(core.reactorState.get() == ReactorState.WARMING_UP, () -> "left WARMING_UP: " + core.reactorState.get());
            check(core.startupInitialized.get(), () -> "startup not initialised");
            check(core.maxShieldCharge.get() > 0 && core.maxSaturation.get() > 0, () -> "max shield/saturation not set");
            check(core.canActivate(), () -> String.format("not ready: T=%.0f sat=%d/%d shield=%.0f/%.0f", core.temperature.get(),
                    core.saturation.get(), core.maxSaturation.get(), core.shieldCharge.get(), core.maxShieldCharge.get()));
            forceSafeTeardown(helper, core);
        });
    }
}
