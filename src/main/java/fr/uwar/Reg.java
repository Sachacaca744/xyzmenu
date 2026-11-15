package fr.uwar;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public class Reg {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Uwar.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Uwar.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Uwar.MODID);

    private static RegistryObject<SoundEvent> snd(String n) {
        return SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Uwar.MODID, n)));
    }

    public static final RegistryObject<SoundEvent> GUN = snd("gun");
    public static final RegistryObject<SoundEvent> BOOM = snd("boom");
    public static final RegistryObject<SoundEvent> LAUNCH = snd("launch");
    public static final RegistryObject<SoundEvent> BEEP = snd("beep");
    public static final RegistryObject<SoundEvent> WARN = snd("warn");
    public static final RegistryObject<SoundEvent> HELI_LOOP = snd("heli_loop");
    public static final RegistryObject<SoundEvent> JET_LOOP = snd("jet_loop");

    public static final Map<Kind, RegistryObject<EntityType<Aircraft>>> AIRCRAFTS = new EnumMap<>(Kind.class);
    public static final Map<Kind, RegistryObject<Item>> AIRCRAFT_ITEMS = new EnumMap<>(Kind.class);
    public static final Map<W, RegistryObject<Item>> AMMO = new EnumMap<>(W.class);

    static {
        for (Kind k : Kind.values()) {
            AIRCRAFTS.put(k, ENTITIES.register(k.id, () -> EntityType.Builder.<Aircraft>of((t, l) -> new Aircraft(t, l, k), MobCategory.MISC)
                    .sized(k.w, k.h).clientTrackingRange(16).updateInterval(1).fireImmune().build(k.id)));
            AIRCRAFT_ITEMS.put(k, ITEMS.register(k.id, () -> new AircraftItem(k, new Item.Properties().stacksTo(1))));
        }
        for (W w : W.values()) {
            AMMO.put(w, ITEMS.register(w.item, () -> new SupplyItem(SupplyItem.Type.AMMO, w, new Item.Properties().stacksTo(w == W.CANNON ? 64 : 16))));
        }
    }

    public static final RegistryObject<Item> FUEL_CAN = ITEMS.register("fuel_can",
            () -> new SupplyItem(SupplyItem.Type.FUEL, null, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> REPAIR_KIT = ITEMS.register("repair_kit",
            () -> new SupplyItem(SupplyItem.Type.REPAIR, null, new Item.Properties().stacksTo(16)));

    public static final RegistryObject<EntityType<Shell>> SHELL = ENTITIES.register("shell",
            () -> EntityType.Builder.<Shell>of(Shell::new, MobCategory.MISC).sized(0.3f, 0.3f)
                    .clientTrackingRange(10).updateInterval(1).build("shell"));
}
