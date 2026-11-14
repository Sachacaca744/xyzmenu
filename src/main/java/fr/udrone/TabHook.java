package fr.udrone;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Tout le contenu du mod dans UN seul onglet creatif : l'onglet commun utab:main. */
@Mod.EventBusSubscriber(modid = Udrone.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class TabHook {
    public static final ResourceKey<CreativeModeTab> TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation("utab", "main"));

    @SubscribeEvent
    public static void onTab(BuildCreativeModeTabContentsEvent e) {
        if (!e.getTabKey().equals(TAB)) return;
        for (Kind k : Kind.values()) e.accept(Drone.makeStack(k, null));
        e.accept(new ItemStack(Reg.CONTROLLER.get()));
        e.accept(new ItemStack(Reg.STATION_ITEM.get()));
        e.accept(new ItemStack(Reg.PAD_ITEM.get()));
    }
}
