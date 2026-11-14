package fr.udrone.client;

import fr.udrone.Drone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Random;

/** Interface de telemetrie FPV. */
public class Hud {
    static final int GREEN = 0xFF58FF7A, AMBER = 0xFFFFC040, RED = 0xFFFF4545, DIM = 0xAA58FF7A;
    private static final Random RND = new Random();
    private static final String[] CARD = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};

    public static void render(GuiGraphics g, int w, int h, float pt) {
        Drone d = ClientCtl.drone();
        Minecraft mc = Minecraft.getInstance();
        if (d == null || mc.player == null || mc.level == null) return;
        Font f = mc.font;
        long t = mc.level.getGameTime();
        boolean blink = (t / 10) % 2 == 0;
        float sig = d.signal();

        // effet camera : lignes de balayage + vignette
        for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, 0x16000000);
        g.fillGradient(0, 0, w, h / 7, 0x77000000, 0x00000000);
        g.fillGradient(0, h - h / 7, w, h, 0x00000000, 0x77000000);
        g.fill(0, 0, w / 28, h, 0x44000000);
        g.fill(w - w / 28, 0, w, h, 0x44000000);
        // bruit de transmission quand le signal faiblit
        if (sig < 0.5f) {
            float k = (0.5f - sig) * 2f;
            int n = (int) (k * 220);
            for (int i = 0; i < n; i++) {
                int x = RND.nextInt(w), y = RND.nextInt(h), ww = 4 + RND.nextInt(40);
                g.fill(x, y, Math.min(w, x + ww), y + 1 + RND.nextInt(2), ((int) (30 + 80 * k) << 24) | 0xDDDDDD);
            }
            if (sig < 0.12f) g.fill(0, 0, w, h, 0x55000000);
        }

        // viseur
        int cx = w / 2, cy = h / 2;
        int c = d.kind.weapons.length > 0 ? GREEN : DIM;
        g.fill(cx - 14, cy, cx - 4, cy + 1, c);
        g.fill(cx + 4, cy, cx + 14, cy + 1, c);
        g.fill(cx, cy - 14, cx + 1, cy - 4, c);
        g.fill(cx, cy + 4, cx + 1, cy + 14, c);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, c);
        // coins de cadre
        int m = 18, L = 22;
        for (int[] q : new int[][]{{m, m, 1, 1}, {w - m, m, -1, 1}, {m, h - m, 1, -1}, {w - m, h - m, -1, -1}}) {
            g.fill(q[0], q[1], q[0] + q[2] * L, q[1] + q[3], DIM);
            g.fill(q[0], q[1], q[0] + q[2], q[1] + q[3] * L, DIM);
        }

        // compas
        float yaw = Mth.wrapDegrees(d.getViewYRot(pt));
        float heading = Mth.wrapDegrees(yaw + 180f);
        if (heading < 0) heading += 360f;
        g.fill(cx - 90, 14, cx + 90, 15, DIM);
        for (int off = -60; off <= 60; off += 5) {
            float a = heading + off;
            float an = ((a % 360) + 360) % 360;
            int px = cx + off * 3;
            if (Math.round(an) % 45 == 0 && off % 5 == 0 && Math.abs(an % 45) < 0.5f) {
                g.fill(px, 15, px + 1, 22, GREEN);
                g.drawCenteredString(f, CARD[(int) Math.round(an / 45) % 8], px, 24, GREEN);
            } else if (Math.round(an) % 15 == 0) g.fill(px, 15, px + 1, 19, DIM);
        }
        g.fill(cx, 10, cx + 1, 14, AMBER);
        g.drawCenteredString(f, String.format("%03d", Math.round(heading) % 360), cx, 2, GREEN);

        // altitude / vitesse
        double dx = d.getX() - d.xo, dy = d.getY() - d.yo, dz = d.getZ() - d.zo;
        double ms = Math.sqrt(dx * dx + dy * dy + dz * dz) * 20.0;
        int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(d.getX()), (int) Math.floor(d.getZ()));
        int agl = (int) Math.max(0, Math.round(d.getY() - ground));
        g.drawString(f, "VIT " + Math.round(ms * 3.6) + " km/h", 28, h / 2 - 12, GREEN);
        g.drawString(f, "    " + String.format("%.1f", ms) + " m/s", 28, h / 2, DIM);
        g.drawString(f, "ALT " + (int) Math.round(d.getY()) + " m", w - 28 - f.width("ALT 000 m"), h / 2 - 12, GREEN);
        g.drawString(f, "SOL " + agl + " m", w - 28 - f.width("SOL 000 m"), h / 2, DIM);
        double vs = dy * 20;
        g.drawString(f, String.format("V/S %+.1f", vs), w - 28 - f.width("V/S +0.0"), h / 2 + 12, vs < -4 ? AMBER : DIM);

        // haut gauche : etat
        if (blink) g.fill(28, 30, 36, 38, RED);
        g.drawString(f, "REC  " + d.kind.title.toUpperCase(), 42, 30, GREEN);
        g.drawString(f, "ETAT : " + Drone.STATE_NAMES[Mth.clamp(d.state(), 0, 5)], 28, 44, d.state() == Drone.LOST ? RED : GREEN);
        double dist = d.distanceTo(mc.player);
        g.drawString(f, "DIST : " + (int) dist + " m / " + (int) d.range() + " m", 28, 56, GREEN);
        if (ClientCtl.zoom() > 1f) g.drawString(f, "ZOOM x" + (int) ClientCtl.zoom(), 28, 68, AMBER);

        // bas gauche : batterie / signal / structure
        int by = h - 64;
        double secs = d.battery() / 20.0;
        float bp = d.battery() / (float) d.kind.battery;
        g.drawString(f, "BATT", 28, by, GREEN);
        bar(g, 62, by + 1, 90, 7, bp, bp < 0.15f ? RED : bp < 0.35f ? AMBER : GREEN);
        g.drawString(f, Math.round(bp * 100) + "%  " + (int) (secs / 60) + ":" + String.format("%02d", (int) (secs % 60)), 158, by, GREEN);
        g.drawString(f, "SIGN", 28, by + 12, GREEN);
        for (int i = 0; i < 5; i++) {
            boolean on = sig * 5f > i + 0.01f;
            int hh = 3 + i * 2;
            g.fill(62 + i * 6, by + 20 - hh, 62 + i * 6 + 4, by + 20, on ? (sig < 0.3f ? RED : GREEN) : 0x44FFFFFF);
        }
        g.drawString(f, sig <= 0 ? "PERDU" : Math.round(sig * 100) + "%", 98, by + 12, sig < 0.3f ? RED : GREEN);
        float hpp = d.hp() / d.kind.hp;
        g.drawString(f, "PV", 28, by + 24, GREEN);
        bar(g, 62, by + 25, 90, 7, hpp, hpp < 0.3f ? RED : GREEN);

        // bas droite : armement
        int ax = w - 28 - 150, ay = h - 64;
        if (d.kind.weapons.length == 0) {
            g.drawString(f, "AUCUN ARMEMENT", ax, ay + 12, DIM);
        } else {
            for (int i = 0; i < d.kind.weapons.length; i++) {
                boolean sel = i == d.sel();
                int col = sel ? AMBER : DIM;
                g.drawString(f, (sel ? "> " : "  ") + d.kind.weapons[i].title.toUpperCase() + "  x" + d.ammo(i), ax, ay + i * 11 - (d.kind.weapons.length - 1) * 4, col);
            }
            float heat = d.heat() / 100f;
            g.drawString(f, "TEMP", ax, h - 24, GREEN);
            bar(g, ax + 34, h - 23, 90, 7, heat, heat > 0.8f ? RED : heat > 0.5f ? AMBER : GREEN);
        }

        // alertes
        int ay2 = 84;
        if (sig < 0.3f && sig > 0 && blink) alert(g, f, cx, ay2, "SIGNAL FAIBLE");
        else if (bp < 0.12f && blink) alert(g, f, cx, ay2, "BATTERIE CRITIQUE");
        else if (d.state() == Drone.RTB && blink) alert(g, f, cx, ay2, "RETOUR A LA BASE");
        else if (d.heat() > 80f && blink) alert(g, f, cx, ay2, "SURCHAUFFE ARME");
        if (d.battery() <= 0 && d.state() != Drone.LANDED) alert(g, f, cx, ay2 + 14, "PLUS DE PUISSANCE");
    }

    static void alert(GuiGraphics g, Font f, int cx, int y, String s) {
        g.drawCenteredString(f, "! " + s + " !", cx, y, RED);
    }

    public static void bar(GuiGraphics g, int x, int y, int w, int h, float v, int col) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xAA000000);
        g.fill(x, y, x + w, y + h, 0x55222222);
        g.fill(x, y, x + (int) (w * Mth.clamp(v, 0f, 1f)), y + h, col);
    }
}
