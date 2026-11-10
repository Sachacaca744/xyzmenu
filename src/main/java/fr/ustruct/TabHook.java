package fr.ustruct;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Ustruct.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class TabHook {
    public static final ResourceKey<CreativeModeTab> CAILLOUX =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation("utab", "main"));

    @SubscribeEvent
    public static void onTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CAILLOUX)) return;
        event.accept(new ItemStack(Reg.WAND.get()));
        event.accept(new ItemStack(Reg.TABLET.get()));
    }
}
