package fr.udynamax.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.udynamax.Net;
import fr.udynamax.Reg;
import fr.udynamax.Udynamax;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public class ClientEvents {
    public static final KeyMapping TOGGLE = new KeyMapping("key.udynamax.map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.udynamax");
    public static final KeyMapping ZOOM = new KeyMapping("key.udynamax.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.udynamax");

    private static final int BX = 8, BY = 8, BW = 120, BH = 22;

    @Mod.EventBusSubscriber(modid = Udynamax.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(TOGGLE);
            e.register(ZOOM);
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("minimap", (gui, g, pt, w, h) -> Minimap.render(g, w));
        }
    }

    @Mod.EventBusSubscriber(modid = Udynamax.MODID, value = Dist.CLIENT)
    public static class ForgeBus {
        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (TOGGLE.consumeClick()) Minimap.enabled = !Minimap.enabled;
            while (ZOOM.consumeClick()) Minimap.zoom = (Minimap.zoom + 1) % 4;
            Minimap.tick(mc);
        }

        private static boolean isBattleScreen(net.minecraft.client.gui.screens.Screen s) {
            return s != null && s.getClass().getName().equals("com.cobblemon.mod.common.client.gui.battle.BattleGUI");
        }

        private static boolean hasBand() {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p == null) return false;
            for (ItemStack s : p.getInventory().items) if (s.is(Reg.BAND.get())) return true;
            for (ItemStack s : p.getInventory().offhand) if (s.is(Reg.BAND.get())) return true;
            return false;
        }

        @SubscribeEvent
        public static void render(ScreenEvent.Render.Post e) {
            if (!isBattleScreen(e.getScreen()) || !hasBand()) return;
            GuiGraphics g = e.getGuiGraphics();
            boolean hover = e.getMouseX() >= BX && e.getMouseX() < BX + BW && e.getMouseY() >= BY && e.getMouseY() < BY + BH;
            g.fill(BX - 1, BY - 1, BX + BW + 1, BY + BH + 1, 0xFFFFFFFF);
            g.fill(BX, BY, BX + BW, BY + BH, hover ? 0xFFE02020 : 0xFFA01010);
            g.drawCenteredString(Minecraft.getInstance().font, "DYNAMAX", BX + BW / 2, BY + 7, 0xFFFFFF);
        }

        @SubscribeEvent
        public static void click(ScreenEvent.MouseButtonPressed.Pre e) {
            if (!isBattleScreen(e.getScreen()) || !hasBand() || e.getButton() != 0) return;
            if (e.getMouseX() >= BX && e.getMouseX() < BX + BW && e.getMouseY() >= BY && e.getMouseY() < BY + BH) {
                Net.CH.sendToServer(new Net.ActivateMsg());
                e.setCanceled(true);
            }
        }
    }
}
