package fr.uwarp.client;

import fr.uwarp.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class IdeaScreen extends Screen {
    private static final int PW = 340;
    private static final int PH = 150;

    private EditBox box;
    private String status = "";

    public IdeaScreen() {
        super(Component.literal("Vos idées"));
    }

    @Override
    protected void init() {
        int left = this.width / 2 - PW / 2;
        int top = this.height / 2 - PH / 2;

        box = new EditBox(this.font, left + 20, top + 54, PW - 40, 20, Component.literal("Idée"));
        box.setMaxLength(300);
        this.addRenderableWidget(box);
        this.setInitialFocus(box);

        this.addRenderableWidget(Button.builder(Component.literal("Envoyer"), b -> send())
                .bounds(left + 20, top + PH - 36, 140, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Retour"), b -> back())
                .bounds(left + PW - 160, top + PH - 36, 140, 20).build());
    }

    private void back() {
        // Retour au menu : on redemande le menu au serveur via la commande /warp
        this.onClose();
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.connection.sendCommand("warp");
        }
    }

    private void send() {
        String text = box.getValue().trim();
        if (text.length() < 5) {
            status = "Ton idée est trop courte.";
            return;
        }
        Net.CHANNEL.sendToServer(new Net.IdeaMsg(text));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int left = this.width / 2 - PW / 2;
        int top = this.height / 2 - PH / 2;

        g.fill(left - 3, top - 3, left + PW + 3, top + PH + 3, 0xFFB8860B);
        g.fillGradient(left, top, left + PW, top + PH, 0xF0141428, 0xF03C2A80);

        g.pose().pushPose();
        g.pose().scale(1.5F, 1.5F, 1.0F);
        g.drawCenteredString(this.font, "Vos idées", (int) (this.width / 2 / 1.5F), (int) ((top + 10) / 1.5F), 0xFFFFD966);
        g.pose().popPose();
        g.drawCenteredString(this.font, "Écris ce que tu aimerais voir sur le serveur.",
                this.width / 2, top + 36, 0xFFC8C8E0);

        int len = box == null ? 0 : box.getValue().length();
        g.drawString(this.font, len + "/300", left + PW - 20 - this.font.width(len + "/300"), top + 78, 0xFF9090B0);
        if (!status.isEmpty()) {
            g.drawCenteredString(this.font, status, this.width / 2, top + 92, 0xFFFF8080);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
