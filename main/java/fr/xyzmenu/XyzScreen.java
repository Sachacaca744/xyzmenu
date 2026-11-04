package fr.xyzmenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class XyzScreen extends Screen {
    private static final int PER_PAGE = 6;
    private static final int ROW_W = 300;
    private static final int ROW_H = 26;
    private static final int TOP = 44;

    private int page = 0;

    public XyzScreen() {
        super(Component.literal("XYZ - Mes commandes"));
    }

    private List<CommandStore.Entry> entries() {
        return CommandStore.entries;
    }

    private int pageCount() {
        return Math.max(1, (entries().size() + PER_PAGE - 1) / PER_PAGE);
    }

    @Override
    protected void init() {
        int left = this.width / 2 - ROW_W / 2;
        page = Math.min(page, pageCount() - 1);
        int start = page * PER_PAGE;

        for (int i = 0; i < PER_PAGE && start + i < entries().size(); i++) {
            CommandStore.Entry e = entries().get(start + i);
            int y = TOP + i * ROW_H;

            EditBox name = new EditBox(this.font, left, y, 90, 20, Component.literal("Nom"));
            name.setMaxLength(24);
            name.setValue(e.name);
            name.setResponder(s -> e.name = s);
            this.addRenderableWidget(name);

            EditBox cmd = new EditBox(this.font, left + 95, y, 130, 20, Component.literal("Commande"));
            cmd.setMaxLength(128);
            cmd.setValue(e.command);
            cmd.setResponder(s -> e.command = s);
            this.addRenderableWidget(cmd);

            this.addRenderableWidget(Button.builder(Component.literal("▶"), b -> run(e))
                    .bounds(left + 230, y, 40, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("X"), b -> {
                entries().remove(e);
                this.rebuildWidgets();
            }).bounds(left + 275, y, 25, 20).build());
        }

        int by = TOP + PER_PAGE * ROW_H + 6;

        Button prev = Button.builder(Component.literal("<"), b -> {
            page--;
            this.rebuildWidgets();
        }).bounds(left, by, 20, 20).build();
        prev.active = page > 0;
        this.addRenderableWidget(prev);

        Button next = Button.builder(Component.literal(">"), b -> {
            page++;
            this.rebuildWidgets();
        }).bounds(left + 25, by, 20, 20).build();
        next.active = page < pageCount() - 1;
        this.addRenderableWidget(next);

        this.addRenderableWidget(Button.builder(Component.literal("+ Ajouter"), b -> {
            entries().add(new CommandStore.Entry("Nouveau", "/"));
            page = pageCount() - 1;
            this.rebuildWidgets();
        }).bounds(left + 55, by, 110, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Terminé"), b -> this.onClose())
                .bounds(left + 170, by, 130, 20).build());
    }

    private void run(CommandStore.Entry e) {
        String c = e.command == null ? "" : e.command.trim();
        if (c.startsWith("/")) {
            c = c.substring(1);
        }
        if (c.isEmpty()) {
            return;
        }
        CommandStore.save();
        Minecraft mc = Minecraft.getInstance();
        this.onClose();
        if (mc.player != null) {
            mc.player.connection.sendCommand(c);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int left = this.width / 2 - ROW_W / 2;
        g.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        g.drawCenteredString(this.font, "▶ exécute la commande  •  X supprime  •  modifications sauvegardées automatiquement",
                this.width / 2, 27, 0xA0A0A0);
        g.drawString(this.font, "Nom", left + 2, TOP - 10, 0xA0A0A0);
        g.drawString(this.font, "Commande", left + 97, TOP - 10, 0xA0A0A0);
        g.drawCenteredString(this.font, "Page " + (page + 1) + "/" + pageCount(),
                this.width / 2, TOP + PER_PAGE * ROW_H + 32, 0xA0A0A0);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void removed() {
        CommandStore.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
