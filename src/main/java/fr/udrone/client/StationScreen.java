package fr.udrone.client;

import fr.udrone.Drone;
import fr.udrone.Kind;
import fr.udrone.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.UUID;

/** Station de controle : liste des drones, carte, telemetrie, commandes. */
public class StationScreen extends Screen {
    public final BlockPos pos;
    private List<Net.Entry> list;
    private int sel;
    private MapTex map;
    private int ticks;
    private Button bTake, bRtb, bHover, bUnlink;
    private int x0, y0;
    static final int W = 392, H = 236;

    public StationScreen(BlockPos pos, List<Net.Entry> list) {
        super(Component.literal("Station de controle"));
        this.pos = pos;
        this.list = list;
    }

    public void update(List<Net.Entry> l) {
        this.list = l;
        if (sel >= l.size()) sel = Math.max(0, l.size() - 1);
    }

    private Net.Entry cur() {
        return list.isEmpty() ? null : list.get(Mth.clamp(sel, 0, list.size() - 1));
    }

    private void cmd(int c) {
        Net.Entry e = cur();
        if (e == null) return;
        Net.CH.sendToServer(new Net.StationCmd(pos, e.id(), c));
    }

    @Override
    protected void init() {
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
        map = new MapTex();
        int by = y0 + H - 28;
        bTake = addRenderableWidget(Button.builder(Component.literal("PILOTER (camera)"), b -> cmd(Net.S_TAKE)).bounds(x0 + 8, by, 104, 20).build());
        bRtb = addRenderableWidget(Button.builder(Component.literal("RETOUR BASE"), b -> cmd(Net.S_RTB)).bounds(x0 + 116, by, 84, 20).build());
        bHover = addRenderableWidget(Button.builder(Component.literal("AUTONOME"), b -> cmd(Net.S_HOVER)).bounds(x0 + 204, by, 74, 20).build());
        bUnlink = addRenderableWidget(Button.builder(Component.literal("DELIER"), b -> cmd(Net.S_UNLINK)).bounds(x0 + 282, by, 52, 20).build());
        addRenderableWidget(Button.builder(Component.literal("FERMER"), b -> onClose()).bounds(x0 + 338, by, 46, 20).build());
    }

    @Override
    public void removed() {
        if (map != null) map.close();
        map = null;
    }

    @Override
    public void tick() {
        ticks++;
        if (ticks % 10 == 0) Net.CH.sendToServer(new Net.StationCmd(pos, new UUID(0, 0), Net.S_REFRESH));
        if (ticks % 20 == 1 && map != null) {
            Net.Entry e = cur();
            if (e != null && e.live()) map.update((int) Math.floor(e.x()), (int) Math.floor(e.z()));
            else map.update(pos.getX(), pos.getZ());
        }
        Net.Entry e = cur();
        boolean live = e != null && e.live();
        bTake.active = live && !e.piloted() && e.state() != Drone.LOST && e.state() != Drone.DEAD;
        bRtb.active = live && e.state() != Drone.LANDED && e.state() != Drone.LOST;
        bHover.active = live && e.piloted();
        bUnlink.active = e != null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        int lx = x0 + 8, ly = y0 + 30;
        for (int i = 0; i < list.size(); i++) {
            int ry = ly + i * 26;
            if (mx >= lx && mx < lx + 100 && my >= ry && my < ry + 24) {
                sel = i;
                ticks = 0;
                return true;
            }
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String stateName(int s) {
        return Drone.STATE_NAMES[Mth.clamp(s, 0, 5)];
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        var f = font;
        // fond et cadre
        g.fill(x0 - 2, y0 - 2, x0 + W + 2, y0 + H + 2, 0xFF0E2A1A);
        g.fill(x0, y0, x0 + W, y0 + H, 0xF0070D0A);
        g.fill(x0, y0, x0 + W, y0 + 20, 0xFF123524);
        g.drawString(f, "STATION DE CONTROLE UD-1", x0 + 8, y0 + 6, Hud.GREEN);
        g.drawString(f, "POS " + pos.getX() + " " + pos.getY() + " " + pos.getZ(), x0 + W - 8 - f.width("POS 0000 000 0000") , y0 + 6, Hud.DIM);

        // liste
        g.drawString(f, "DRONES LIES", x0 + 8, y0 + 22, Hud.DIM);
        for (int i = 0; i < list.size(); i++) {
            Net.Entry e = list.get(i);
            int ry = y0 + 30 + i * 26;
            boolean s = i == sel;
            g.fill(x0 + 8, ry, x0 + 108, ry + 24, s ? 0xFF1E5A3A : 0xFF10241A);
            Kind k = Kind.values()[Mth.clamp(e.kind(), 0, Kind.values().length - 1)];
            String nm = k.title.length() > 17 ? k.title.substring(0, 16) + "." : k.title;
            g.drawString(f, nm, x0 + 12, ry + 3, s ? 0xFFFFFFFF : Hud.GREEN);
            int dot = !e.live() ? 0xFF777777 : e.piloted() ? Hud.AMBER : e.state() == Drone.LOST ? Hud.RED : Hud.GREEN;
            g.fill(x0 + 12, ry + 15, x0 + 18, ry + 21, dot);
            g.drawString(f, e.live() ? (e.batt() * 100 / k.battery) + "%  " + stateName(e.state()) : "NON CHARGE", x0 + 22, ry + 13, Hud.DIM);
        }
        if (list.isEmpty()) {
            g.drawString(f, "Aucun drone.", x0 + 12, y0 + 36, Hud.DIM);
            g.drawString(f, "Clic droit sur la", x0 + 12, y0 + 50, Hud.DIM);
            g.drawString(f, "station avec une", x0 + 12, y0 + 60, Hud.DIM);
            g.drawString(f, "telecommande lie", x0 + 12, y0 + 70, Hud.DIM);
            g.drawString(f, "les drones.", x0 + 12, y0 + 80, Hud.DIM);
        }

        // carte
        int mx0 = x0 + 116, my0 = y0 + 30, ms = MapTex.N;
        g.drawString(f, "CARTE (1 px = 1 bloc, nord en haut)", mx0, y0 + 22, Hud.DIM);
        g.fill(mx0 - 1, my0 - 1, mx0 + ms + 1, my0 + ms + 1, Hud.GREEN);
        if (map != null) g.blit(map.rl, mx0, my0, 0, 0, ms, ms, MapTex.N, MapTex.N);
        for (int i = 0; i <= 4; i++) {
            int o = i * ms / 4;
            g.fill(mx0 + o, my0, mx0 + o + 1, my0 + ms, 0x2258FF7A);
            g.fill(mx0, my0 + o, mx0 + ms, my0 + o + 1, 0x2258FF7A);
        }
        Net.Entry e = cur();
        double cxw = pos.getX(), czw = pos.getZ();
        if (e != null && e.live()) {
            cxw = Math.floor(e.x());
            czw = Math.floor(e.z());
        }
        // station
        int sx = (int) (mx0 + ms / 2 + (pos.getX() - cxw)), sy = (int) (my0 + ms / 2 + (pos.getZ() - czw));
        if (sx > mx0 + 2 && sx < mx0 + ms - 3 && sy > my0 + 2 && sy < my0 + ms - 3) {
            g.fill(sx - 2, sy - 2, sx + 3, sy + 3, 0xFFFFFFFF);
            g.fill(sx - 1, sy - 1, sx + 2, sy + 2, 0xFF2266FF);
        }
        // drones
        for (int i = 0; i < list.size(); i++) {
            Net.Entry d = list.get(i);
            if (!d.live()) continue;
            int px = (int) (mx0 + ms / 2 + (d.x() - cxw)), py = (int) (my0 + ms / 2 + (d.z() - czw));
            if (px < mx0 + 3 || px > mx0 + ms - 4 || py < my0 + 3 || py > my0 + ms - 4) continue;
            int col = i == sel ? Hud.AMBER : Hud.GREEN;
            float yr = d.yaw() * Mth.DEG_TO_RAD;
            for (int k = 2; k < 9; k++) g.fill(px + Math.round(-Mth.sin(yr) * k), py + Math.round(Mth.cos(yr) * k),
                    px + Math.round(-Mth.sin(yr) * k) + 1, py + Math.round(Mth.cos(yr) * k) + 1, col);
            g.fill(px - 2, py - 2, px + 3, py + 3, 0xFF000000);
            g.fill(px - 1, py - 1, px + 2, py + 2, col);
        }

        // telemetrie
        int tx = x0 + 252, ty = y0 + 22;
        g.drawString(f, "TELEMETRIE", tx, ty, Hud.DIM);
        if (e == null) {
            g.drawString(f, "-", tx, ty + 12, Hud.DIM);
        } else {
            Kind k = Kind.values()[Mth.clamp(e.kind(), 0, Kind.values().length - 1)];
            g.drawString(f, k.title, tx, ty + 11, 0xFFFFFFFF);
            if (!e.live()) {
                g.drawString(f, "Drone hors de portee", tx, ty + 25, Hud.AMBER);
                g.drawString(f, "(chunk non charge).", tx, ty + 35, Hud.AMBER);
            } else {
                double dist = Math.sqrt(Math.pow(e.x() - (pos.getX() + 0.5), 2) + Math.pow(e.y() - (pos.getY() + 0.5), 2) + Math.pow(e.z() - (pos.getZ() + 0.5), 2));
                g.drawString(f, "ETAT  " + stateName(e.state()), tx, ty + 24, e.state() == Drone.LOST ? Hud.RED : Hud.GREEN);
                g.drawString(f, "ALT   " + (int) e.y() + " m", tx, ty + 35, Hud.GREEN);
                g.drawString(f, "XZ    " + (int) e.x() + " / " + (int) e.z(), tx, ty + 46, Hud.GREEN);
                g.drawString(f, "DIST  " + (int) dist + " m", tx, ty + 57, Hud.GREEN);
                float bp = e.batt() / (float) k.battery;
                g.drawString(f, "BATT", tx, ty + 70, Hud.GREEN);
                Hud.bar(g, tx + 34, ty + 71, 84, 7, bp, bp < 0.15f ? Hud.RED : bp < 0.35f ? Hud.AMBER : Hud.GREEN);
                g.drawString(f, "SIGN", tx, ty + 82, Hud.GREEN);
                Hud.bar(g, tx + 34, ty + 83, 84, 7, e.sig(), e.sig() < 0.3f ? Hud.RED : Hud.GREEN);
                g.drawString(f, "PV", tx, ty + 94, Hud.GREEN);
                Hud.bar(g, tx + 34, ty + 95, 84, 7, e.hp() / k.hp, e.hp() / k.hp < 0.3f ? Hud.RED : Hud.GREEN);
                g.drawString(f, "EQUIPEMENT", tx, ty + 110, Hud.DIM);
                if (k.weapons.length == 0) g.drawString(f, "camera seule", tx, ty + 121, Hud.DIM);
                for (int i = 0; i < k.weapons.length; i++) {
                    int am = (e.ammo() >> (8 * i)) & 255;
                    g.drawString(f, (i == e.sel() ? "> " : "  ") + k.weapons[i].title + " x" + am, tx, ty + 121 + i * 10, i == e.sel() ? Hud.AMBER : Hud.GREEN);
                }
                if (k.weapons.length > 0) {
                    g.drawString(f, "TEMP", tx, ty + 121 + k.weapons.length * 10, Hud.GREEN);
                    Hud.bar(g, tx + 34, ty + 122 + k.weapons.length * 10, 84, 6, e.heat() / 100f, e.heat() > 80 ? Hud.RED : Hud.GREEN);
                }
            }
        }
        g.drawString(f, "Camera : bouton PILOTER (vue FPV plein ecran)", x0 + 8, y0 + H - 40, Hud.DIM);
        super.render(g, mx, my, pt);
    }
}
