package fr.utaxi;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Range la cabine dans l'onglet commun "Cailloux" (mod utab). Sans utab, l'objet reste dispo via /taxi give. */
@Mod.EventBusSubscriber(modid = Utaxi.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class TabHook {
    public static final ResourceKey<CreativeModeTab> CAILLOUX =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation("utab", "main"));

    @SubscribeEvent
    public static void onTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CAILLOUX)) {
            event.accept(new ItemStack(Reg.BOOTH_ITEM.get()));
        }
    }
}
