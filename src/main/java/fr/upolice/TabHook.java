package fr.upolice;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = Upolice.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class TabHook {
    public static final ResourceKey<CreativeModeTab> CAILLOUX =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation("utab", "main"));

    @SubscribeEvent
    public static void onTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CAILLOUX)) return;
        for (RegistryObject<Item> o : new RegistryObject[]{Reg.GAS_SPRAY, Reg.GRENADE_TEAR, Reg.GRENADE_FLASH, Reg.GRENADE_BURST,
                Reg.GRENADE_SMOKE, Reg.LBD, Reg.PAINTBALL, Reg.WATER_CANNON, Reg.NET_GUN, Reg.CUFFS}) {
            event.accept(new ItemStack(o.get()));
        }
    }
}
