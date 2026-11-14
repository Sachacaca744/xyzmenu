package fr.udrone.client;

import com.mojang.blaze3d.platform.NativeImage;
import fr.udrone.Udrone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/** Carte vue de dessus (1 pixel = 1 bloc) construite avec les chunks deja charges par le client. */
public class MapTex {
    public static final int N = 128;
    private final DynamicTexture tex = new DynamicTexture(N, N, false);
    public final ResourceLocation rl = new ResourceLocation(Udrone.MODID, "dynamic/station_map");

    public MapTex() {
        Minecraft.getInstance().getTextureManager().register(rl, tex);
    }

    public void close() {
        Minecraft.getInstance().getTextureManager().release(rl);
    }

    private static int height(ClientLevel lv, int x, int z) {
        LevelChunk c = lv.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (c == null) return Integer.MIN_VALUE;
        return c.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
    }

    public void update(int cx, int cz) {
        ClientLevel lv = Minecraft.getInstance().level;
        if (lv == null) return;
        NativeImage img = tex.getPixels();
        if (img == null) return;
        int x0 = cx - N / 2, z0 = cz - N / 2;
        for (int py = 0; py < N; py++) {
            for (int px = 0; px < N; px++) {
                int x = x0 + px, z = z0 + py;
                int h = height(lv, x, z);
                int col;
                if (h == Integer.MIN_VALUE) {
                    col = 0xFF1A1410 | (((px + py) & 1) == 0 ? 0x000402 : 0);
                } else {
                    BlockPos p = new BlockPos(x, h - 1, z);
                    LevelChunk c = lv.getChunkSource().getChunkNow(x >> 4, z >> 4);
                    BlockState bs = c.getBlockState(p);
                    MapColor mc = bs.getMapColor(lv, p);
                    MapColor.Brightness br;
                    if (bs.getFluidState().is(FluidTags.WATER)) {
                        int d = 0;
                        BlockPos q = p;
                        while (d < 12 && lv.getBlockState(q).getFluidState().is(FluidTags.WATER)) {
                            q = q.below();
                            d++;
                        }
                        br = d < 3 ? MapColor.Brightness.HIGH : d < 7 ? MapColor.Brightness.NORMAL : MapColor.Brightness.LOW;
                    } else {
                        int hn = height(lv, x, z - 1);
                        br = hn == Integer.MIN_VALUE || hn == h ? MapColor.Brightness.NORMAL
                                : hn < h ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
                    }
                    col = mc == MapColor.NONE ? 0xFF241E18 : mc.calculateRGBColor(br);
                }
                img.setPixelRGBA(px, py, col);
            }
        }
        tex.upload();
    }
}
