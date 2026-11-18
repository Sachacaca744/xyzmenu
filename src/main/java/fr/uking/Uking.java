package fr.uking;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Uking.MODID)
public class Uking {
    public static final String MODID = "uking";

    public Uking() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        Reg.BLOCKS.register(bus);
        Reg.ITEMS.register(bus);
        Reg.ENTITIES.register(bus);
        Net.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Cfg.SPEC);
        if (System.getProperty("uking.selftest") != null) {
            try {
                Class.forName("fr.uking.SelfTest").getMethod("init").invoke(null);
            } catch (Throwable t) {
                System.out.println("SELFTEST: init impossible " + t);
            }
        }
    }
}
