package com.brandon3055.draconicevolution.gametest;

// WP-31 - optional G12. NOT annotated with @GameTestHolder, so ForgeGameTestHooks' annotation scan skips it;
// it is registered through RegisterGameTestsEvent only when -Ddeport.gametest.reactorRun=true is passed, and its
// @GameTest carries templateNamespace so the forge.enabledGameTestNamespaces filter still matches
// [V ForgeGameTestHooks.java:98-115, RegisterGameTestsEvent.java:42-45].
// Risk: RUNNING is the first state that can reach BEYOND_HOPE (TileReactorCore.java:404-413). The guard below forces
// COLD the moment temperature or shield leave a conservative envelope, and DEConfig.disableLargeReactorBoom is set for
// the batch so a failure produces the small boom instead of ProcessExplosion.

import com.brandon3055.draconicevolution.DEConfig;
import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorCore;
import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorCore.ReactorState;
import com.brandon3055.draconicevolution.init.DEContent;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.brandon3055.draconicevolution.gametest.DEGameTestUtil.*;

// Without @PrefixGameTestTemplate(false) Forge prefixes the template with the class name
// ("reactorrungametests.platform_11x9x11") and a missing template crashes the whole gametest server (run-09).
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = DEGameTestUtil.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ReactorRunGameTests {
    static final String BATCH = "de_reactor_run";

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        if (Boolean.getBoolean("deport.gametest.reactorRun")) {
            event.register(ReactorRunGameTests.class);
        }
    }

    @BeforeBatch(batch = BATCH)
    public static void before(ServerLevel level) {
        DEConfig.disableLargeReactorBoom = true;
    }

    @AfterBatch(batch = BATCH)
    public static void after(ServerLevel level) {
        ReactorGameTests.coolDownAll();
    }

    /** G12 (optional): activate, run 200 ticks with a creative-fed shield, assert stable generation, force COLD. */
    @GameTest(templateNamespace = DEGameTestUtil.MODID, template = T_11, batch = BATCH, timeoutTicks = 600, required = false)
    public static void reactorRunsStable(GameTestHelper helper) {
        TileReactorCore core = ReactorGameTests.build(helper);
        core.reactableFuel.set(ReactorGameTests.FUEL);
        core.chargeReactor();
        helper.setBlock(ReactorGameTests.CAPACITOR, DEContent.CREATIVE_OP_CAPACITOR.get());
        long[] runningSince = {-1};
        helper.onEachTick(() -> {
            boolean envelopeBroken = core.temperature.get() > 8000D
                    || (core.reactorState.get() == ReactorState.RUNNING && core.shieldCharge.get() < core.maxShieldCharge.get() * 0.2D);
            if (envelopeBroken) {
                ReactorGameTests.forceSafeTeardown(helper, core);
                helper.fail(String.format("envelope broken: T=%.0f shield=%.0f/%.0f", core.temperature.get(), core.shieldCharge.get(), core.maxShieldCharge.get()));
            }
            if (core.reactorState.get() == ReactorState.WARMING_UP && core.canActivate()) {
                core.activateReactor();
            }
            if (core.reactorState.get() == ReactorState.RUNNING && runningSince[0] < 0) {
                runningSince[0] = helper.getTick();
            }
        });
        helper.succeedWhen(() -> {
            // Exploratory: the exact 1.20.1 trend (temperature/saturation curve without extraction) is compared manually
            // in WP-42; here only "reaches RUNNING and stays inside the envelope for 200 ticks" is asserted.
            check(runningSince[0] >= 0 && helper.getTick() - runningSince[0] >= 200, () -> "RUNNING for " + (runningSince[0] < 0 ? 0 : helper.getTick() - runningSince[0]) + " ticks");
            check(core.reactorState.get() == ReactorState.RUNNING || core.reactorState.get() == ReactorState.STOPPING, () -> "state " + core.reactorState.get());
            LOGGER.info("DEPORT-GATE reactor.run T={} sat={}/{} shield={}/{} gen={}", core.temperature.get(), core.saturation.get(),
                    core.maxSaturation.get(), core.shieldCharge.get(), core.maxShieldCharge.get(), core.generationRate.get());
            ReactorGameTests.forceSafeTeardown(helper, core);
        });
    }
}
