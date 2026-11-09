package fr.udynamax;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class Reg {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Udynamax.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Udynamax.MODID);

    public static final RegistryObject<Block> WARD_BLOCK = BLOCKS.register("dynamax_ward", WardBlock::new);

    public static final RegistryObject<Item> RED_STAR = ITEMS.register("red_star",
            () -> new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()) {
                @Override
                public boolean isFoil(net.minecraft.world.item.ItemStack s) {
                    return true;
                }
            });
    public static final RegistryObject<Item> BAND = ITEMS.register("dynamax_band",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()) {
                @Override
                public boolean isFoil(net.minecraft.world.item.ItemStack s) {
                    return true;
                }
            });
    public static final RegistryObject<Item> WARD_ITEM = ITEMS.register("dynamax_ward",
            () -> new BlockItem(WARD_BLOCK.get(), new Item.Properties().rarity(Rarity.RARE)));
}
