package fr.ucore;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;

@Mod(Ucore.MODID)
public class Ucore {
    public static final String MODID = "ucore";

    public Ucore() {
        // Mod serveur uniquement : les joueurs peuvent se connecter sans l'installer.
        ModLoadingContext.get().registerExtensionPoint(
                IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(
                        () -> IExtensionPoint.DisplayTest.IGNORESERVERONLY,
                        (remote, isServer) -> true));

        MinecraftForge.EVENT_BUS.register(Events.class);
    }
}
