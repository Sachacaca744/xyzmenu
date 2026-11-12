package fr.ugta;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Ugta.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Ugta.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Ugta.MODID);

    private static RegistryObject<SoundEvent> snd(String n) {
        return SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Ugta.MODID, n)));
    }

    public static final RegistryObject<SoundEvent> ENGINE = snd("engine");
    public static final RegistryObject<SoundEvent> SIREN = snd("siren");
    public static final RegistryObject<SoundEvent> ROTOR = snd("rotor");
    public static final RegistryObject<SoundEvent> HORN = snd("horn");
    public static final RegistryObject<SoundEvent> PROP = snd("prop");
    public static final RegistryObject<SoundEvent> SHOT = snd("shot");

    public static final Map<Kind, RegistryObject<EntityType<Vehicle>>> VEH = new EnumMap<>(Kind.class);
    public static final Map<Kind, RegistryObject<Item>> VEH_ITEMS = new EnumMap<>(Kind.class);

    static {
        for (Kind k : Kind.values()) {
            VEH.put(k, ENTITIES.register(k.id, () -> EntityType.Builder.<Vehicle>of((t, l) -> new Vehicle(t, l, k), MobCategory.MISC)
                    .sized(k.w, k.h).clientTrackingRange(k.cat == 0 ? 10 : 16).updateInterval(1).build(k.id)));
            if (k != Kind.POLICE_HELI)
                VEH_ITEMS.put(k, ITEMS.register(k.id, () -> new VehicleItem(k, new Item.Properties().stacksTo(1))));
        }
    }

    public static final RegistryObject<EntityType<Cop>> COP = ENTITIES.register("cop",
            () -> EntityType.Builder.<Cop>of(Cop::new, MobCategory.MISC).sized(0.6f, 1.8f).clientTrackingRange(10).build("cop"));
    public static final RegistryObject<EntityType<Ped>> PED = ENTITIES.register("ped",
            () -> EntityType.Builder.<Ped>of(Ped::new, MobCategory.MISC).sized(0.6f, 1.8f).clientTrackingRange(8).build("ped"));

    public static final RegistryObject<Item> PISTOL_PROP = ITEMS.register("pistol_prop", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> RIFLE_PROP = ITEMS.register("rifle_prop", () -> new Item(new Item.Properties().stacksTo(1)));

    public static Item vehicleItem(Kind k) {
        var o = VEH_ITEMS.get(k);
        return o == null ? VEH_ITEMS.get(Kind.POLICE).get() : o.get();
    }

    public static Vehicle create(Kind k, ServerLevel lv) {
        return VEH.get(k).get().create(lv);
    }
}
