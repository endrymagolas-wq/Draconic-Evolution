package com.brandon3055.draconicevolution.gametest;

// WP-31b - controlled long reactor run (PORTING_PLAN §8.3 "Реактор": stable run, trends, shutdown).
// Same assembly as G11 (ReactorGameTests.build: 1296 fuel = one awakened draconium block, creative-fed injector).
// Control law: from RUNNING, a constant 300 000 OP/t is extracted through one stabilizer's OP capability (what a flux
// gate set to 300k would pull), for RUN_TICKS ticks; then shutdownReactor() and extraction stops, and the test waits
// for STOPPING -> COOLING. Every tick the state is compared with an independent copy of the 1.20.1 donor formulas
// (donors/de-1.20/.../blocks/reactor/tileentity/TileReactorCore.java updateOnlineState()/injectEnergy(), unchanged in
// the port apart from the explosion enum): the donor model, replayed offline (tools/refdiff/reactor_model.py),
// reproduces G12's logged values exactly (T=2557.3, sat=106262915, gen=113267 at 200 ticks).
// Model expectation for this run: T 2500 -> ~3254 C, saturation ~50 % -> ~61 %, shield 100 %, COOLING ~1280 ticks
// after shutdown. Safety envelope (forced COLD + fail): T > 6000 or shield < 50 %; disableLargeReactorBoom for the batch.
// CSV: <run dir>/deport-out/reactor-longrun.csv (one row per 20 ticks + every state change).

import com.brandon3055.brandonscore.api.power.IOPStorage;
import com.brandon3055.brandonscore.utils.EnergyUtils;
import com.brandon3055.draconicevolution.DEConfig;
import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorCore;
import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorCore.ReactorState;
import com.brandon3055.draconicevolution.init.DEContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static com.brandon3055.draconicevolution.gametest.DEGameTestUtil.*;

@GameTestHolder(DEGameTestUtil.MODID)
@PrefixGameTestTemplate(false)
public class ReactorLongRunGameTests {
    static {
        DEGameTestUtil.installReporter();
    }

    static final boolean REQUIRED = false;
    static final long EXTRACT_PER_TICK = 300_000L;
    static final int RUN_TICKS = 2400;
    static final String CSV = "reactor-longrun.csv";
    private static boolean savedBoomSetting;

    @BeforeBatch(batch = BATCH_REACTOR_LONG)
    public static void before(ServerLevel level) {
        savedBoomSetting = DEConfig.disableLargeReactorBoom;
        DEConfig.disableLargeReactorBoom = true;
    }

    @AfterBatch(batch = BATCH_REACTOR_LONG)
    public static void after(ServerLevel level) {
        ReactorGameTests.coolDownAll();
        DEConfig.disableLargeReactorBoom = savedBoomSetting;
    }

    /** Independent copy of the 1.20.1 running-state formulas (see header), used as the expected trend. */
    static final class DonorModel {
        double temp, reactable, converted, shield, maxShield;
        long sat, maxSat;

        DonorModel(TileReactorCore core) {
            temp = core.temperature.get();
            reactable = core.reactableFuel.get();
            converted = core.convertedFuel.get();
            shield = core.shieldCharge.get();
            maxShield = core.maxShieldCharge.get();
            sat = core.saturation.get();
            maxSat = core.maxSaturation.get();
        }

        void step(boolean stopping, long extract, double injected) {
            double coreSat = (double) sat / (double) maxSat;
            double negCSat = (1D - coreSat) * 99D;
            double temp50 = Math.min((temp / TileReactorCore.MAX_TEMPERATURE) * 50, 99);
            double tFuel = converted + reactable;
            double convLVL = ((converted / tFuel) * 1.3D) - 0.3D;
            double tempRiseExpo = (negCSat * negCSat * negCSat) / (100 - negCSat) + 444.7;
            double tempRiseResist = (temp50 * temp50 * temp50 * temp50) / (100 - temp50);
            double riseAmount = (tempRiseExpo - (tempRiseResist * (1D - convLVL)) + convLVL * 1000) / 10000;
            if (stopping && convLVL < 1) {
                if (sat >= maxSat * 0.99D && reactable > 0D) temp -= 1D - convLVL;
                else temp += riseAmount * 10;
            } else {
                temp += riseAmount * 10;
            }
            int baseMaxRFt = (int) ((maxSat / 1000D) * DEConfig.reactorOutputMultiplier * 1.5D * 10);
            int maxRFt = (int) (baseMaxRFt * (1D + (convLVL * 2)));
            double gen = (1D - coreSat) * maxRFt;
            sat += (int) gen;
            double tdf = temp > 8000 ? 1 + ((temp - 8000) * (temp - 8000) * 0.0000025) : temp > 2000 ? 1 : temp > 1000 ? (temp - 1000) / 1000 : 0;
            int drain = (int) Math.min(tdf * Math.max(0.01, (1D - coreSat)) * (baseMaxRFt / 10.923556), (double) Integer.MAX_VALUE);
            shield -= Math.min(drain, shield);
            double fuelUse = tdf * (1D - coreSat) * (0.001 * DEConfig.reactorFuelUsageMultiplier * 5);
            if (reactable > 0) {
                converted += fuelUse;
                reactable -= fuelUse;
            }
            double tempFactor = temp > 15000 ? 1D - Math.min(1, (temp - 15000D) / 10000D) : 1;
            shield += Math.min(injected * (1D - (shield / maxShield)), maxShield - shield) * tempFactor;
            if (shield > maxShield) shield = maxShield;
            sat -= Math.min(sat, extract);
        }
    }

    static void writeCsv(StringBuilder csv) {
        try {
            OUT_DIR.mkdirs();
            Files.writeString(new File(OUT_DIR, CSV).toPath(), csv.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Could not write {}: {}", CSV, e.toString());
        }
    }

    @GameTest(template = T_11, batch = BATCH_REACTOR_LONG, timeoutTicks = 5200, required = REQUIRED)
    public static void reactorControlledLongRunAndShutdown(GameTestHelper helper) {
        TileReactorCore core = ReactorGameTests.build(helper);
        BlockPos stabPos = ReactorGameTests.CORE.north(5);   // facing SOUTH -> OP capability on its NORTH face
        StringBuilder csv = new StringBuilder("tick,phase,state,temperature,saturation,max_saturation,shield,max_shield,generation,field_drain,reactable_fuel,converted_fuel,extracted,model_temperature,model_saturation\n");
        long[] runStart = {-1}, stopTick = {-1}, coolingTick = {-1};
        long[] extractedTotal = {0};
        double[] maxTemp = {0}, minShieldFrac = {1}, maxTempDev = {0}, maxSatDev = {0};
        double[] tempAtStop = {0}, satAtStop = {0};
        boolean[] done = {false}, failed = {false};
        DonorModel[] model = {null};
        ReactorState[] lastState = {null};

        safe(() -> {
            check(core.structureValid.get(), () -> "structure invalid: " + core.structureError.get());
            core.reactableFuel.set(ReactorGameTests.FUEL);
            core.chargeReactor();
            check(core.reactorState.get() == ReactorState.WARMING_UP, () -> "state after chargeReactor " + core.reactorState.get());
            helper.setBlock(ReactorGameTests.CAPACITOR, DEContent.CREATIVE_OP_CAPACITOR.get());
        }).run();

        helper.onEachTick(safe(() -> {
            if (done[0] || failed[0]) return;
            ReactorState state = core.reactorState.get();
            long tick = helper.getTick();
            double tFrac = core.maxShieldCharge.get() > 0 ? core.shieldCharge.get() / core.maxShieldCharge.get() : 0;
            boolean running = state == ReactorState.RUNNING || state == ReactorState.STOPPING;
            if (core.temperature.get() > 6000D || (running && tFrac < 0.5D)) {
                failed[0] = true;
                ReactorGameTests.forceSafeTeardown(helper, core);
                writeCsv(csv);
                helper.fail(String.format("envelope broken at tick %d: T=%.0f shield=%.3f state=%s", tick, core.temperature.get(), tFrac, state));
                return;
            }
            ReactorState atCoreTick = state;   // the core already ticked this server tick (levels tick before GameTestTicker)
            if (state == ReactorState.WARMING_UP && core.canActivate()) {
                core.activateReactor();
                state = core.reactorState.get();
            }
            long extracted = 0;
            boolean firstRunTick = false;
            if (state == ReactorState.RUNNING) {
                if (runStart[0] < 0) {
                    runStart[0] = tick;
                    firstRunTick = true;
                }
                long sinceRun = tick - runStart[0];
                if (sinceRun >= RUN_TICKS) {
                    tempAtStop[0] = core.temperature.get();
                    satAtStop[0] = (double) core.saturation.get() / core.maxSaturation.get();
                    core.shutdownReactor();
                    stopTick[0] = tick;
                    state = core.reactorState.get();
                } else {
                    IOPStorage out = EnergyUtils.getStorage(helper.getBlockEntity(stabPos), Direction.NORTH);
                    check(out != null, () -> "stabilizer has no OP capability on its back face");
                    extracted = out.extractOP(EXTRACT_PER_TICK, false);
                    extractedTotal[0] += extracted;
                }
            }
            if (firstRunTick) {
                // activation happened after this tick's core update: the model starts from the state as it is now
                model[0] = new DonorModel(core);
            } else if (model[0] != null && (atCoreTick == ReactorState.RUNNING || atCoreTick == ReactorState.STOPPING)
                    && (state == ReactorState.RUNNING || state == ReactorState.STOPPING)) {
                // replay this tick in the donor model: update (in the state the core ticked in), injection, extraction
                model[0].step(atCoreTick == ReactorState.STOPPING, extracted, 1e9);
                maxTempDev[0] = Math.max(maxTempDev[0], Math.abs(model[0].temp - core.temperature.get()) / TileReactorCore.MAX_TEMPERATURE);
                maxSatDev[0] = Math.max(maxSatDev[0], Math.abs(model[0].sat - core.saturation.get()) / (double) core.maxSaturation.get());
            }
            if (running) {
                maxTemp[0] = Math.max(maxTemp[0], core.temperature.get());
                minShieldFrac[0] = Math.min(minShieldFrac[0], tFrac);
            }
            if (state == ReactorState.COOLING && coolingTick[0] < 0) {
                coolingTick[0] = tick;
            }
            if (tick % 20 == 0 || state != lastState[0]) {
                String phase = runStart[0] < 0 ? "warmup" : stopTick[0] < 0 ? "run" : coolingTick[0] < 0 ? "stopping" : "cooling";
                csv.append(String.format(java.util.Locale.ROOT, "%d,%s,%s,%.3f,%d,%d,%.1f,%.1f,%.1f,%d,%.4f,%.4f,%d,%.3f,%d%n", tick, phase, state, core.temperature.get(),
                        core.saturation.get(), core.maxSaturation.get(), core.shieldCharge.get(), core.maxShieldCharge.get(), core.generationRate.get(),
                        core.fieldDrain.get(), core.reactableFuel.get(), core.convertedFuel.get(), extracted,
                        model[0] == null ? 0D : model[0].temp, model[0] == null ? 0L : model[0].sat));
            }
            lastState[0] = state;
            if (coolingTick[0] >= 0 && tick - coolingTick[0] >= 20) {
                done[0] = true;
            }
        }));

        helper.succeedWhen(safe(() -> {
            check(done[0], () -> String.format("not finished: state=%s T=%.0f sat=%d/%d runStart=%d stop=%d", core.reactorState.get(), core.temperature.get(),
                    core.saturation.get(), core.maxSaturation.get(), runStart[0], stopTick[0]));
            writeCsv(csv);
            LOGGER.info("DEPORT-GATE reactor.longrun run_ticks={} extracted={} T_stop={} sat_stop={} T_max={} shield_min={} stop_to_cooling={} model_dev_T={} model_dev_sat={} csv={}",
                    RUN_TICKS, extractedTotal[0], String.format("%.1f", tempAtStop[0]), String.format("%.4f", satAtStop[0]), String.format("%.1f", maxTemp[0]),
                    String.format("%.4f", minShieldFrac[0]), coolingTick[0] - stopTick[0], String.format("%.6f", maxTempDev[0]), String.format("%.6f", maxSatDev[0]),
                    new File(OUT_DIR, CSV).getAbsolutePath());
            ReactorGameTests.forceSafeTeardown(helper, core);
            // trend bands around the donor-model values (T 3254, sat 0.609, shield 1.0, ~1280 ticks to COOLING)
            check(tempAtStop[0] > 3000 && tempAtStop[0] < 3500, () -> "temperature at shutdown " + tempAtStop[0] + " outside 3000..3500 (model 3254)");
            check(satAtStop[0] > 0.55 && satAtStop[0] < 0.67, () -> "saturation at shutdown " + satAtStop[0] + " outside 0.55..0.67 (model 0.609)");
            check(minShieldFrac[0] > 0.99, () -> "shield dropped to " + minShieldFrac[0]);
            check(extractedTotal[0] == EXTRACT_PER_TICK * RUN_TICKS, () -> "extracted " + extractedTotal[0] + " OP, expected " + EXTRACT_PER_TICK * RUN_TICKS);
            check(coolingTick[0] - stopTick[0] > 600 && coolingTick[0] - stopTick[0] < 2000, () -> "STOPPING lasted " + (coolingTick[0] - stopTick[0]) + " ticks (model ~1280)");
            check(maxTempDev[0] < 1e-6 && maxSatDev[0] < 1e-6, () -> String.format("deviation from the 1.20.1 donor model: T %.3g, sat %.3g (limit 1e-6 of range)", maxTempDev[0], maxSatDev[0]));
        }));
    }
}
