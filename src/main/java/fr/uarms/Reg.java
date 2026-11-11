package fr.uarms;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class Reg {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Uarms.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Uarms.MODID);

    public static final RegistryObject<EntityType<Soldier>> SOLDIER = ENTITIES.register("soldier",
            () -> EntityType.Builder.<Soldier>of(Soldier::new, MobCategory.MONSTER).sized(0.6f, 1.95f).clientTrackingRange(10).build("soldier"));

    // munitions
    public static final RegistryObject<Item> BULLET = ITEMS.register("bullet", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SHELL = ITEMS.register("shell", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> RUBBER = ITEMS.register("rubber_ball", () -> new Item(new Item.Properties()));

    // armes blanches
    public static final RegistryObject<Item> KNIFE = ITEMS.register("knife",
            () -> new KnifeItem(false, new Item.Properties().stacksTo(1).durability(250)));
    public static final RegistryObject<Item> WAR_KNIFE = ITEMS.register("war_knife",
            () -> new KnifeItem(true, new Item.Properties().stacksTo(1).durability(1000).rarity(Rarity.RARE)));

    // armes a feu
    public static final RegistryObject<Item> PISTOL = gun("pistol", 6f, 8, 50, 1, 0.01f, false, BULLET, false, 0f, null,
            SoundEvents.FIREWORK_ROCKET_BLAST, 1.6f, "Pistolet semi-automatique.");
    public static final RegistryObject<Item> SMG = gun("smg", 3.5f, 2, 40, 1, 0.04f, true, BULLET, false, 0f, null,
            SoundEvents.FIREWORK_ROCKET_BLAST, 1.9f, "Pistolet-mitrailleur (tir continu : maintiens le clic droit).");
    public static final RegistryObject<Item> RIFLE = gun("rifle", 5f, 3, 80, 1, 0.02f, true, BULLET, false, 0f, null,
            SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.5f, "Fusil d'assaut (tir continu : maintiens le clic droit).");
    public static final RegistryObject<Item> SHOTGUN = gun("shotgun", 3f, 25, 25, 8, 0.09f, false, SHELL, false, 0f, null,
            SoundEvents.GENERIC_EXPLODE, 1.8f, "Fusil à pompe : 8 plombs par tir.");
    public static final RegistryObject<Item> SNIPER = gun("sniper", 20f, 40, 150, 1, 0.0f, false, BULLET, false, 0f, null,
            SoundEvents.GENERIC_EXPLODE, 2.0f, "Fusil de précision. Accroupis-toi pour zoomer.");

    // non letal
    public static final RegistryObject<Item> FLASHBALL = gun("flashball", 4f, 40, 30, 1, 0.02f, false, RUBBER, true, 1.3f,
            e -> {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
                e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0));
            }, SoundEvents.CROSSBOW_SHOOT, 0.6f, "Lanceur de balles de défense : sonne et repousse.");
    public static final RegistryObject<Item> GOMME_COGNE = gun("gomme_cogne", 2f, 12, 30, 1, 0.03f, false, RUBBER, true, 0.5f,
            e -> {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
            }, SoundEvents.CROSSBOW_SHOOT, 0.9f, "Lanceur de balles en caoutchouc.");

    public static final RegistryObject<Item> TASER = ITEMS.register("taser",
            () -> new TaserItem(new Item.Properties().stacksTo(1).durability(150).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> PEPPER = ITEMS.register("pepper_spray",
            () -> new SprayItem(new Item.Properties().stacksTo(1).durability(40)));
    public static final RegistryObject<Item> BATON = ITEMS.register("baton",
            () -> new BatonItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SHIELD = ITEMS.register("ballistic_shield",
            () -> new ShieldItem(new Item.Properties().stacksTo(1).durability(2500).rarity(Rarity.RARE)));

    public static final RegistryObject<Item> SOLDIER_EGG = ITEMS.register("soldier_spawn_egg",
            () -> new ForgeSpawnEggItem(SOLDIER, 0x4b5320, 0x1a1a1a, new Item.Properties()));

    private static RegistryObject<Item> gun(String id, float dmg, int cd, float range, int pellets, float spread, boolean auto,
                                            RegistryObject<Item> ammo, boolean nonLethal, float knock,
                                            java.util.function.Consumer<net.minecraft.world.entity.LivingEntity> onHit,
                                            net.minecraft.sounds.SoundEvent snd, float pitch, String desc) {
        return ITEMS.register(id, () -> new GunItem(new GunItem.Spec(dmg, cd, range, pellets, spread, auto, ammo::get,
                nonLethal, knock, onHit, snd, pitch, desc), new Item.Properties().stacksTo(1)));
    }
}
