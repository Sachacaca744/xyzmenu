package fr.uboards.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.uboards.BoardBE;
import fr.uboards.BoardBlock;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class BoardRenderer implements BlockEntityRenderer<BoardBE> {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");

    private record Layout(String key, List<FormattedCharSequence> lines, int px) {}

    private final Font font;
    private final Map<BoardBE, Layout> cache = new WeakHashMap<>();

    public BoardRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public boolean shouldRenderOffScreen(BoardBE be) {
        return true;
    }

    @Override
    public void render(BoardBE be, float partial, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        BlockState st = be.getBlockState();
        if (!(st.getBlock() instanceof BoardBlock)) {
            return;
        }
        Direction f = st.getValue(BoardBlock.FACING);
        int w = be.getW(), h = be.getH();
        float z = 0.0625F + 0.003F;

        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-f.toYRot()));
        ps.translate(-0.5, 0.0, -0.5);

        if (be.isScreen()) {
            quad(ps, buf, WHITE, w, h, z, 255);
            drawText(be, ps, buf, w, h, z + 0.004F);
        } else {
            ResourceLocation tex = be.getImg().isEmpty() ? null : ClientImages.get(be.getImg());
            if (tex != null) {
                quad(ps, buf, tex, w, h, z, 255);
            } else {
                quad(ps, buf, WHITE, w, h, z, be.getImg().isEmpty() ? 60 : 130);
            }
        }
        ps.popPose();
    }

    private void quad(PoseStack ps, MultiBufferSource buf, ResourceLocation tex, int w, int h, float z, int grey) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(tex));
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        int l = LightTexture.FULL_BRIGHT;
        vc.vertex(m, 0, 0, z).color(grey, grey, grey, 255).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(l).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, w, 0, z).color(grey, grey, grey, 255).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(l).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, w, h, z).color(grey, grey, grey, 255).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(l).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, 0, h, z).color(grey, grey, grey, 255).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(l).normal(n, 0, 0, 1).endVertex();
    }

    private Layout layout(BoardBE be, int w, int h) {
        String key = w + "x" + h + "|" + be.getText();
        Layout cur = cache.get(be);
        if (cur != null && cur.key().equals(key)) {
            return cur;
        }
        Layout best = null;
        for (int px = 12; px <= 160; px++) {
            int pad = Math.max(1, px / 12);
            int wrap = w * px - 2 * pad;
            List<FormattedCharSequence> lines = font.split(Component.literal(be.getText()), wrap);
            best = new Layout(key, lines, px);
            if (lines.size() * 9 <= h * px - 2 * pad) {
                break;
            }
        }
        cache.put(be, best);
        return best;
    }

    private void drawText(BoardBE be, PoseStack ps, MultiBufferSource buf, int w, int h, float z) {
        if (be.getText().isEmpty()) {
            return;
        }
        Layout lay = layout(be, w, h);
        int px = lay.px();
        ps.pushPose();
        ps.translate(0, h, z);
        float s = 1.0F / px;
        ps.scale(s, -s, s);
        float startY = (h * px - lay.lines().size() * 9) / 2.0F;
        float y = startY;
        for (FormattedCharSequence line : lay.lines()) {
            float x = (w * px - font.width(line)) / 2.0F;
            font.drawInBatch(line, x, y, 0xFF000000, false, ps.last().pose(), buf,
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            y += 9;
        }
        ps.popPose();
    }
}
