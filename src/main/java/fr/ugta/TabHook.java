package fr.ugta;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Ugta.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class TabHook {
    public static final ResourceKey<CreativeModeTab> CAILLOUX =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation("utab", "main"));

    @SubscribeEvent
    public static void onTab(BuildCreativeModeTabContentsEvent e) {
        if (!e.getTabKey().equals(CAILLOUX)) return;
        for (var o : Reg.VEH_ITEMS.values()) e.accept(new ItemStack(o.get()));
        e.accept(new ItemStack(Reg.PISTOL_PROP.get()));
        e.accept(new ItemStack(Reg.RIFLE_PROP.get()));
    }

    @SubscribeEvent
    public static void attrs(EntityAttributeCreationEvent e) {
        e.put(Reg.COP.get(), Cop.attrs().build());
        e.put(Reg.PED.get(), Ped.attrs().build());
    }
}
