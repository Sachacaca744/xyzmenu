package fr.udynamax;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Udynamax.MODID)
public class Udynamax {
    public static final String MODID = "udynamax";

    public Udynamax() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        Reg.BLOCKS.register(bus);
        Reg.ITEMS.register(bus);
        Net.register();
        MinecraftForge.EVENT_BUS.register(Events.class);
    }
}
