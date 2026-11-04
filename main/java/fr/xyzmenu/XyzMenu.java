package fr.xyzmenu;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(XyzMenu.MODID)
public class XyzMenu {
    public static final String MODID = "xyzmenu";

    public XyzMenu() {
        // Mod client uniquement : n'empêche pas de rejoindre un serveur sans le mod.
        ModLoadingContext.get().registerExtensionPoint(
                IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(
                        () -> IExtensionPoint.DisplayTest.IGNORESERVERONLY,
                        (remote, isServer) -> true));

        if (FMLEnvironment.dist == Dist.CLIENT) {
            CommandStore.load();
            MinecraftForge.EVENT_BUS.register(ClientHandler.class);
        }
    }
}
