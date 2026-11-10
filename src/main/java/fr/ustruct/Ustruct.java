package fr.ustruct;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Ustruct.MODID)
public class Ustruct {
    public static final String MODID = "ustruct";

    public Ustruct() {
        // Installe le monde sauvegarde (avant que le jeu ne lise les mondes)
        try {
            WorldIO.installOnStartup();
        } catch (Throwable t) {
            System.err.println("[ustruct] installation du monde impossible : " + t);
            t.printStackTrace();
        }
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        Reg.ITEMS.register(bus);
        Net.register();
        MinecraftForge.EVENT_BUS.register(Events.class);
        MinecraftForge.EVENT_BUS.register(Cmds.class);
    }
}
