package fr.ustruct;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class Reg {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Ustruct.MODID);

    public static final RegistryObject<Item> WAND = ITEMS.register("wand",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)) {
                @Override
                public boolean isFoil(net.minecraft.world.item.ItemStack s) {
                    return true;
                }
            });
    public static final RegistryObject<Item> TABLET = ITEMS.register("tablet",
            () -> new TabletItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
}
