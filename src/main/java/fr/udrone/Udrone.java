package fr.udrone;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Udrone.MODID)
public class Udrone {
    public static final String MODID = "udrone";

    public Udrone() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        Reg.ITEMS.register(bus);
        Reg.BLOCKS.register(bus);
        Reg.ENTITIES.register(bus);
        Reg.SOUNDS.register(bus);
        Reg.BES.register(bus);
        Net.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Cfg.SPEC);
        MinecraftForge.EVENT_BUS.register(Pilot.class);
    }
}
