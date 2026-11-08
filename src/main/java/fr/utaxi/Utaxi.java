package fr.utaxi;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Utaxi.MODID)
public class Utaxi {
    public static final String MODID = "utaxi";

    public Utaxi() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        Reg.BLOCKS.register(bus);
        Reg.ITEMS.register(bus);
        Reg.BES.register(bus);
        Net.register();
        MinecraftForge.EVENT_BUS.register(Events.class);
        MinecraftForge.EVENT_BUS.register(Cmds.class);
    }
}
