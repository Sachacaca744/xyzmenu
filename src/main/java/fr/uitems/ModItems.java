package fr.uitems;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Uitems.MODID);

    public static final RegistryObject<Item> STAFF_SHIRT = ITEMS.register("staff_shirt",
            () -> new ArmorItem(ShirtMaterial.STAFF, ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> BUILDER_SHIRT = ITEMS.register("builder_shirt",
            () -> new ArmorItem(ShirtMaterial.BUILDER, ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> FOUNDER_SHIRT = ITEMS.register("founder_shirt",
            () -> new ArmorItem(ShirtMaterial.FOUNDER, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> FOUNDER_SWORD = ITEMS.register("founder_sword",
            () -> new FounderSword(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
}
