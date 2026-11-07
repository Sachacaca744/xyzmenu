package fr.uarmor;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Uarmor.MODID);

    private static Item.Properties props() {
        return new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC);
    }

    public static final RegistryObject<Item> HELMET = ITEMS.register("admin_helmet",
            () -> new ArmorItem(AdminMaterial.ADMIN, ArmorItem.Type.HELMET, props()));
    public static final RegistryObject<Item> CHESTPLATE = ITEMS.register("admin_chestplate",
            () -> new ArmorItem(AdminMaterial.ADMIN, ArmorItem.Type.CHESTPLATE, props()));
    public static final RegistryObject<Item> LEGGINGS = ITEMS.register("admin_leggings",
            () -> new ArmorItem(AdminMaterial.ADMIN, ArmorItem.Type.LEGGINGS, props()));
    public static final RegistryObject<Item> BOOTS = ITEMS.register("admin_boots",
            () -> new ArmorItem(AdminMaterial.ADMIN, ArmorItem.Type.BOOTS, props()));
    public static final RegistryObject<Item> RIOT_SHIELD = ITEMS.register("riot_shield",
            () -> new RiotShield(props().durability(10000)));
}
