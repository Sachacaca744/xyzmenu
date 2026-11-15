package fr.uwar;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;

@Mod(Uwar.MODID)
public class Uwar {
    public static final String MODID = "uwar";

    public Uwar() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        Reg.ITEMS.register(bus);
        Reg.ENTITIES.register(bus);
        Reg.SOUNDS.register(bus);
        Net.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Cfg.SPEC);
        if (System.getProperty("uwar.selftest") != null) {
            try {
                Class.forName("fr.uwar.SelfTest").getMethod("init").invoke(null);
            } catch (Throwable t) {
                System.out.println("SELFTEST: init impossible " + t);
            }
        }
    }
}
