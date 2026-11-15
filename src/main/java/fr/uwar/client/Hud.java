package fr.uwar.client;

import fr.uwar.Aircraft;
import fr.uwar.W;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Affichage tete haute du pilote. */
public class Hud {
    static final int GREEN = 0xFF58FF7A, AMBER = 0xFFFFC040, RED = 0xFFFF4545, DIM = 0x9958FF7A, BG = 0x66000000;
    private static final String[] CARD = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};

    /** Projette un point monde a l'ecran. null si derriere la camera. */
    static int[] project(Vec3 p, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 rel = p.subtract(cam.getPosition());
        Vector3f look = cam.getLookVector(), up = cam.getUpVector(), left = cam.getLeftVector();
        double z = rel.x * look.x + rel.y * look.y + rel.z * look.z;
        if (z < 0.5) return null;
        double x = rel.x * left.x + rel.y * left.y + rel.z * left.z;
        double y = rel.x * up.x + rel.y * up.y + rel.z * up.z;
        double th = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        double asp = (double) w / h;
        int sx = (int) (w / 2.0 - x / (z * th * asp) * (w / 2.0));
        int sy = (int) (h / 2.0 - y / (z * th) * (h / 2.0));
        return new int[]{sx, sy};
    }

    static void bar(GuiGraphics g, int x, int y, int w, int h, float k, int col) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xAA000000);
        g.fill(x, y, x + w, y + h, 0x55000000);
        g.fill(x, y, x + (int) (w * Mth.clamp(k, 0f, 1f)), y + h, col);
    }

    static void box(GuiGraphics g, int cx, int cy, int r, int c) {
        int l = r / 2;
        for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) {
            g.fill(cx + sx * r - (sx > 0 ? l : 0), cy + sy * r, cx + sx * r + (sx > 0 ? 1 : l), cy + sy * r + 1, c);
            g.fill(cx + sx * r, cy + sy * r - (sy > 0 ? l : 0), cx + sx * r + 1, cy + sy * r + (sy > 0 ? 1 : l), c);
        }
    }

    public static void render(GuiGraphics g, int w, int h, float pt) {
        Minecraft mc = Minecraft.getInstance();
        Aircraft a = ClientHooks.mine();
        if (a == null || mc.level == null || mc.player == null) return;
        Font f = mc.font;
        long t = mc.level.getGameTime();
        boolean blink = (t / 8) % 2 == 0;
        int cx = w / 2, cy = h / 2;

        // viseur central
        g.fill(cx - 12, cy, cx - 4, cy + 1, GREEN);
        g.fill(cx + 4, cy, cx + 12, cy + 1, GREEN);
        g.fill(cx, cy - 12, cx + 1, cy - 4, GREEN);
        g.fill(cx, cy + 4, cx + 1, cy + 12, GREEN);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, GREEN);

        // marqueur de nez (avion) : ou pointe reellement l'appareil
        Vec3 eye = a.position().add(0, a.kind.h * 0.6, 0);
        if (!a.kind.heli()) {
            int[] n = project(eye.add(a.aimDir().scale(120)), w, h);
            if (n != null) {
                g.fill(n[0] - 6, n[1], n[0] - 2, n[1] + 1, AMBER);
                g.fill(n[0] + 3, n[1], n[0] + 7, n[1] + 1, AMBER);
                g.fill(n[0], n[1] - 6, n[0] + 1, n[1] - 2, AMBER);
                g.fill(n[0] - 1, n[1] - 1, n[0] + 2, n[1] + 2, AMBER);
            }
        }

        // cap
        float yaw = Mth.wrapDegrees(a.getYRot() + 180f);
        if (yaw < 0) yaw += 360f;
        String hdg = String.format("%03d %s", Math.round(yaw) % 360, CARD[Math.floorMod(Math.round(yaw / 45f), 8)]);
        g.drawCenteredString(f, hdg, cx, 14, GREEN);
        g.fill(cx - 40, 24, cx + 41, 25, DIM);
        g.fill(cx, 24, cx + 1, 29, GREEN);

        // vitesse / altitude
        Vec3 v = a.getDeltaMovement();
        double kmh = a.vel.length() * 72.0;
        if (a.isControlledByLocalInstance()) kmh = v.length() * 72.0;
        int x0 = 16, y0 = h / 2 - 34;
        g.fill(x0 - 4, y0 - 4, x0 + 104, y0 + 70, BG);
        g.drawString(f, "VIT  " + Math.round(kmh) + " km/h", x0, y0, GREEN, false);
        double agl = agl(a);
        g.drawString(f, "ALT  " + Math.round(a.getY()), x0, y0 + 12, GREEN, false);
        g.drawString(f, "SOL  " + (agl < 0 ? "--" : Math.round(agl)), x0, y0 + 24, agl >= 0 && agl < 12 && !a.grounded() && v.y < -0.2 ? RED : GREEN, false);
        g.drawString(f, a.kind.heli() ? "COLL " : "GAZ  ", x0, y0 + 36, GREEN, false);
        bar(g, x0 + 36, y0 + 37, 62, 6, a.kind.heli() ? a.thr() : a.thrust, GREEN);
        float fk = a.fuel() / a.kind.fuelCap, hk = a.hp() / a.kind.maxHp;
        g.drawString(f, "CARB", x0, y0 + 48, fk < 0.2f ? RED : GREEN, false);
        bar(g, x0 + 36, y0 + 49, 62, 6, fk, fk < 0.2f ? RED : fk < 0.4f ? AMBER : GREEN);
        g.drawString(f, "COQUE", x0, y0 + 60, hk < 0.3f ? RED : GREEN, false);
        bar(g, x0 + 36, y0 + 61, 62, 6, hk, hk < 0.3f ? RED : hk < 0.6f ? AMBER : GREEN);

        // armes
        int ax = w - 150, ay = h / 2 - 34;
        g.fill(ax - 6, ay - 4, w - 12, ay + 14 + a.kind.weapons.length * 12, BG);
        g.drawString(f, "ARMEMENT", ax, ay, DIM, false);
        for (int i = 0; i < a.kind.weapons.length; i++) {
            W wp = a.kind.weapons[i];
            boolean sel = i == a.selIndex();
            int am = a.ammo(wp);
            int col = sel ? (am > 0 ? GREEN : RED) : DIM;
            g.drawString(f, (sel ? "> " : "  ") + wp.label, ax, ay + 12 + i * 12, col, false);
            String s = am + "/" + a.cap(wp);
            g.drawString(f, s, w - 18 - f.width(s), ay + 12 + i * 12, col, false);
        }

        // verrouillage missile
        if (a.selected() == W.MISSILE) {
            Entity tg = mc.level.getEntity(a.lockId());
            if (tg != null) {
                int[] s = project(tg.getBoundingBox().getCenter(), w, h);
                if (s != null) {
                    int c = blink ? RED : AMBER;
                    box(g, s[0], s[1], 14, c);
                    g.drawString(f, "VERROUILLE " + Math.round(tg.distanceTo(a)) + " m", s[0] + 18, s[1] - 4, c, false);
                }
                g.drawCenteredString(f, "CIBLE VERROUILLEE", cx, h - 74, blink ? RED : AMBER);
            } else {
                g.drawCenteredString(f, "RECHERCHE DE CIBLE...", cx, h - 74, DIM);
            }
        }

        // alertes
        int wy = 40;
        if (!a.kind.heli() && !a.grounded() && a.speedF < 0.45f && a.engineOn() == a.engineOn() && a.fuel() > 0) {
            if (blink) g.drawCenteredString(f, "DECROCHAGE", cx, wy, RED);
            wy += 12;
        }
        if (a.fuel() <= 0) {
            g.drawCenteredString(f, "PLUS DE CARBURANT", cx, wy, RED);
            wy += 12;
        } else if (fk < 0.2f) {
            if (blink) g.drawCenteredString(f, "CARBURANT FAIBLE", cx, wy, AMBER);
            wy += 12;
        }
        if (hk < 0.3f) {
            if (blink) g.drawCenteredString(f, "COQUE CRITIQUE", cx, wy, RED);
            wy += 12;
        }
        if (a.ammo(a.selected()) <= 0) g.drawCenteredString(f, "MUNITIONS VIDES - R : RECHARGER", cx, wy, AMBER);

        String help = a.kind.heli()
                ? "ZQSD/WASD vol  Espace monter  Ctrl descendre  Clic gauche tirer  G arme  R recharger  Maj sortir"
                : "W/S gaz  Souris cap  Espace postcombustion  Ctrl frein  Clic gauche tirer  G arme  R recharger  Maj sortir";
        g.drawCenteredString(f, help, cx, h - 12, 0x8858FF7A);
    }

    static double agl(Aircraft a) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 p = a.position();
        var r = mc.level.clip(new ClipContext(p, p.add(0, -400, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, a));
        return r.getType() == HitResult.Type.MISS ? -1 : p.y - r.getLocation().y;
    }
}
