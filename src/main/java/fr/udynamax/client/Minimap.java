package fr.udynamax.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public class Minimap {
    public static final int N = 64;       // pixels de la texture
    public static final int SIZE = 96;    // taille a l'ecran
    private static final float[] BPP = {0.5f, 1f, 2f, 4f};
    private static final ResourceLocation RL = new ResourceLocation("udynamax", "minimap");
    private static DynamicTexture tex;
    public static boolean enabled = true;
    public static int zoom = 1;
    private static int counter = 0;
    private static int lastPx = Integer.MIN_VALUE, lastPz = Integer.MIN_VALUE, lastZoom = -1;

    private static void init(Minecraft mc) {
        if (tex == null) {
            tex = new DynamicTexture(N, N, true);
            mc.getTextureManager().register(RL, tex);
        }
    }

    public static void tick(Minecraft mc) {
        if (!enabled || mc.player == null || mc.level == null) return;
        counter++;
        LocalPlayer p = mc.player;
        boolean moved = p.getBlockX() != lastPx || p.getBlockZ() != lastPz || zoom != lastZoom;
        if (counter % 10 != 0 && !(moved && counter % 3 == 0)) return;
        init(mc);
        lastPx = p.getBlockX();
        lastPz = p.getBlockZ();
        lastZoom = zoom;
        update(mc.level, p);
    }

    private static int sampleY(ClientLevel lv, int x, int z, int py) {
        if (lv.dimensionType().hasCeiling()) {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int y = Math.min(py + 6, lv.getMaxBuildHeight() - 1); y > lv.getMinBuildHeight(); y--) {
                m.set(x, y, z);
                BlockState s = lv.getBlockState(m);
                if (!s.isAir() && !lv.getBlockState(m.above()).canOcclude()) {
                    if (s.getMapColor(lv, m).col != 0) return y;
                }
            }
            return lv.getMinBuildHeight();
        }
        int y = lv.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 12 && y > lv.getMinBuildHeight(); i++, y--) {
            m.set(x, y, z);
            if (lv.getBlockState(m).getMapColor(lv, m).col != 0) return y;
        }
        return y;
    }

    private static void update(ClientLevel lv, LocalPlayer p) {
        NativeImage img = tex.getPixels();
        if (img == null) return;
        float bpp = BPP[zoom];
        int px = p.getBlockX(), pz = p.getBlockZ(), py = p.getBlockY();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                int wx = (int) Math.floor(px + (i - N / 2) * bpp);
                int wz = (int) Math.floor(pz + (j - N / 2) * bpp);
                int argb;
                m.set(wx, py, wz);
                if (!lv.hasChunkAt(m)) {
                    argb = 0xFF14141C;
                } else {
                    int y = sampleY(lv, wx, wz, py);
                    m.set(wx, y, wz);
                    BlockState s = lv.getBlockState(m);
                    int col = s.getMapColor(lv, m).col;
                    if (col == 0) {
                        argb = 0xFF14141C;
                    } else {
                        int r = (col >> 16) & 255, g = (col >> 8) & 255, b = col & 255;
                        if (s.getFluidState().is(FluidTags.WATER)) {
                            int depth = 0;
                            m.set(wx, y, wz);
                            while (depth < 10 && lv.getBlockState(m).getFluidState().is(FluidTags.WATER)) {
                                depth++;
                                m.move(0, -1, 0);
                            }
                            float f = Math.max(0.45f, 1f - depth * 0.07f);
                            r = (int) (r * f);
                            g = (int) (g * f);
                            b = (int) (b * f);
                        } else {
                            int yN = sampleY(lv, wx, wz - Math.max(1, Math.round(bpp)), py);
                            float f = y > yN ? 1.12f : (y < yN ? 0.82f : 1.0f);
                            r = Math.min(255, (int) (r * f));
                            g = Math.min(255, (int) (g * f));
                            b = Math.min(255, (int) (b * f));
                        }
                        argb = 0xFF000000 | (r << 16) | (g << 8) | b;
                    }
                }
                int abgr = (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF);
                img.setPixelRGBA(i, j, abgr);
            }
        }
        tex.upload();
    }

    public static void render(GuiGraphics g, int screenW) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || mc.player == null || mc.level == null || tex == null) return;
        if (mc.options.hideGui) return;
        if (mc.screen != null && mc.screen.getClass().getName().endsWith("BattleGUI")) return;
        int x = screenW - SIZE - 8, y = 8;
        g.fill(x - 2, y - 2, x + SIZE + 2, y + SIZE + 2, 0xCC000000);
        g.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, 0xFF8A1010);
        g.blit(RL, x, y, SIZE, SIZE, 0, 0, N, N, N, N);

        LocalPlayer p = mc.player;
        float bpp = BPP[zoom];
        float scale = SIZE / (float) N;
        // autres joueurs
        for (AbstractClientPlayer o : mc.level.players()) {
            if (o == p) continue;
            double dx = (o.getX() - p.getX()) / bpp * scale, dz = (o.getZ() - p.getZ()) / bpp * scale;
            if (Math.abs(dx) < SIZE / 2 - 2 && Math.abs(dz) < SIZE / 2 - 2) {
                int ox = x + SIZE / 2 + (int) dx, oz = y + SIZE / 2 + (int) dz;
                g.fill(ox - 1, oz - 1, ox + 2, oz + 2, 0xFF40D0FF);
            }
        }
        // joueur + direction
        int cx = x + SIZE / 2, cy = y + SIZE / 2;
        double yaw = Math.toRadians(p.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        for (int k = 1; k <= 5; k++) {
            int ax = cx + (int) Math.round(fx * k * 2), ay = cy + (int) Math.round(fz * k * 2);
            g.fill(ax, ay, ax + 2, ay + 2, 0xFFFF3030);
        }
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFF3030);
        // texte
        g.drawString(mc.font, "N", x + SIZE / 2 - 2, y + 1, 0xFFFFFF, true);
        String coords = p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ();
        g.drawString(mc.font, coords, x + SIZE - mc.font.width(coords), y + SIZE + 4, 0xFFFFFF, true);
        String z = "x" + (zoom == 0 ? "2" : zoom == 1 ? "1" : zoom == 2 ? "1/2" : "1/4");
        g.drawString(mc.font, z, x, y + SIZE + 4, 0xAAAAAA, true);
    }
}
