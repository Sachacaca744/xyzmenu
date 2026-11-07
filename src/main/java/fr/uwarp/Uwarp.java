package fr.uwarp;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

@Mod(Uwarp.MODID)
public class Uwarp {
    public static final String MODID = "uwarp";

    public Uwarp() {
        Net.register();
        MinecraftForge.EVENT_BUS.register(ServerEvents.class);
    }
}
