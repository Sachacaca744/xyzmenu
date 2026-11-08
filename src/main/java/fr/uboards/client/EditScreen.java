package fr.uboards.client;

import fr.uboards.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class EditScreen extends Screen {
    private final BlockPos pos;
    private final boolean remote;
    private final boolean screenMode;
    private final String initial;
    private EditBox box;

    public EditScreen(BlockPos pos, boolean remote, boolean screenMode, String initial) {
        super(Component.literal(screenMode ? "Texte de l'écran" : "Image du tableau"));
        this.pos = pos;
        this.remote = remote;
        this.screenMode = screenMode;
        this.initial = initial == null ? "" : initial.replace("\n", "\\n");
    }

    @Override
    protected void init() {
        box = new EditBox(this.font, this.width / 2 - 170, this.height / 2 - 10, 340, 20, Component.empty());
        box.setMaxLength(900);
        box.setValue(initial);
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(Component.literal("Valider"), b -> send())
                .bounds(this.width / 2 - 170, this.height / 2 + 20, 165, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Annuler"), b -> onClose())
                .bounds(this.width / 2 + 5, this.height / 2 + 20, 165, 20).build());
    }

    private void send() {
        Net.CHANNEL.sendToServer(new Net.EditMsg(remote, pos, box.getValue()));
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            send();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 60, 0xFFFFFF);
        if (screenMode) {
            g.drawCenteredString(this.font, Component.literal("Écris le texte. \\n = retour à la ligne. Vide = effacer."),
                    this.width / 2, this.height / 2 - 40, 0xAAAAAA);
        } else {
            g.drawCenteredString(this.font, Component.literal("Lien https://... ou nom d'un fichier du dossier config/uboards/ du serveur."),
                    this.width / 2, this.height / 2 - 40, 0xAAAAAA);
            g.drawCenteredString(this.font, Component.literal("Vide = effacer. (PNG, JPG ou GIF)"),
                    this.width / 2, this.height / 2 - 28, 0xAAAAAA);
        }
        super.render(g, mx, my, pt);
    }
}
