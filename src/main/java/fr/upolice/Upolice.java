package fr.upolice;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Upolice.MODID)
public class Upolice {
    public static final String MODID = "upolice";

    public Upolice() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        Reg.ITEMS.register(bus);
        Reg.ENTITIES.register(bus);
    }
}
