package fr.upaint;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Range les objets dans l'onglet commun "Cailloux" (mod utab). */
@Mod.EventBusSubscriber(modid = Upaint.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class TabHook {
    public static final ResourceKey<CreativeModeTab> CAILLOUX =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation("utab", "main"));

    @SubscribeEvent
    public static void onTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CAILLOUX)) return;
        event.accept(new ItemStack(Reg.COMPASS_SUN.get()));
        event.accept(new ItemStack(Reg.COMPASS_RAIN.get()));
        event.accept(new ItemStack(Reg.COMPASS_STORM.get()));
        event.accept(new ItemStack(Reg.BRUSH.get()));
        event.accept(new ItemStack(Reg.ROLLER.get()));
        event.accept(new ItemStack(Reg.BUCKET.get()));
        event.accept(new ItemStack(Reg.ERASER.get()));
        event.accept(new ItemStack(Reg.PIPETTE.get()));
    }
}
