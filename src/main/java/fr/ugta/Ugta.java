package fr.ugta;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Ugta.MODID)
public class Ugta {
    public static final String MODID = "ugta";

    public Ugta() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        Reg.ITEMS.register(bus);
        Reg.ENTITIES.register(bus);
        Reg.SOUNDS.register(bus);
        Net.register();
        MinecraftForge.EVENT_BUS.register(Wanted.class);
        MinecraftForge.EVENT_BUS.register(Spawner.class);
        MinecraftForge.EVENT_BUS.register(Cmds.class);
    }
}
