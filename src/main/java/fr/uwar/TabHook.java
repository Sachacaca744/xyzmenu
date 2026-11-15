package fr.uwar;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Tout le contenu du mod dans UN seul onglet creatif : l'onglet commun utab:main. */
@Mod.EventBusSubscriber(modid = Uwar.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class TabHook {
    public static final ResourceKey<CreativeModeTab> TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation("utab", "main"));

    @SubscribeEvent
    public static void onTab(BuildCreativeModeTabContentsEvent e) {
        if (!e.getTabKey().equals(TAB)) return;
        for (Kind k : Kind.values()) {
            e.accept(Aircraft.makeStack(k, false));
            e.accept(Aircraft.makeStack(k, true));
        }
        for (W w : W.values()) e.accept(new ItemStack(Reg.AMMO.get(w).get()));
        e.accept(new ItemStack(Reg.FUEL_CAN.get()));
        e.accept(new ItemStack(Reg.REPAIR_KIT.get()));
    }
}
