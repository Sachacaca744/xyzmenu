package fr.uboards;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Uboards.MODID)
public class Uboards {
    public static final String MODID = "uboards";

    public Uboards() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        Reg.BLOCKS.register(bus);
        Reg.ITEMS.register(bus);
        Reg.BES.register(bus);
        Reg.TABS.register(bus);
        Net.register();
        MinecraftForge.EVENT_BUS.register(Cmds.class);
    }
}
