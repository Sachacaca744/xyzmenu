package fr.xyzmenu;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ClientHandler {
    private static boolean pendingOpen = false;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("xyz").executes(ctx -> {
                    // On ouvre l'écran au prochain tick : sinon le chat le referme juste après la commande.
                    pendingOpen = true;
                    return 1;
                }));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && pendingOpen) {
            pendingOpen = false;
            Minecraft.getInstance().setScreen(new XyzScreen());
        }
    }
}
