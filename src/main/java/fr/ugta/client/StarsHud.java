package fr.ugta.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Indice de recherche : 5 etoiles en haut a droite. Clignote tant que la police ne te voit pas. */
@Mod.EventBusSubscriber(modid = "ugta", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class StarsHud {
    private static int stars;
    private static boolean seen;

    public static void set(int s, boolean sn) {
        stars = s;
        seen = sn;
    }

    private static final String[] STAR = {
            "....#....",
            "....#....",
            "...###...",
            "#########",
            ".#######.",
            "..#####..",
            "..##.##..",
            ".##...##.",
            ".#.....#."};

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("ugta_stars", (gui, g, pt, w, h) -> draw(g, w));
    }

    private static void draw(GuiGraphics g, int w) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        int sc = 2, gap = 4, sw = 9 * sc;
        int total = 5 * sw + 4 * gap;
        int x0 = w - total - 10, y0 = 10;
        long t = mc.level == null ? 0 : mc.level.getGameTime();
        boolean flashOn = (t / 8) % 2 == 0;
        if (stars > 0) g.drawString(mc.font, "RECHERCHÉ", x0 + total - mc.font.width("RECHERCHÉ"), y0 + 9 * sc + 3,
                seen ? 0xFFFF5555 : (flashOn ? 0xFFFFD700 : 0xFF886600), true);
        for (int i = 0; i < 5; i++) {
            boolean on = i < stars;
            int color;
            if (!on) color = 0xA0404040;
            else if (seen) color = 0xFFFFD700;
            else color = flashOn ? 0xFFFFD700 : 0x60A08000;
            int sx = x0 + i * (sw + gap);
            for (int y = 0; y < 9; y++)
                for (int x = 0; x < 9; x++)
                    if (STAR[y].charAt(x) == '#') g.fill(sx + x * sc, y0 + y * sc, sx + x * sc + sc, y0 + y * sc + sc, color);
        }
    }
}
