package fr.upolice;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class Reg {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Upolice.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Upolice.MODID);

    public static final RegistryObject<EntityType<GrenadeEntity>> GRENADE = ENTITIES.register("grenade",
            () -> EntityType.Builder.<GrenadeEntity>of(GrenadeEntity::new, MobCategory.MISC).sized(0.25f, 0.25f)
                    .clientTrackingRange(6).updateInterval(5).build("grenade"));

    private static Item.Properties p() {
        return new Item.Properties().stacksTo(1);
    }

    // 1-5 : gaz et grenades
    public static final RegistryObject<Item> GAS_SPRAY = ITEMS.register("tear_gas_spray", () -> new GasSpray(p().durability(40)));
    public static final RegistryObject<Item> GRENADE_TEAR = ITEMS.register("tear_gas_grenade",
            () -> new GrenadeItem(GrenadeItem.Kind.TEAR, new Item.Properties().stacksTo(8)));
    public static final RegistryObject<Item> GRENADE_FLASH = ITEMS.register("flashbang",
            () -> new GrenadeItem(GrenadeItem.Kind.FLASH, new Item.Properties().stacksTo(8)));
    public static final RegistryObject<Item> GRENADE_BURST = ITEMS.register("dispersal_grenade",
            () -> new GrenadeItem(GrenadeItem.Kind.BURST, new Item.Properties().stacksTo(8)));
    public static final RegistryObject<Item> GRENADE_SMOKE = ITEMS.register("smoke_grenade",
            () -> new GrenadeItem(GrenadeItem.Kind.SMOKE, new Item.Properties().stacksTo(8)));

    // 6-10 : armes
    public static final RegistryObject<Item> LBD = ITEMS.register("lbd40", () -> new ShotItem(new ShotItem.Spec(
            4f, 40, 35, 1.0f, e -> {
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 40, 0));
    }, SoundEvents.CROSSBOW_SHOOT, 0.6f, false, "Lanceur de balles de défense : impact sonnant."), p().durability(250).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> PAINTBALL = ITEMS.register("paint_marker", () -> new ShotItem(new ShotItem.Spec(
            1f, 6, 40, 0.1f, e -> {
        e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0));
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0));
    }, SoundEvents.SLIME_BLOCK_HIT, 1.8f, true, "Marqueur de peinture : la cible brille 30 s."), p().durability(400)));
    public static final RegistryObject<Item> WATER_CANNON = ITEMS.register("water_cannon",
            () -> new WaterCannon(p().durability(300)));
    public static final RegistryObject<Item> NET_GUN = ITEMS.register("net_launcher",
            () -> new NetGun(p().durability(25)));
    public static final RegistryObject<Item> CUFFS = ITEMS.register("handcuffs",
            () -> new Cuffs(p().durability(40)));
}
