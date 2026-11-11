package fr.uarms;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.item.Item;

@Mod.EventBusSubscriber(modid = Uarms.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class TabHook {
    public static final ResourceKey<CreativeModeTab> CAILLOUX =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation("utab", "main"));

    @SubscribeEvent
    public static void onTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CAILLOUX)) return;
        for (RegistryObject<Item> o : new RegistryObject[]{Reg.KNIFE, Reg.WAR_KNIFE, Reg.PISTOL, Reg.SMG, Reg.RIFLE, Reg.SHOTGUN,
                Reg.SNIPER, Reg.TASER, Reg.SHIELD, Reg.FLASHBALL, Reg.GOMME_COGNE, Reg.PEPPER, Reg.BATON,
                Reg.BULLET, Reg.SHELL, Reg.RUBBER, Reg.SOLDIER_EGG}) {
            event.accept(new ItemStack(o.get()));
        }
    }

    @SubscribeEvent
    public static void attrs(net.minecraftforge.event.entity.EntityAttributeCreationEvent e) {
        e.put(Reg.SOLDIER.get(), Soldier.attrs().build());
    }
}
