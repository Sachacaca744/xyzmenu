package fr.uwarp.client;

import fr.uwarp.Cfg;
import net.minecraft.client.Minecraft;

public class Hooks {
    public static void openWarp(boolean shop, boolean world, boolean spawn) {
        Minecraft.getInstance().setScreen(new WarpScreen(new boolean[]{shop, world, spawn, true}));
    }

    public static void openAdmin(String json) {
        Minecraft.getInstance().setScreen(new AdminScreen(Cfg.parse(json)));
    }
}
