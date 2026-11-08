package fr.upaint.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.upaint.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BrushScreen extends Screen {
    private static final int COLS = 10, ROWS = 6, CELL = 22;
    private static final int[] DYES = {
            0xFFFFFF, 0xD0D0D0, 0x808080, 0x1D1D21, 0x835432, 0xB02E26, 0xF9801D, 0xFED83D,
            0x80C71F, 0x5E7C16, 0x169C9C, 0x3AB3DA, 0x3C44AA, 0x8932B8, 0xC74EBD, 0xF38BAA};

    private String tex;
    private int color;
    private EditBox search, hex;
    private int page = 0;
    private final List<ResourceLocation> all = new ArrayList<>();
    private List<ResourceLocation> shown = new ArrayList<>();

    public BrushScreen(String tex, int color) {
        super(Component.literal("Peinture"));
        this.tex = tex;
        this.color = color;
        var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        for (ResourceLocation rl : atlas.getTextureLocations()) {
            if (rl.getPath().startsWith("block/")) all.add(rl);
        }
        all.sort(Comparator.comparing(ResourceLocation::toString));
        filter();
    }

    private void filter() {
        String q = search == null ? "" : search.getValue().toLowerCase().trim();
        shown = new ArrayList<>();
        for (ResourceLocation r : all) {
            if (q.isEmpty() || r.toString().contains(q)) shown.add(r);
        }
        int pages = Math.max(1, (shown.size() + COLS * ROWS - 1) / (COLS * ROWS));
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;
    }

    private int gx() { return this.width / 2 - COLS * CELL / 2 - 60; }
    private int gy() { return 50; }

    @Override
    protected void init() {
        int gx = gx();
        search = new EditBox(font, gx, 24, COLS * CELL, 18, Component.literal("Recherche"));
        search.setHint(Component.literal("Rechercher une texture (ex: stone, wool...)"));
        search.setResponder(s -> { page = 0; filter(); });
        addRenderableWidget(search);
        int by = gy() + ROWS * CELL + 6;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; filter(); })
                .bounds(gx, by, 24, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; filter(); })
                .bounds(gx + COLS * CELL - 24, by, 24, 18).build());
        int rx = gx + COLS * CELL + 14;
        hex = new EditBox(font, rx, gy() + 90, 70, 18, Component.literal("Couleur"));
        hex.setMaxLength(7);
        hex.setValue(String.format("#%06X", color));
        hex.setResponder(s -> {
            try {
                String h = s.startsWith("#") ? s.substring(1) : s;
                if (h.length() == 6) color = Integer.parseInt(h, 16);
            } catch (NumberFormatException ignored) {
            }
        });
        addRenderableWidget(hex);
        addRenderableWidget(Button.builder(Component.literal("Valider"), b -> {
            Net.CH.sendToServer(new Net.SetBrushMsg(tex, color));
            onClose();
        }).bounds(rx, gy() + ROWS * CELL - 12, 90, 20).build());
        setInitialFocus(search);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "Choisis une texture et une couleur", width / 2, 8, 0xFFFFFF);
        int gx = gx(), gy = gy();
        int start = page * COLS * ROWS;
        ResourceLocation hover = null;
        for (int i = 0; i < COLS * ROWS; i++) {
            int idx = start + i;
            if (idx >= shown.size()) break;
            int x = gx + (i % COLS) * CELL, y = gy + (i / COLS) * CELL;
            ResourceLocation rl = shown.get(idx);
            g.fill(x, y, x + CELL - 1, y + CELL - 1, rl.toString().equals(tex) ? 0xFFFFFF55 : 0xFF303030);
            TextureAtlasSprite sp = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(rl);
            RenderSystem.setShaderColor(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, 1f);
            g.blit(x + 2, y + 2, 0, CELL - 5, CELL - 5, sp);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            if (mx >= x && mx < x + CELL && my >= y && my < y + CELL) hover = rl;
        }
        int pages = Math.max(1, (shown.size() + COLS * ROWS - 1) / (COLS * ROWS));
        g.drawCenteredString(font, (page + 1) + " / " + pages + "  (" + shown.size() + ")",
                gx + COLS * CELL / 2, gy + ROWS * CELL + 11, 0xAAAAAA);

        int rx = gx + COLS * CELL + 14;
        g.drawString(font, "Aperçu", rx, gy, 0xFFFFFF);
        g.fill(rx - 1, gy + 11, rx + 65, gy + 77, 0xFF000000);
        TextureAtlasSprite cur = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(ResourceLocation.tryParse(tex) == null ? new ResourceLocation("missingno") : ResourceLocation.tryParse(tex));
        RenderSystem.setShaderColor(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, 1f);
        g.blit(rx, gy + 12, 0, 64, 64, cur);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        g.drawString(font, "Couleur (hex)", rx, gy + 80, 0xFFFFFF);
        for (int i = 0; i < DYES.length; i++) {
            int x = rx + (i % 8) * 12, y = gy + 114 + (i / 8) * 12;
            g.fill(x, y, x + 11, y + 11, 0xFF000000 | DYES[i]);
        }
        super.render(g, mx, my, pt);
        if (hover != null) g.renderTooltip(font, Component.literal(hover.toString()), mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int gx = gx(), gy = gy();
        int start = page * COLS * ROWS;
        for (int i = 0; i < COLS * ROWS; i++) {
            int idx = start + i;
            if (idx >= shown.size()) break;
            int x = gx + (i % COLS) * CELL, y = gy + (i / COLS) * CELL;
            if (mx >= x && mx < x + CELL && my >= y && my < y + CELL) {
                tex = shown.get(idx).toString();
                return true;
            }
        }
        int rx = gx + COLS * CELL + 14;
        for (int i = 0; i < DYES.length; i++) {
            int x = rx + (i % 8) * 12, y = gy + 114 + (i / 8) * 12;
            if (mx >= x && mx < x + 11 && my >= y && my < y + 11) {
                color = DYES[i];
                hex.setValue(String.format("#%06X", color));
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
