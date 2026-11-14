package fr.udrone;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public class Reg {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Udrone.MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Udrone.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Udrone.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Udrone.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Udrone.MODID);

    private static RegistryObject<SoundEvent> snd(String n) {
        return SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Udrone.MODID, n)));
    }

    public static final RegistryObject<SoundEvent> GUN = snd("gun");
    public static final RegistryObject<SoundEvent> BOOM = snd("boom");
    public static final RegistryObject<SoundEvent> LAUNCH = snd("launch");
    public static final RegistryObject<SoundEvent> BEEP = snd("beep");
    public static final RegistryObject<SoundEvent> LOST = snd("lost");
    public static final RegistryObject<SoundEvent> CHARGE = snd("charge");
    public static final RegistryObject<SoundEvent> QUAD_LOOP = snd("quad_loop");
    public static final RegistryObject<SoundEvent> HEAVY_LOOP = snd("heavy_loop");
    public static final RegistryObject<SoundEvent> REAPER_LOOP = snd("reaper_loop");

    public static final Map<Kind, RegistryObject<EntityType<Drone>>> DRONES = new EnumMap<>(Kind.class);
    public static final Map<Kind, RegistryObject<Item>> DRONE_ITEMS = new EnumMap<>(Kind.class);

    static {
        for (Kind k : Kind.values()) {
            DRONES.put(k, ENTITIES.register(k.id, () -> EntityType.Builder.<Drone>of((t, l) -> new Drone(t, l, k), MobCategory.MISC)
                    .sized(k.w, k.h).clientTrackingRange(10).updateInterval(1).fireImmune().build(k.id)));
            DRONE_ITEMS.put(k, ITEMS.register(k.id, () -> new DroneItem(k, new Item.Properties().stacksTo(1))));
        }
    }

    public static final RegistryObject<EntityType<Shell>> SHELL = ENTITIES.register("shell",
            () -> EntityType.Builder.<Shell>of(Shell::new, MobCategory.MISC).sized(0.3f, 0.3f)
                    .clientTrackingRange(8).updateInterval(1).build("shell"));

    public static final RegistryObject<Item> CONTROLLER = ITEMS.register("controller",
            () -> new ControllerItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Block> STATION = BLOCKS.register("control_station",
            () -> new StationBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(3.0f, 6.0f)
                    .sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> PAD = BLOCKS.register("charging_pad",
            () -> new PadBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 6.0f)
                    .sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Item> STATION_ITEM = ITEMS.register("control_station",
            () -> new BlockItem(STATION.get(), new Item.Properties()));
    public static final RegistryObject<Item> PAD_ITEM = ITEMS.register("charging_pad",
            () -> new BlockItem(PAD.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<StationBE>> STATION_BE = BES.register("control_station",
            () -> BlockEntityType.Builder.of(StationBE::new, STATION.get()).build(null));
}
