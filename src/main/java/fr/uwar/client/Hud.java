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
        java.util.List<W> ld = a.loaded();
        int ax = w - 178, ay = h / 2 - 34;
        g.fill(ax - 6, ay - 4, w - 12, ay + 14 + ld.size() * 12, BG);
        g.drawString(f, "ARMEMENT  (5 : config)", ax, ay, DIM, false);
        for (int i = 0; i < ld.size(); i++) {
            W wp = ld.get(i);
            boolean sel = wp == a.selected();
            int am = a.ammo(wp);
            int col = sel ? (am > 0 ? GREEN : RED) : DIM;
            g.drawString(f, (sel ? "> " : "  ") + wp.shortName, ax, ay + 12 + i * 12, col, false);
            String s = am + "/" + a.cap(wp);
            g.drawString(f, s, w - 18 - f.width(s), ay + 12 + i * 12, col, false);
        }

        // point vise (croisiere / balistique)
        if (a.selected() == W.CRUISE || a.selected() == W.BALLISTIC) {
            Vec3 apt = a.aimPoint(mc.level, eye, a.aimDir());
            int[] s = project(apt, w, h);
            if (s != null) {
                box(g, s[0], s[1], 10, AMBER);
                g.drawString(f, "POINT VISE " + Math.round(apt.distanceTo(eye)) + " m", s[0] + 14, s[1] - 4, AMBER, false);
            }
        }

        // viseur de bombardement (bombes) : point d'impact prevu + indicateur de largage
        if (a.selected().bombLike() && !a.grounded()) bombsight(g, f, a, mc, eye, w, h, cx, blink);

        // verrouillage missile
        if (Aircraft.lockWeapon(a.selected())) {
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
        if (!a.kind.heli() && !a.grounded() && a.speedF < a.kind.stall + 0.2f && a.fuel() > 0) {
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

        String h1 = a.kind.heli() ? "ZQSD/WASD : vol   Espace : monter   Ctrl : descendre   Souris : cap" : "W/S : gaz   Souris : cap et assiette   Espace : postcombustion   Ctrl : frein";
        g.drawCenteredString(f, h1, cx, h - 58, 0x8858FF7A);
        g.drawCenteredString(f, "Clic gauche : tirer   G : arme suivante   R : recharger   Maj : sortir", cx, h - 48, 0x8858FF7A);
    }

    static double agl(Aircraft a) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 p = a.position();
        var r = mc.level.clip(new ClipContext(p, p.add(0, -400, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, a));
        return r.getType() == HitResult.Type.MISS ? -1 : p.y - r.getLocation().y;
    }

    /** Simule la chute d'une bombe lachee maintenant (meme physique que Shell) : renvoie {x,y,z,temps} ou null. */
    static double[] predictImpact(Aircraft a, Minecraft mc) {
        Vec3 p = a.position().add(0, 0.5, 0), v = a.getDeltaMovement();
        Vec3 last = p;
        for (int i = 1; i <= 600; i++) {
            v = v.add(0, -0.05, 0).scale(0.995);
            Vec3 n = p.add(v);
            if (i % 3 == 0 || n.y < mc.level.getMinBuildHeight()) {
                net.minecraft.world.phys.BlockHitResult br = mc.level.clip(new net.minecraft.world.level.ClipContext(last, n, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, a));
                if (br.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                    Vec3 q = br.getLocation();
                    return new double[]{q.x, q.y, q.z, i / 20.0};
                }
                last = n;
            }
            p = n;
            if (p.y < mc.level.getMinBuildHeight()) break;
        }
        return null;
    }

    static void bombsight(GuiGraphics g, Font f, Aircraft a, Minecraft mc, Vec3 eye, int w, int h, int cx, boolean blink) {
        double[] im = predictImpact(a, mc);
        int bx = w - 178, by = h / 2 - 34 + 26 + a.loaded().size() * 12;
        g.fill(bx - 6, by - 4, w - 12, by + 62, BG);
        g.drawString(f, "VISEUR BOMBES", bx, by, DIM, false);
        if (im == null) {
            g.drawString(f, "IMPACT   --", bx, by + 12, GREEN, false);
            return;
        }
        Vec3 ip = new Vec3(im[0], im[1], im[2]);
        int[] s = project(ip, w, h);
        if (s != null) {
            box(g, s[0], s[1], 8, GREEN);
            g.drawString(f, "IMPACT", s[0] + 12, s[1] - 4, GREEN, false);
        }
        Vec3 vel = a.getDeltaMovement();
        Vec3 fwd = new Vec3(vel.x, 0, vel.z);
        if (fwd.lengthSqr() < 1e-4) fwd = new Vec3(-Math.sin(Math.toRadians(a.getYRot())), 0, Math.cos(Math.toRadians(a.getYRot())));
        fwd = fwd.normalize();
        double hd = Math.hypot(ip.x - a.getX(), ip.z - a.getZ());
        g.drawString(f, "IMPACT   " + Math.round(hd) + " m  (" + String.format("%.1f", im[3]) + " s)", bx, by + 12, GREEN, false);
        // cible : point du sol sous la ligne de visee
        Vec3 d = a.aimDir();
        net.minecraft.world.phys.BlockHitResult br = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(d.scale(700)), net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, a));
        if (br.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            g.drawString(f, "CIBLE    -- (visez le sol)", bx, by + 24, DIM, false);
            return;
        }
        Vec3 tp = br.getLocation();
        int[] ts = project(tp, w, h);
        if (ts != null) {
            box(g, ts[0], ts[1], 12, AMBER);
            g.drawString(f, "CIBLE", ts[0] + 16, ts[1] - 4, AMBER, false);
        }
        Vec3 dl = tp.subtract(ip);
        double along = dl.x * fwd.x + dl.z * fwd.z;
        double side = dl.x * -fwd.z + dl.z * fwd.x;
        double tdist = Math.hypot(tp.x - a.getX(), tp.z - a.getZ());
        g.drawString(f, "CIBLE    " + Math.round(tdist) + " m", bx, by + 24, AMBER, false);
        String adv;
        int col;
        if (Math.abs(along) <= 14 && Math.abs(side) <= 25) {
            adv = "LARGUEZ !";
            col = blink ? GREEN : 0xFFFFFFFF;
        } else if (along > 14) {
            adv = "ATTENDRE  " + Math.round(along) + " m";
            col = AMBER;
        } else {
            adv = "TROP TARD";
            col = RED;
        }
        g.drawString(f, adv, bx, by + 38, col, false);
        g.drawString(f, "DERIVE   " + (Math.abs(side) < 3 ? "OK" : (side > 0 ? "gauche " : "droite ") + Math.round(Math.abs(side)) + " m"), bx, by + 50, Math.abs(side) <= 25 ? GREEN : AMBER, false);
        if (Math.abs(along) <= 14 && Math.abs(side) <= 25)
            g.drawCenteredString(f, ">>> LARGUEZ LES BOMBES <<<", cx, h - 86, blink ? GREEN : 0xFFFFFFFF);
        // barre de progression : 0 = impact sur la cible
        int bw = 160, bx0 = cx - bw / 2, by0 = h - 100;
        g.fill(bx0, by0, bx0 + bw, by0 + 4, 0x66FFFFFF);
        double k = Math.max(-1, Math.min(1, along / 300.0));
        int mx = bx0 + bw / 2 - (int) (k * bw / 2);
        g.fill(bx0 + bw / 2 - 1, by0 - 3, bx0 + bw / 2 + 1, by0 + 7, GREEN);
        g.fill(mx - 2, by0 - 2, mx + 2, by0 + 6, AMBER);
    }
}
