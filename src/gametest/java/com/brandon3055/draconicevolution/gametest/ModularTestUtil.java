package com.brandon3055.draconicevolution.gametest;

// WP-31b - modular item helpers. Modules are installed through DE's own ModuleGrid.attemptInstall/checkInstall
// (api/modules/lib/ModuleGrid.java), with a minimal ModuleHostContainer around a single stack, so the same tier,
// category, overlap and grid-size checks run as in the modular item GUI.

import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.brandon3055.draconicevolution.api.modules.Module;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleContext;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleEntity;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleGrid;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleHostContainer;
import com.brandon3055.draconicevolution.api.modules.lib.StackModuleContext;
import com.brandon3055.brandonscore.api.power.IOPStorage;
import com.brandon3055.brandonscore.utils.EnergyUtils;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public final class ModularTestUtil {
    private ModularTestUtil() {}

    public static ModuleHost host(ItemStack stack) {
        return stack.getCapability(DECapabilities.MODULE_HOST_CAPABILITY).orElseThrow(() -> new GameTestAssertException(stack + " has no module host capability"));
    }

    public static IOPStorage energy(ItemStack stack) {
        IOPStorage storage = EnergyUtils.getStorage(stack);
        if (storage == null) throw new GameTestAssertException(stack + " has no OP storage");
        return storage;
    }

    /** Container for ModuleGrid without a menu: one stack, held by {@code player} in {@code slot} (may be null). */
    public static final class StackContainer implements ModuleHostContainer {
        private final ItemStack stack;
        private final Player player;
        private final EquipmentSlot slot;
        private final ModuleGrid grid;

        public StackContainer(ItemStack stack, Player player, @Nullable EquipmentSlot slot) {
            this.stack = stack;
            this.player = player;
            this.slot = slot;
            this.grid = new ModuleGrid(this, player.getInventory());
        }

        @Override
        public ModuleGrid getGrid() {
            return grid;
        }

        @Override
        public ModuleHost getModuleHost() {
            return host(stack);
        }

        @Override
        public ModuleContext getModuleContext() {
            return new StackModuleContext(stack, player, slot);
        }

        @Override
        public void onGridChange() {}
    }

    /** Installs one module (from a module item stack, so item NBT such as undying charge is read) in the first free cell. */
    public static ModuleEntity<?> install(ItemStack host, Player player, ItemStack moduleStack) {
        Module<?> module = com.brandon3055.draconicevolution.api.modules.items.ModuleItem.getModule(moduleStack);
        if (module == null) throw new GameTestAssertException(moduleStack + " is not a module item");
        StackContainer container = new StackContainer(host, player, null);
        ModuleEntity<?> entity = module.createEntity();
        entity.readFromItemStack(moduleStack, container.getModuleContext());
        if (!container.getGrid().attemptInstall(entity)) {
            throw new GameTestAssertException("could not install " + moduleStack + " into " + host + " (" + host(host).getModuleEntities().size() + " modules installed)");
        }
        return entity;
    }

    public static ModuleEntity<?> install(ItemStack host, Player player, Supplier<? extends net.minecraft.world.item.Item> moduleItem) {
        return install(host, player, new ItemStack(moduleItem.get()));
    }
}
