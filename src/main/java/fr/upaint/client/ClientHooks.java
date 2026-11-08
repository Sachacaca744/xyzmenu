package fr.upaint.client;

import net.minecraft.client.Minecraft;

public class ClientHooks {
    public static void openBrush(String tex, int color) {
        Minecraft.getInstance().setScreen(new BrushScreen(tex, color));
    }
}
