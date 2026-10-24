package com.brandon3055.draconicevolution.init;

import com.brandon3055.brandonscore.lib.CustomTabHandling;
import com.brandon3055.draconicevolution.api.modules.items.ModuleItem;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.brandon3055.draconicevolution.DraconicEvolution.MODID;

/**
 * Created by brandon3055 on 05/02/2024
 * [1.19.2 port] Pre-1.19.3 CreativeModeTab API. Contents and order replicate the 1.20.1 tab-content lambdas exactly.
 */
public class DECreativeTabs {

    //Construction order == 1.20 registration order (blocks, items, modules)
    public static final CreativeModeTab BLOCKS = new CyclingTab(MODID + ".blocks", DECreativeTabs::blockContents);
    public static final CreativeModeTab ITEMS = new CyclingTab(MODID + ".items", DECreativeTabs::itemContents);
    public static final CreativeModeTab MODULES = new CyclingTab(MODID + ".modules", DECreativeTabs::moduleContents);

    /** Kept for DraconicEvolution. Tabs are created by class initialisation. */
    public static void init() {}

    //1.20 DECreativeTabs:36-45
    private static void blockContents(Consumer<ItemStack> output) {
        for (ResourceLocation key : ForgeRegistries.BLOCKS.getKeys()) {
            if (key.getNamespace().equals(MODID)) {
                Block block = ForgeRegistries.BLOCKS.getValue(key);
                if (block instanceof CustomTabHandling || block == null) continue;
                output.accept(new ItemStack(block));
            }
        }
    }

    //1.20 DECreativeTabs:51-60
    private static void itemContents(Consumer<ItemStack> output) {
        for (ResourceLocation key : ForgeRegistries.ITEMS.getKeys()) {
            if (key.getNamespace().equals(MODID)) {
                Item item = ForgeRegistries.ITEMS.getValue(key);
                if (item instanceof CustomTabHandling || item == null || item instanceof BlockItem || item instanceof ModuleItem) continue;
                output.accept(new ItemStack(item));
            }
        }
    }

    //1.20 DECreativeTabs:66-75
    private static void moduleContents(Consumer<ItemStack> output) {
        for (ResourceLocation key : ForgeRegistries.ITEMS.getKeys()) {
            if (key.getNamespace().equals(MODID)) {
                Item item = ForgeRegistries.ITEMS.getValue(key);
                if (!(item instanceof ModuleItem)) continue;
                output.accept(new ItemStack(item));
            }
        }
    }

    private static class CyclingTab extends CreativeModeTab {
        private final Consumer<Consumer<ItemStack>> contents;
        private List<ItemStack> icons = List.of();

        private CyclingTab(String label, Consumer<Consumer<ItemStack>> contents) {
            super(label);
            this.contents = contents;
        }

        private List<ItemStack> build() {
            List<ItemStack> list = new ArrayList<>();
            contents.accept(stack -> {
                if (!stack.isEmpty()) list.add(stack); //1.20 would throw here; never hit for DE (every non-CTH block has an item)
            });
            icons = list.stream().map(ItemStack::copy).toList();
            return list;
        }

        @Override
        public void fillItemList(NonNullList<ItemStack> items) {
            items.addAll(build());
        }

        @Override
        public ItemStack makeIcon() {
            List<ItemStack> list = icons.isEmpty() ? build() : icons;
            return list.isEmpty() ? ItemStack.EMPTY : list.get(0);
        }

        //1.20 DECreativeTabs:91-94 (1200 ms per icon)
        @Override
        public ItemStack getIconItem() {
            if (icons.isEmpty()) build();
            if (icons.isEmpty()) return ItemStack.EMPTY;
            return icons.get((int) ((System.currentTimeMillis() / 1200) % icons.size()));
        }
    }
}
