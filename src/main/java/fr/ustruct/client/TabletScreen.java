package fr.ustruct.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TabletScreen extends Screen {
    private static String lastName = "";
    private static int rot = 0, mir = 0;
    private static boolean air = false;
    private EditBox name;
    private Button bRot, bMir, bAir;
    private static final String[] MIR = {"aucun", "x", "z"};

    public TabletScreen() {
        super(Component.literal("Structures"));
    }

    private void cmd(String c) {
        if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.connection.sendCommand(c);
    }

    private String nm() {
        lastName = name.getValue().trim();
        return lastName;
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 - 80;
        name = new EditBox(font, cx - 100, y, 200, 20, Component.literal("Nom"));
        name.setMaxLength(48);
        name.setValue(lastName);
        name.setHint(Component.literal("Nom de la structure"));
        addRenderableWidget(name);
        y += 26;
        addRenderableWidget(Button.builder(Component.literal("Coin 1 ici"), b -> cmd("ustruct pos1")).bounds(cx - 100, y, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Coin 2 ici"), b -> cmd("ustruct pos2")).bounds(cx + 2, y, 98, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Sauvegarder"), b -> {
            if (!nm().isEmpty()) cmd("ustruct save " + nm() + " noentities");
        }).bounds(cx - 100, y, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Sauver + cadres"), b -> {
            if (!nm().isEmpty()) cmd("ustruct save " + nm() + " entities");
        }).bounds(cx + 2, y, 98, 20).build());
        y += 28;
        bRot = Button.builder(Component.literal("Rotation : " + rot), b -> {
            rot = (rot + 90) % 360;
            b.setMessage(Component.literal("Rotation : " + rot));
        }).bounds(cx - 100, y, 98, 20).build();
        bMir = Button.builder(Component.literal("Miroir : " + MIR[mir]), b -> {
            mir = (mir + 1) % 3;
            b.setMessage(Component.literal("Miroir : " + MIR[mir]));
        }).bounds(cx + 2, y, 98, 20).build();
        addRenderableWidget(bRot);
        addRenderableWidget(bMir);
        y += 24;
        bAir = Button.builder(Component.literal("Remplacer par l'air : " + (air ? "oui" : "non")), b -> {
            air = !air;
            b.setMessage(Component.literal("Remplacer par l'air : " + (air ? "oui" : "non")));
        }).bounds(cx - 100, y, 200, 20).build();
        addRenderableWidget(bAir);
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Placer ici"), b -> {
            if (!nm().isEmpty()) cmd("ustruct paste " + nm() + " ~ ~ ~ " + rot + " " + MIR[mir] + " " + air);
        }).bounds(cx - 100, y, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Annuler (undo)"), b -> cmd("ustruct undo")).bounds(cx + 2, y, 98, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Liste"), b -> cmd("ustruct list")).bounds(cx - 100, y, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Sauver le monde"), b -> {
            if (!nm().isEmpty()) cmd("ustruct world export " + nm());
        }).bounds(cx + 2, y, 98, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "Structures & mondes", width / 2, height / 2 - 100, 0xFFFFFF);
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, ClientSel.sizeText(), width / 2, height / 2 + 74, 0x66FF99);
        g.drawCenteredString(font, "Le nom sert aussi pour 'Sauver le monde' (fichier .zip)", width / 2, height / 2 + 88, 0x999999);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
