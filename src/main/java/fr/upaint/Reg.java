package fr.upaint;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class Reg {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Upaint.MODID);

    private static Item.Properties p() {
        return new Item.Properties().stacksTo(1).rarity(Rarity.RARE);
    }

    public static final RegistryObject<Item> COMPASS_SUN = ITEMS.register("compass_sun",
            () -> new WeatherCompass(WeatherCompass.Kind.SUN, p()));
    public static final RegistryObject<Item> COMPASS_RAIN = ITEMS.register("compass_rain",
            () -> new WeatherCompass(WeatherCompass.Kind.RAIN, p()));
    public static final RegistryObject<Item> COMPASS_STORM = ITEMS.register("compass_storm",
            () -> new WeatherCompass(WeatherCompass.Kind.STORM, p()));

    public static final RegistryObject<Item> BRUSH = ITEMS.register("brush",
            () -> new PaintToolItem(PaintToolItem.Kind.BRUSH, p()));
    public static final RegistryObject<Item> ROLLER = ITEMS.register("roller",
            () -> new PaintToolItem(PaintToolItem.Kind.ROLLER, p()));
    public static final RegistryObject<Item> BUCKET = ITEMS.register("bucket",
            () -> new PaintToolItem(PaintToolItem.Kind.BUCKET, p()));
    public static final RegistryObject<Item> ERASER = ITEMS.register("eraser",
            () -> new PaintToolItem(PaintToolItem.Kind.ERASER, p()));
    public static final RegistryObject<Item> PIPETTE = ITEMS.register("pipette",
            () -> new PaintToolItem(PaintToolItem.Kind.PIPETTE, p()));
}
