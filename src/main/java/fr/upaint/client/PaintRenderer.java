package fr.upaint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.upaint.PaintData;
import fr.upaint.Upaint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Upaint.MODID, value = Dist.CLIENT)
public class PaintRenderer {
    private static final float E = 0.003f;
    private static final Map<String, TextureAtlasSprite> CACHE = new HashMap<>();
    private static Object lastAtlas;

    // coins (CCW vu de l'exterieur) par index de Direction : DOWN,UP,NORTH,SOUTH,WEST,EAST
    private static final float[][][] V = {
            {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
            {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
            {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
            {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},
            {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}},
    };

    private static float[] uv(int f, float[] v) {
        return switch (f) {
            case 0, 1 -> new float[]{v[0], v[2]};
            case 2 -> new float[]{1 - v[0], 1 - v[1]};
            case 3 -> new float[]{v[0], 1 - v[1]};
            case 4 -> new float[]{v[2], 1 - v[1]};
            default -> new float[]{1 - v[2], 1 - v[1]};
        };
    }

    private static TextureAtlasSprite sprite(String tex) {
        var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite s = CACHE.get(tex);
        if (s == null) {
            ResourceLocation rl = ResourceLocation.tryParse(tex);
            s = atlas.apply(rl == null ? new ResourceLocation("missingno") : rl);
            CACHE.put(tex, s);
        }
        return s;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        if (ClientPaint.CHUNKS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        var atlasObj = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        if (atlasObj != lastAtlas) {
            CACHE.clear();
            lastAtlas = atlasObj;
        }
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        RenderType rt = RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
        VertexConsumer vc = buf.getBuffer(rt);
        double max2 = 64.0 * 64.0;
        int camCx = ((int) Math.floor(cam.x)) >> 4;
        int camCz = ((int) Math.floor(cam.z)) >> 4;
        boolean drew = false;

        for (Map.Entry<Long, Map<Long, PaintData.Paint[]>> ce : ClientPaint.CHUNKS.entrySet()) {
            int cx = net.minecraft.world.level.ChunkPos.getX(ce.getKey());
            int cz = net.minecraft.world.level.ChunkPos.getZ(ce.getKey());
            if (Math.abs(cx - camCx) > 5 || Math.abs(cz - camCz) > 5) continue;
            for (Map.Entry<Long, PaintData.Paint[]> be : ce.getValue().entrySet()) {
                BlockPos pos = BlockPos.of(be.getKey());
                double dx = pos.getX() + 0.5 - cam.x, dy = pos.getY() + 0.5 - cam.y, dz = pos.getZ() + 0.5 - cam.z;
                if (dx * dx + dy * dy + dz * dz > max2) continue;
                if (level.getBlockState(pos).isAir()) continue;
                PaintData.Paint[] arr = be.getValue();
                for (int f = 0; f < 6; f++) {
                    PaintData.Paint p = arr[f];
                    if (p == null) continue;
                    Direction d = Direction.from3DDataValue(f);
                    int light = LevelRenderer.getLightColor(level, pos.relative(d));
                    float sh = level.getShade(d, true);
                    float r = ((p.color() >> 16) & 255) / 255f * sh;
                    float g = ((p.color() >> 8) & 255) / 255f * sh;
                    float b = (p.color() & 255) / 255f * sh;
                    TextureAtlasSprite sp = sprite(p.tex());
                    ps.pushPose();
                    ps.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
                    Matrix4f m = ps.last().pose();
                    Matrix3f n = ps.last().normal();
                    for (int i = 0; i < 4; i++) {
                        float[] v = V[f][i];
                        float[] t = uv(f, v);
                        float x = v[0] + d.getStepX() * E;
                        float y = v[1] + d.getStepY() * E;
                        float z = v[2] + d.getStepZ() * E;
                        vc.vertex(m, x, y, z)
                                .color(r, g, b, 1f)
                                .uv(sp.getU(t[0] * 16f), sp.getV(t[1] * 16f))
                                .overlayCoords(OverlayTexture.NO_OVERLAY)
                                .uv2(light)
                                .normal(n, d.getStepX(), d.getStepY(), d.getStepZ())
                                .endVertex();
                    }
                    ps.popPose();
                    drew = true;
                }
            }
        }
        if (drew) buf.endBatch(rt);
    }
}
