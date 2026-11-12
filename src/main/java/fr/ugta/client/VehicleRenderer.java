package fr.ugta.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.ugta.Kind;
import fr.ugta.Ugta;
import fr.ugta.Vehicle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Modeles 3D des vehicules : assemblage de boites colorees (avant = +Z, gauche = +X). */
public class VehicleRenderer extends EntityRenderer<Vehicle> {
    private static final ResourceLocation WHITE = new ResourceLocation(Ugta.MODID, "textures/entity/white.png");
    private static final int GLASS = 0x26303c, DARK = 0x16161a, TIRE = 0x151515, HUB = 0x9aa0a8;

    private PoseStack ps;
    private VertexConsumer vc;
    private int light;

    public VehicleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(Vehicle e) {
        return WHITE;
    }

    @Override
    public void render(Vehicle e, float yawIn, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        this.ps = ps;
        this.light = light;
        this.vc = buf.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yRotO, e.getYRot())));
        ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, e.xRotO, e.getXRot())));
        int c = e.color();
        float wr = Mth.lerp(pt, e.wheelRotO, e.wheelRot);
        float rotor = Mth.lerp(pt, e.rotorO, e.rotor);
        boolean flash = ((e.tickCount / 5) & 1) == 0;
        switch (e.kind) {
            case SEDAN -> car(c, wr, false);
            case SPORTS -> sports(c, wr);
            case POLICE -> police(wr, flash);
            case TRUCK -> truck(c, wr);
            case PLANE -> plane(c, rotor);
            case HELI -> heli(c, rotor, false, flash);
            case POLICE_HELI -> heli(0x15151c, rotor, true, flash);
        }
        ps.popPose();
        super.render(e, yawIn, pt, ps, buf, light);
    }

    // ------------------------------------------------------------------ outils

    private static int dim(int c, float f) {
        int r = (int) (((c >> 16) & 255) * f), g = (int) (((c >> 8) & 255) * f), b = (int) ((c & 255) * f);
        return (Mth.clamp(r, 0, 255) << 16) | (Mth.clamp(g, 0, 255) << 8) | Mth.clamp(b, 0, 255);
    }

    private void box(float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
        PoseStack.Pose p = ps.last();
        Matrix4f m = p.pose();
        Matrix3f n = p.normal();
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        quad(m, n, r, g, b, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0);
        quad(m, n, r, g, b, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0);
        quad(m, n, r, g, b, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0, 0, 0, -1);
        quad(m, n, r, g, b, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, 0, 0, 1);
        quad(m, n, r, g, b, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0, 1, 0, 0);
        quad(m, n, r, g, b, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1, 0, 0);
    }

    private void quad(Matrix4f m, Matrix3f n, int r, int g, int b, float ax, float ay, float az, float bx, float by, float bz,
                      float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz) {
        v(m, n, r, g, b, ax, ay, az, 0, 0, nx, ny, nz);
        v(m, n, r, g, b, bx, by, bz, 0, 1, nx, ny, nz);
        v(m, n, r, g, b, cx, cy, cz, 1, 1, nx, ny, nz);
        v(m, n, r, g, b, dx, dy, dz, 1, 0, nx, ny, nz);
    }

    private void v(Matrix4f m, Matrix3f n, int r, int g, int b, float x, float y, float z, float u, float vv, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(r, g, b, 255).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }

    private void wheel(float x, float y, float z, float rot, float rad, float w) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Axis.XP.rotationDegrees(rot));
        box(-w, -rad, -rad, w, rad, rad, TIRE);
        box(-w - 0.01f, -rad * 0.4f, -rad * 0.4f, w + 0.01f, rad * 0.4f, rad * 0.4f, HUB);
        ps.popPose();
    }

    private void lights(float y, float zf, float zb, float half) {
        box(half * 0.45f, y, zf, half * 0.9f, y + 0.2f, zf + 0.04f, 0xFFF3B0);
        box(-half * 0.9f, y, zf, -half * 0.45f, y + 0.2f, zf + 0.04f, 0xFFF3B0);
        box(half * 0.45f, y, zb - 0.04f, half * 0.9f, y + 0.2f, zb, 0xC01010);
        box(-half * 0.9f, y, zb - 0.04f, -half * 0.45f, y + 0.2f, zb, 0xC01010);
    }

    // ------------------------------------------------------------------ modeles

    private void car(int c, float wr, boolean police) {
        box(-0.95f, 0.35f, -2.0f, 0.95f, 0.95f, 2.0f, c);
        box(-0.85f, 0.95f, -0.8f, 0.85f, 1.4f, 1.2f, GLASS);
        box(-0.9f, 1.4f, -0.85f, 0.9f, 1.48f, 1.25f, c);
        for (float sx : new float[]{-1, 1}) {
            box(sx * 0.82f - 0.06f, 0.95f, 1.1f, sx * 0.82f + 0.06f, 1.4f, 1.22f, c);
            box(sx * 0.82f - 0.06f, 0.95f, -0.82f, sx * 0.82f + 0.06f, 1.4f, -0.7f, c);
        }
        box(-0.97f, 0.35f, 2.0f, 0.97f, 0.6f, 2.12f, 0x303030);
        box(-0.97f, 0.35f, -2.12f, 0.97f, 0.6f, -2.0f, 0x303030);
        box(-0.4f, 0.5f, 2.0f, 0.4f, 0.62f, 2.05f, 0x181818);
        lights(0.65f, 2.0f, -2.0f, 1.0f);
        for (float sx : new float[]{-1, 1}) {
            wheel(sx * 0.98f, 0.33f, 1.25f, wr, 0.33f, 0.12f);
            wheel(sx * 0.98f, 0.33f, -1.25f, wr, 0.33f, 0.12f);
        }
    }

    private void sports(int c, float wr) {
        box(-0.95f, 0.3f, -2.1f, 0.95f, 0.75f, 2.1f, c);
        box(-0.8f, 0.75f, -0.5f, 0.8f, 1.15f, 1.1f, GLASS);
        box(-0.82f, 1.15f, -0.55f, 0.82f, 1.22f, 1.0f, c);
        box(-0.15f, 0.75f, -2.1f, 0.15f, 0.78f, 2.1f, 0xEEEEEE);
        box(-0.9f, 1.0f, -2.05f, 0.9f, 1.07f, -1.7f, DARK);
        box(-0.7f, 0.75f, -1.95f, -0.6f, 1.0f, -1.85f, DARK);
        box(0.6f, 0.75f, -1.95f, 0.7f, 1.0f, -1.85f, DARK);
        box(-0.97f, 0.3f, 2.1f, 0.97f, 0.5f, 2.2f, 0x202020);
        lights(0.5f, 2.1f, -2.1f, 1.0f);
        for (float sx : new float[]{-1, 1}) {
            wheel(sx * 0.98f, 0.36f, 1.3f, wr, 0.36f, 0.14f);
            wheel(sx * 0.98f, 0.36f, -1.3f, wr, 0.36f, 0.14f);
        }
    }

    private void police(float wr, boolean flash) {
        car(0x16161c, wr, true);
        box(-0.9f, 1.4f, -0.85f, 0.9f, 1.48f, 1.25f, 0xF4F4F4);
        for (float sx : new float[]{-1, 1}) box(sx * 0.95f - (sx > 0 ? 0 : 0.012f), 0.4f, -0.8f, sx * 0.95f + (sx > 0 ? 0.012f : 0), 0.95f, 0.95f, 0xF4F4F4);
        box(-0.5f, 1.48f, -0.2f, 0.5f, 1.55f, 0.2f, 0x202020);
        box(0.05f, 1.55f, -0.15f, 0.5f, 1.68f, 0.15f, flash ? 0xFF2020 : 0x601010);
        box(-0.5f, 1.55f, -0.15f, -0.05f, 1.68f, 0.15f, flash ? 0x302060 : 0x2040FF);
    }

    private void truck(int c, float wr) {
        box(-1.1f, 0.5f, -3.0f, 1.1f, 0.85f, 3.0f, 0x202020);
        box(-1.15f, 0.85f, 1.2f, 1.15f, 2.5f, 3.0f, c);
        box(-1.0f, 1.55f, 2.95f, 1.0f, 2.3f, 3.03f, GLASS);
        for (float sx : new float[]{-1, 1}) box(sx * 1.15f - 0.02f, 1.55f, 1.4f, sx * 1.15f + 0.02f, 2.3f, 2.5f, GLASS);
        box(-1.25f, 0.9f, -3.0f, 1.25f, 2.9f, 1.1f, 0xE6E6E6);
        box(-1.26f, 1.7f, -3.0f, 1.26f, 2.0f, 1.1f, dim(c, 0.9f));
        box(-1.0f, 0.5f, 3.0f, 1.0f, 0.8f, 3.1f, 0x303030);
        box(0.55f, 0.95f, 3.0f, 0.95f, 1.2f, 3.04f, 0xFFF3B0);
        box(-0.95f, 0.95f, 3.0f, -0.55f, 1.2f, 3.04f, 0xFFF3B0);
        box(0.5f, 0.95f, -3.04f, 0.95f, 1.2f, -3.0f, 0xC01010);
        box(-0.95f, 0.95f, -3.04f, -0.5f, 1.2f, -3.0f, 0xC01010);
        for (float sx : new float[]{-1, 1}) {
            wheel(sx * 1.2f, 0.45f, 2.2f, wr, 0.45f, 0.18f);
            wheel(sx * 1.2f, 0.45f, -0.6f, wr, 0.45f, 0.18f);
            wheel(sx * 1.2f, 0.45f, -2.0f, wr, 0.45f, 0.18f);
        }
    }

    private void plane(int c, float rot) {
        box(-0.45f, 0.8f, -3.0f, 0.45f, 1.7f, 2.6f, 0xF0F0F0);
        box(-0.3f, 1.0f, -4.2f, 0.3f, 1.6f, -3.0f, 0xF0F0F0);
        box(-0.35f, 0.95f, 2.6f, 0.35f, 1.55f, 3.0f, c);
        box(-0.4f, 1.7f, 0.3f, 0.4f, 2.0f, 1.6f, GLASS);
        box(-4.2f, 1.15f, -0.6f, 4.2f, 1.25f, 0.8f, c);
        box(-4.2f, 1.15f, -0.6f, -3.6f, 1.25f, 0.8f, 0xEEEEEE);
        box(3.6f, 1.15f, -0.6f, 4.2f, 1.25f, 0.8f, 0xEEEEEE);
        box(-0.07f, 1.7f, -4.1f, 0.07f, 2.7f, -3.2f, c);
        box(-1.6f, 1.3f, -4.1f, 1.6f, 1.38f, -3.4f, c);
        for (float sx : new float[]{-1, 1}) {
            box(sx * 0.9f - 0.04f, 0.5f, 0.2f, sx * 0.9f + 0.04f, 1.2f, 0.4f, 0x303030);
            wheel(sx * 0.9f, 0.3f, 0.3f, 0, 0.3f, 0.1f);
        }
        box(-0.04f, 0.5f, 2.2f, 0.04f, 1.0f, 2.35f, 0x303030);
        wheel(0, 0.25f, 2.3f, 0, 0.25f, 0.08f);
        ps.pushPose();
        ps.translate(0, 1.25f, 3.05f);
        ps.mulPose(Axis.ZP.rotationDegrees(rot));
        box(-1.25f, -0.09f, -0.04f, 1.25f, 0.09f, 0.04f, 0x202020);
        ps.popPose();
        box(-0.12f, 1.13f, 3.0f, 0.12f, 1.37f, 3.12f, 0xB0B0B0);
    }

    private void heli(int c, float rot, boolean police, boolean flash) {
        box(-0.9f, 0.5f, -1.4f, 0.9f, 1.9f, 1.6f, c);
        box(-0.8f, 0.7f, 1.6f, 0.8f, 1.8f, 2.2f, GLASS);
        box(-0.2f, 1.3f, -5.2f, 0.2f, 1.7f, -1.4f, c);
        box(-0.06f, 1.3f, -5.4f, 0.06f, 2.3f, -4.7f, c);
        box(-0.8f, 1.4f, -5.2f, 0.8f, 1.46f, -4.8f, c);
        for (float sx : new float[]{-1, 1}) {
            box(sx * 1.0f - 0.07f, 0.1f, -1.7f, sx * 1.0f + 0.07f, 0.22f, 1.9f, 0x202020);
            box(sx * 0.95f - 0.04f, 0.2f, -0.9f, sx * 0.95f + 0.04f, 0.6f, -0.8f, 0x202020);
            box(sx * 0.95f - 0.04f, 0.2f, 1.0f, sx * 0.95f + 0.04f, 0.6f, 1.1f, 0x202020);
        }
        box(-0.12f, 1.9f, -0.12f, 0.12f, 2.3f, 0.12f, 0x303030);
        if (police) {
            box(-0.91f, 0.9f, -1.4f, 0.91f, 1.2f, 1.6f, 0xF4F4F4);
            box(0.1f, 1.9f, 0.4f, 0.5f, 2.0f, 0.8f, flash ? 0xFF2020 : 0x601010);
            box(-0.5f, 1.9f, 0.4f, -0.1f, 2.0f, 0.8f, flash ? 0x302060 : 0x2040FF);
        }
        ps.pushPose();
        ps.translate(0, 2.3f, 0);
        ps.mulPose(Axis.YP.rotationDegrees(rot));
        box(-4.5f, -0.03f, -0.15f, 4.5f, 0.03f, 0.15f, 0x202020);
        box(-0.15f, -0.03f, -4.5f, 0.15f, 0.03f, 4.5f, 0x202020);
        ps.popPose();
        ps.pushPose();
        ps.translate(0.14f, 1.5f, -5.2f);
        ps.mulPose(Axis.XP.rotationDegrees(rot * 2));
        box(-0.03f, -0.8f, -0.08f, 0.03f, 0.8f, 0.08f, 0x202020);
        ps.popPose();
    }
}
