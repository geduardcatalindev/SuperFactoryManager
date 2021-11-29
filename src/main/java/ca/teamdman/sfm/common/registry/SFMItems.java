package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fmllegacy.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class SFMItems {
    public static final  CreativeModeTab        TAB   = new CreativeModeTab(SFM.MOD_ID) {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(SFMBlocks.MANAGER_BLOCK.get());
        }
    };
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SFM.MOD_ID);
    public static final RegistryObject<Item> MANAGER_ITEM = register("manager", SFMBlocks.MANAGER_BLOCK);
    public static final RegistryObject<Item> CABLE_ITEM   = register("cable", SFMBlocks.CABLE_BLOCK);

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private static RegistryObject<Item> register(String name, RegistryObject<Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().tab(TAB)));
    }
}