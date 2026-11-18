package fr.uking;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public class Reg {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Uking.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Uking.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Uking.MODID);

    public static final RegistryObject<Block> FIEF_STONE = BLOCKS.register("fief_stone",
            () -> new FiefStoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1f, 3600000f).sound(SoundType.STONE).lightLevel(s -> 8)));
    public static final RegistryObject<Item> FIEF_STONE_ITEM = ITEMS.register("fief_stone", () -> new BlockItem(FIEF_STONE.get(), new Item.Properties()));

    public static final RegistryObject<Item> PLAN = ITEMS.register("plan", () -> new Gear.Plan(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> STANDARD = ITEMS.register("standard", () -> new Gear.Standard(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> KNIGHT_SWORD = ITEMS.register("knight_sword", () -> new Gear.Reach(Tiers.IRON, 4, -2.3f, 0, false, new Item.Properties()));
    public static final RegistryObject<Item> SPEAR = ITEMS.register("spear", () -> new Gear.Reach(Tiers.IRON, 3, -2.6f, 1.5, false, new Item.Properties()));
    public static final RegistryObject<Item> HALBERD = ITEMS.register("halberd", () -> new Gear.Reach(Tiers.IRON, 6, -3.1f, 1.0, false, new Item.Properties()));
    public static final RegistryObject<Item> MACE = ITEMS.register("mace", () -> new Gear.Reach(Tiers.IRON, 5, -3.0f, 0, true, new Item.Properties()));
    public static final RegistryObject<Item> CROSSBOW = ITEMS.register("crossbow", () -> new Gear.FootCrossbow(new Item.Properties().stacksTo(1).durability(500)));
    public static final RegistryObject<Item> BOLT = ITEMS.register("bolt", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> BOULDER = ITEMS.register("boulder", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CATAPULT_ITEM = ITEMS.register("catapult", () -> new Gear.CatapultItem(new Item.Properties().stacksTo(1)));
    public static final Map<UType, RegistryObject<Item>> RECRUIT = new EnumMap<>(UType.class);

    static {
        for (UType t : UType.values()) {
            if (t == UType.LORD) continue;
            RECRUIT.put(t, ITEMS.register("recruit_" + t.id, () -> new Gear.Recruit(t, new Item.Properties().stacksTo(16))));
        }
    }

    public static final RegistryObject<EntityType<Soldier>> SOLDIER = ENTITIES.register("soldier",
            () -> EntityType.Builder.<Soldier>of(Soldier::new, MobCategory.MISC).sized(0.6f, 1.95f).clientTrackingRange(10).build("soldier"));
    public static final RegistryObject<EntityType<Catapult>> CATAPULT = ENTITIES.register("catapult",
            () -> EntityType.Builder.<Catapult>of(Catapult::new, MobCategory.MISC).sized(2.6f, 2.2f).clientTrackingRange(10).build("catapult"));
    public static final RegistryObject<EntityType<Boulder>> BOULDER_ENTITY = ENTITIES.register("boulder",
            () -> EntityType.Builder.<Boulder>of(Boulder::new, MobCategory.MISC).sized(0.6f, 0.6f).clientTrackingRange(12).updateInterval(2).build("boulder"));

    @Mod.EventBusSubscriber(modid = Uking.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        public static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation("utab", "main"));

        @SubscribeEvent
        public static void attrs(EntityAttributeCreationEvent e) {
            e.put(SOLDIER.get(), Soldier.attrs().build());
            e.put(CATAPULT.get(), Catapult.attrs().build());
        }

        @SubscribeEvent
        public static void tab(BuildCreativeModeTabContentsEvent e) {
            if (!e.getTabKey().equals(TAB)) return;
            for (var i : new RegistryObject[]{FIEF_STONE_ITEM, PLAN, STANDARD, KNIGHT_SWORD, SPEAR, HALBERD, MACE, CROSSBOW, BOLT, BOULDER, CATAPULT_ITEM})
                e.accept(new ItemStack((Item) i.get()));
            for (var r : RECRUIT.values()) e.accept(new ItemStack(r.get()));
        }
    }
}
