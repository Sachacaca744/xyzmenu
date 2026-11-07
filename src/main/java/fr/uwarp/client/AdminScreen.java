package fr.uwarp.client;

import fr.uwarp.Cfg;
import fr.uwarp.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AdminScreen extends Screen {
    private static final String[] KEYS = {"shop", "world", "spawn"};
    private static final String[] NAMES = {"Boutique", "World", "Spawn"};
    private static final int W = 400;
    private static final int ROW0 = 44;
    private static final int ROW_H = 28;

    private final Cfg.Data data;
    private boolean dirty = false;
    private String status = "";

    public AdminScreen(Cfg.Data data) {
        super(Component.literal("ADMIN - Configuration du /warp"));
        this.data = data;
    }

    private Cfg.Dest dest(int i) {
        return i == 0 ? data.shop : (i == 1 ? data.world : data.spawn);
    }

    @Override
    protected void init() {
        int left = this.width / 2 - W / 2;

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            final Cfg.Dest d = dest(i);
            int y = ROW0 + i * ROW_H;

            boolean isCmd = "COMMAND".equals(d.mode);
            this.addRenderableWidget(Button.builder(Component.literal(isCmd ? "Commande" : "Téléport"), b -> {
                d.mode = "COMMAND".equals(d.mode) ? "TELEPORT" : "COMMAND";
                dirty = true;
                this.rebuildWidgets();
            }).bounds(left + 62, y, 108, 20).build());

            if (isCmd) {
                EditBox cmd = new EditBox(this.font, left + 175, y, 215, 20, Component.literal("Commande"));
                cmd.setMaxLength(200);
                cmd.setValue(d.command);
                cmd.setResponder(s -> {
                    d.command = s;
                    dirty = true;
                });
                this.addRenderableWidget(cmd);
            } else {
                this.addRenderableWidget(Button.builder(Component.literal("Ma position"), b -> {
                    save();
                    Net.CHANNEL.sendToServer(new Net.SetHereMsg(KEYS[idx]));
                }).bounds(left + 300, y, 90, 20).build());
            }
        }

        int whY = ROW0 + 3 * ROW_H + 22;
        EditBox hook = new EditBox(this.font, left, whY, W, 20, Component.literal("Webhook"));
        hook.setMaxLength(400);
        hook.setValue(data.webhook);
        hook.setResponder(s -> {
            data.webhook = s;
            dirty = true;
        });
        this.addRenderableWidget(hook);

        int btnY = whY + 34;
        this.addRenderableWidget(Button.builder(Component.literal("Sauvegarder"), b -> {
            if (save()) {
                status = "Sauvegardé.";
            }
        }).bounds(left, btnY, 190, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> this.onClose())
                .bounds(left + 210, btnY, 190, 20).build());
    }

    private boolean save() {
        String json = Cfg.toJson(data);
        if (json.length() > Net.MAX_JSON - 500) {
            status = "Configuration trop grosse.";
            return false;
        }
        Net.CHANNEL.sendToServer(new Net.SaveAdminMsg(json));
        dirty = false;
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int left = this.width / 2 - W / 2;

        g.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFD966);
        g.drawCenteredString(this.font,
                "Chaque bouton du /warp : soit une commande (ex: shop), soit une position (clique « Ma position » là où tu es).",
                this.width / 2, 22, 0xFFA0A0B8);

        for (int i = 0; i < 3; i++) {
            Cfg.Dest d = dest(i);
            int y = ROW0 + i * ROW_H;
            g.drawString(this.font, NAMES[i], left + 2, y + 6, 0xFFFFFFFF);
            if (!"COMMAND".equals(d.mode)) {
                String where;
                if (d.set) {
                    String dim = d.dim.contains(":") ? d.dim.substring(d.dim.indexOf(':') + 1) : d.dim;
                    where = Math.round(d.x) + " " + Math.round(d.y) + " " + Math.round(d.z) + " " + dim;
                } else {
                    where = "pas défini";
                }
                g.drawString(this.font, where, left + 175, y + 6, d.set ? 0xFF80E0A0 : 0xFFFF8080);
            }
        }

        int whY = ROW0 + 3 * ROW_H + 22;
        g.drawString(this.font, "Webhook des idées (Discord/Slack, laisse vide pour désactiver) :",
                left, whY - 11, 0xFFA0A0B8);
        g.drawCenteredString(this.font, status, this.width / 2, whY + 60, 0xFFFFD966);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void removed() {
        if (dirty) {
            save();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
