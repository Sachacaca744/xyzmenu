package fr.utaxi.client;

import net.minecraft.client.Minecraft;

import java.util.List;

public class ClientHooks {
    public static void openTaxi(List<String> names) {
        Minecraft.getInstance().setScreen(new TaxiScreen(names));
    }
}
