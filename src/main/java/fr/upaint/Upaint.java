package fr.upaint;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Upaint.MODID)
public class Upaint {
    public static final String MODID = "upaint";

    public Upaint() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        Reg.ITEMS.register(bus);
        Net.register();
        MinecraftForge.EVENT_BUS.register(Events.class);
    }
}
