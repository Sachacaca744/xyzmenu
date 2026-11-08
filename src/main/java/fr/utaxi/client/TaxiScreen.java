package fr.utaxi.client;

import fr.utaxi.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Menu "Taxi Corvaillus" : liste des destinations, 6 par page. */
public class TaxiScreen extends Screen {
    private static final int PER_PAGE = 6;
    private final List<String> names;
    private int page = 0;

    public TaxiScreen(List<String> names) {
        super(Component.literal("Taxi Corvaillus"));
        this.names = names;
    }

    @Override
    protected void init() {
        int pages = (names.size() + PER_PAGE - 1) / PER_PAGE;
        int cx = this.width / 2;
        int top = this.height / 2 - 62;
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < names.size(); i++) {
            final int idx = start + i;
            addRenderableWidget(Button.builder(Component.literal(names.get(idx)), b -> {
                Net.CHANNEL.sendToServer(new Net.GoMsg(idx));
                onClose();
            }).bounds(cx - 100, top + i * 24, 200, 20).build());
        }
        int by = top + PER_PAGE * 24 + 4;
        if (pages > 1) {
            Button prev = Button.builder(Component.literal("<"), b -> { page = Math.max(0, page - 1); rebuildWidgets(); })
                    .bounds(cx - 100, by, 40, 20).build();
            Button next = Button.builder(Component.literal(">"), b -> { page = Math.min(pages - 1, page + 1); rebuildWidgets(); })
                    .bounds(cx + 60, by, 40, 20).build();
            prev.active = page > 0;
            next.active = page < pages - 1;
            addRenderableWidget(prev);
            addRenderableWidget(next);
        }
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose())
                .bounds(cx - 50, by, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int cx = this.width / 2;
        int top = this.height / 2 - 62;
        int bottom = top + PER_PAGE * 24 + 34;
        // panneau bleu nuit avec bandeau titre
        g.fill(cx - 120, top - 50, cx + 120, bottom, 0xCC0B1630);
        g.fill(cx - 120, top - 50, cx + 120, top - 24, 0xFF1C4FA0);
        g.fill(cx - 120, top - 24, cx + 120, top - 22, 0xFF55C8F5);
        g.drawCenteredString(this.font, this.title, cx, top - 41, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.literal("Où voulez-vous aller ?"), cx, top - 16, 0xBFD8FF);
        super.render(g, mx, my, pt);
    }
}
