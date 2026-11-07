package fr.uwarp.client;

import fr.uwarp.Net;
import fr.uwarp.Uwarp;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class WarpScreen extends Screen {
    private static final ResourceLocation[] ICONS = {
            new ResourceLocation(Uwarp.MODID, "textures/gui/icon_shop.png"),
            new ResourceLocation(Uwarp.MODID, "textures/gui/icon_world.png"),
            new ResourceLocation(Uwarp.MODID, "textures/gui/icon_spawn.png"),
            new ResourceLocation(Uwarp.MODID, "textures/gui/icon_idea.png")
    };
    private static final String[] KEYS = {"shop", "world", "spawn", "idea"};
    private static final String[] NAMES = {"Boutique", "World", "Spawn", "Vos idées"};
    private static final String[] SUBS = {"Achats et offres", "Explorer le monde", "Retour au départ", "Propose tes idées"};
    private static final int[] TOP = {0xFFE8B83A, 0xFF37C27E, 0xFFEC6A56, 0xFFA676F0};
    private static final int[] BOT = {0xFF7A5410, 0xFF125A3C, 0xFF7A2A22, 0xFF3C2A80};

    private static final int PW = 380;
    private static final int PH = 214;
    private static final int TW = 80;
    private static final int TH = 116;
    private static final int GAP = 12;

    private final boolean[] ready;
    private String status = "";

    public WarpScreen(boolean[] ready) {
        super(Component.literal("Warp"));
        this.ready = ready;
    }

    private int panelLeft() {
        return this.width / 2 - PW / 2;
    }

    private int panelTop() {
        return this.height / 2 - PH / 2;
    }

    private int tileX(int i) {
        return panelLeft() + (PW - (4 * TW + 3 * GAP)) / 2 + i * (TW + GAP);
    }

    private int tileY() {
        return panelTop() + 56;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> this.onClose())
                .bounds(this.width / 2 - 40, panelTop() + PH - 26, 80, 18).build());
    }

    private int hovered(double mx, double my) {
        for (int i = 0; i < 4; i++) {
            int x = tileX(i);
            int y = tileY();
            if (mx >= x && mx < x + TW && my >= y - 4 && my < y + TH) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button == 0) {
            int i = hovered(mx, my);
            if (i >= 0) {
                if (i == 3) {
                    Minecraft.getInstance().setScreen(new IdeaScreen());
                } else if (ready[i]) {
                    Net.CHANNEL.sendToServer(new Net.GoMsg(KEYS[i]));
                    this.onClose();
                } else {
                    status = NAMES[i] + " : pas encore configuré par l'admin.";
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        long t = Util.getMillis();
        int left = panelLeft();
        int top = panelTop();

        // Cadre dore + fond degrade
        g.fill(left - 3, top - 3, left + PW + 3, top + PH + 3, 0xFFB8860B);
        g.fill(left - 1, top - 1, left + PW + 1, top + PH + 1, 0xFF3A2A08);
        g.fillGradient(left, top, left + PW, top + PH, 0xF0141428, 0xF02E1B4A);

        // Etoiles scintillantes
        for (int i = 0; i < 46; i++) {
            int sx = left + 6 + (i * 97) % (PW - 12);
            int sy = top + 6 + (i * 53) % (PH - 12);
            double a = Math.sin(t / 350.0 + i * 1.7) * 0.5 + 0.5;
            int alpha = (int) (40 + 150 * a);
            g.fill(sx, sy, sx + 1, sy + 1, (alpha << 24) | 0xFFFFFF);
        }

        // Titre anime
        double pulse = Math.sin(t / 400.0) * 0.5 + 0.5;
        int green = (int) (200 + 55 * pulse);
        int blue = (int) (80 + 130 * pulse);
        int color = 0xFF000000 | (255 << 16) | (green << 8) | blue;
        g.pose().pushPose();
        g.pose().scale(2.0F, 2.0F, 1.0F);
        g.drawCenteredString(this.font, "WARP", this.width / 4, (top + 12) / 2, color);
        g.pose().popPose();
        g.drawCenteredString(this.font, "Choisis ta destination", this.width / 2, top + 36, 0xFFB8B8D0);

        int hov = hovered(mouseX, mouseY);
        for (int i = 0; i < 4; i++) {
            boolean ok = i == 3 || ready[i];
            boolean isHover = hov == i;
            int x = tileX(i);
            int y = tileY() - (isHover ? 4 : 0);
            int cx = x + TW / 2;

            int border = isHover ? 0xFFFFFFFF : 0xAA000000;
            g.fill(x - 1, y - 1, x + TW + 1, y + TH + 1, border);
            int c1 = ok ? TOP[i] : 0xFF6A6A6A;
            int c2 = ok ? BOT[i] : 0xFF2A2A2A;
            g.fillGradient(x, y, x + TW, y + TH, c1, c2);
            // reflet en haut de la tuile
            g.fillGradient(x, y, x + TW, y + 22, 0x50FFFFFF, 0x00FFFFFF);

            g.blit(ICONS[i], cx - 32, y + 12, 64, 64, 0.0F, 0.0F, 32, 32, 32, 32);

            g.drawCenteredString(this.font, NAMES[i], cx, y + TH - 34, 0xFFFFFFFF);
            g.pose().pushPose();
            g.pose().scale(0.75F, 0.75F, 1.0F);
            String sub = ok ? SUBS[i] : "Non configuré";
            int subColor = ok ? 0xFFE8E8F0 : 0xFFFF8080;
            g.drawCenteredString(this.font, sub, (int) (cx / 0.75F), (int) ((y + TH - 20) / 0.75F), subColor);
            g.pose().popPose();
        }

        if (!status.isEmpty()) {
            g.drawCenteredString(this.font, status, this.width / 2, top + PH - 40, 0xFFFFD966);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
