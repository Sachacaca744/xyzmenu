package fr.uwar.client;

import fr.uwar.Aircraft;
import fr.uwar.Net;
import fr.uwar.W;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Interface d'armement (touche 5) : choisir les armes montees sur l'appareil. */
public class LoadoutScreen extends Screen {
    private final Aircraft a;
    private final List<W> list = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    private int x0, y0;
    private static final int ROW = 22, WIDTH = 330;

    public LoadoutScreen(Aircraft a) {
        super(Component.literal("Armement"));
        this.a = a;
        for (W w : W.values()) if (w != W.CANNON && a.kind.canCarry(w)) list.add(w);
    }

    @Override
    protected void init() {
        buttons.clear();
        x0 = (width - WIDTH) / 2;
        y0 = Math.max(34, (height - (list.size() * ROW + 90)) / 2 + 24);
        for (int i = 0; i < list.size(); i++) {
            W w = list.get(i);
            Button b = Button.builder(Component.empty(), btn -> toggle(w))
                    .bounds(x0, y0 + i * ROW, WIDTH - 70, 20)
                    .tooltip(Tooltip.create(Component.literal(w.desc)))
                    .build();
            buttons.add(addRenderableWidget(b));
        }
        int by = y0 + list.size() * ROW + 28;
        addRenderableWidget(Button.builder(Component.literal("Recharger depuis l'inventaire"), btn -> Net.CH.sendToServer(new Net.CmdMsg(Net.C_RELOAD)))
                .bounds(x0, by, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fermer"), btn -> onClose()).bounds(x0 + 210, by, WIDTH - 210, 20).build());
        refresh();
    }

    private void toggle(W w) {
        Net.CH.sendToServer(new Net.LoadoutMsg(a.getId(), a.loadout() ^ w.bit()));
    }

    private void refresh() {
        for (int i = 0; i < list.size(); i++) {
            W w = list.get(i);
            boolean on = a.has(w);
            buttons.get(i).setMessage(Component.literal((on ? "[X] " : "[   ] ") + w.label));
        }
    }

    @Override
    public void tick() {
        if (a.isRemoved() || a.getControllingPassenger() == null && !a.hasPassenger(minecraft.player) && a.distanceTo(minecraft.player) > 12) onClose();
        refresh();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (ClientEvents.LOADOUT.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mod);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, a.kind.name + " - ARMEMENT", width / 2, y0 - 30, 0xFF58FF7A);
        String sl = "Emplacements : " + a.slotsUsed() + " / " + a.kind.slots + "   (le canon 20 mm est toujours monte)";
        g.drawCenteredString(font, sl, width / 2, y0 - 17, a.slotsUsed() >= a.kind.slots ? 0xFFFFC040 : 0xFFCCCCCC);
        super.render(g, mx, my, pt);
        for (int i = 0; i < list.size(); i++) {
            W w = list.get(i);
            boolean on = a.has(w);
            String s = on ? a.ammo(w) + " / " + a.cap(w) : "-";
            g.drawString(font, s, x0 + WIDTH - 62, y0 + i * ROW + 6, on ? 0xFF58FF7A : 0xFF888888, false);
        }
        int ty = y0 + list.size() * ROW + 6;
        boolean ground = a.grounded() || minecraft.player.getAbilities().instabuild;
        g.drawCenteredString(font, ground ? "Clic sur une arme : l'installer / la retirer (les munitions retirees sont rendues).  Survole pour la description."
                : "Pose l'appareil au sol pour modifier l'armement.", width / 2, ty, ground ? 0xFFAAAAAA : 0xFFFF6060);
        g.drawCenteredString(font, "Munitions : clic droit sur l'appareil avec l'objet, ou bouton ci-dessous / touche R.", width / 2, ty + 12, 0xFF888888);
    }
}
