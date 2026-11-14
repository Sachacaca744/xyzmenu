package fr.udrone.client;

import fr.udrone.Net;
import net.minecraft.client.Minecraft;

public class ClientHooks {
    public static void station(Net.StationMsg m) {
        Minecraft mc = Minecraft.getInstance();
        if (m.open()) mc.setScreen(new StationScreen(m.pos(), m.list()));
        else if (mc.screen instanceof StationScreen s && s.pos.equals(m.pos())) s.update(m.list());
    }
}
