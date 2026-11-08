package fr.uboards.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public class ClientHooks {
    public static void openEdit(BlockPos pos, boolean remote, boolean screen, String initial) {
        Minecraft.getInstance().setScreen(new EditScreen(pos, remote, screen, initial));
    }
}
