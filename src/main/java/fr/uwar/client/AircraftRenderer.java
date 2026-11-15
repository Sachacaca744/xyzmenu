package fr.uwar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.uwar.Aircraft;
import fr.uwar.Uwar;
import fr.uwar.W;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class AircraftRenderer extends EntityRenderer<Aircraft> {
    public AircraftRenderer(EntityRendererProvider.Context c) {
        super(c);
        this.shadowRadius = 1.2f;
    }

    @Override
    public ResourceLocation getTextureLocation(Aircraft a) {
        return new ResourceLocation(Uwar.MODID, "textures/entity/" + a.kind.id + ".png");
    }

    @Override
    public boolean shouldRender(Aircraft a, Frustum f, double x, double y, double z) {
        return true;
    }

    public static void draw(Mesh m, PoseStack.Pose pose, VertexConsumer vc, int light, float alpha) {
        for (float[] q : m.q) {
            if (q.length < 24) continue;
            int mat = (int) q[23];
            int l = (mat == Mesh.LED || mat == Mesh.RLED || mat == Mesh.FLAME) ? LightTexture.FULL_BRIGHT : light;
            for (int i = 0; i < 4; i++) {
                vc.vertex(pose.pose(), q[i * 3], q[i * 3 + 1], q[i * 3 + 2])
                        .color(1f, 1f, 1f, alpha)
                        .uv(q[12 + i * 2], q[13 + i * 2])
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(l)
                        .normal(pose.normal(), q[20], q[21], q[22])
                        .endVertex();
            }
        }
    }

    @Override
    public void render(Aircraft a, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Models.Model m = Models.get(a.kind);
        ResourceLocation tex = getTextureLocation(a);
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(tex));
        Minecraft mc = Minecraft.getInstance();
        boolean inside = mc.options.getCameraType().isFirstPerson() && mc.player != null && a.hasPassenger(mc.player);
        float bodyYaw = Mth.rotLerp(pt, a.yRotO, a.getYRot());
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        ps.translate(0, m.pivotY, 0);
        ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, a.xRotO, a.getXRot())));
        ps.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(pt, a.rollO, a.roll)));
        ps.translate(0, -m.pivotY, 0);
        if (!inside) {
            draw(m.body, ps.last(), vc, light, 1f);
            if (a.grounded() || (a.kind.heli() && a.getDeltaMovement().lengthSqr() < 0.02)) draw(m.gear, ps.last(), vc, light, 1f);
            for (Models.Ord o : m.ord) {
                if (a.ammo(W.values()[o.weapon]) > o.idx) draw(o.m, ps.last(), vc, light, 1f);
            }
        }
        float rot = Mth.lerp(pt, a.rotorO, a.rotor);
        if (a.kind.heli() && !inside) {
            VertexConsumer vt = null;
            ps.pushPose();
            ps.translate(m.rx, m.ry, m.rz);
            ps.mulPose(Axis.YP.rotationDegrees(-rot));
            draw(m.rotor, ps.last(), vc, light, 1f);
            if (a.engineOn()) {
                vt = buf.getBuffer(RenderType.entityTranslucent(tex));
                draw(m.rotorDisc, ps.last(), vt, light, 0.28f);
            }
            ps.popPose();
            if (!inside) {
                ps.pushPose();
                ps.translate(m.tx, m.ty, m.tz);
                ps.mulPose(Axis.ZP.rotationDegrees(90));
                ps.mulPose(Axis.YP.rotationDegrees(rot * 1.7f));
                draw(m.tail, ps.last(), vc, light, 1f);
                if (a.engineOn()) {
                    if (vt == null) vt = buf.getBuffer(RenderType.entityTranslucent(tex));
                    draw(m.tailDisc, ps.last(), vt, light, 0.25f);
                }
                ps.popPose();
            }
        } else if (!a.kind.heli() && a.engineOn() && (a.thr() > 0.35f || a.boosting())) {
            float k = a.boosting() ? 1.25f : 0.5f + a.thr() * 0.6f;
            float pul = 1f + 0.08f * (float) Math.sin((a.tickCount + pt) * 2.7f);
            ps.pushPose();
            ps.translate(0, 1.0f, -6.0f);
            ps.scale(0.8f, 0.8f, k * pul);
            ps.translate(0, -1.0f, 6.0f);
            draw(m.flame, ps.last(), vc, light, 1f);
            ps.popPose();
        }
        ps.popPose();
    }
}
