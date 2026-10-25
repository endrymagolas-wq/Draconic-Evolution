package com.brandon3055.draconicevolution.gametest;

// WP-31b - test-only fluid tanks. Neither vanilla 1.19.2 nor DE has a block with a fluid-handler capability, which the
// fluid gate (TileFluidGate) needs on both sides. On the gametest server only, every vanilla barrel block entity gets a
// 16 000 mB FluidTank capability attached (AttachCapabilitiesEvent<BlockEntity>, a Forge-bus event; Forge 43
// BlockEntity gathers attached capabilities in its constructor). No registry entries are added (G1/T* stay unchanged)
// and nothing happens on a normal server (ForgeGameTestHooks.isGametestServer()).

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.brandon3055.draconicevolution.gametest.DEGameTestUtil.checkAt;

@Mod.EventBusSubscriber(modid = DEGameTestUtil.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GameTestFluidTanks {
    private static final ResourceLocation KEY = new ResourceLocation(DEGameTestUtil.MODID, "gametest_tank");
    public static final int CAPACITY = 16_000;

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        if (!(event.getObject() instanceof BarrelBlockEntity) || !ForgeGameTestHooks.isGametestServer()) {
            return;
        }
        FluidTank tank = new FluidTank(CAPACITY);
        LazyOptional<IFluidHandler> optional = LazyOptional.of(() -> tank);
        event.addCapability(KEY, new ICapabilityProvider() {
            @Nonnull
            @Override
            public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
                return ForgeCapabilities.FLUID_HANDLER.orEmpty(cap, optional);
            }
        });
    }

    public static IFluidHandler tank(GameTestHelper helper, BlockPos rel) {
        BlockEntity be = helper.getBlockEntity(rel);
        IFluidHandler handler = be == null ? null : be.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
        checkAt(helper, handler != null, rel, () -> "no gametest tank capability on " + be);
        return handler;
    }
}
