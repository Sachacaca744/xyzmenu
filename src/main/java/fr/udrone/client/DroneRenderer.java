package fr.udrone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.udrone.Drone;
import fr.udrone.Udrone;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class DroneRenderer extends EntityRenderer<Drone> {
    public DroneRenderer(EntityRendererProvider.Context c) {
        super(c);
        this.shadowRadius = 0.3f;
    }

    @Override
    public ResourceLocation getTextureLocation(Drone d) {
        return new ResourceLocation(Udrone.MODID, "textures/entity/" + d.kind.id + ".png");
    }

    @Override
    public boolean shouldRender(Drone d, Frustum f, double x, double y, double z) {
        return true;
    }

    public static void draw(Mesh m, PoseStack.Pose pose, VertexConsumer vc, int light, float alpha) {
        for (float[] q : m.q) {
            if (q.length < 24) continue;
            int mat = (int) q[23];
            int l = (mat == Mesh.LED || mat == Mesh.RLED) ? LightTexture.FULL_BRIGHT : light;
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
    public void render(Drone d, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Models.Model m = Models.get(d.kind);
        ResourceLocation tex = getTextureLocation(d);
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(tex));
        float bodyYaw = Mth.rotLerp(pt, d.yRotO, d.getYRot());
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, d.tiltPO, d.tiltP)));
        ps.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(pt, d.tiltRO, d.tiltR)));
        draw(m.body, ps.last(), vc, light, 1f);
        // munitions visibles
        for (Models.Ord o : m.ord) {
            if (d.ammo(o.weapon) > o.idx) draw(o.m, ps.last(), vc, light, 1f);
        }
        // nacelle camera : suit le regard du pilote
        ps.pushPose();
        ps.translate(m.gx, m.gy, m.gz);
        float rel = Mth.wrapDegrees(d.getViewYRot(pt) - bodyYaw);
        float cp = Mth.clamp(d.getViewXRot(pt), -15f, 90f);
        ps.mulPose(Axis.YP.rotationDegrees(-rel));
        ps.mulPose(Axis.XP.rotationDegrees(cp));
        draw(m.gimbal, ps.last(), vc, light, 1f);
        ps.popPose();
        // helices
        float thr = d.throttle();
        VertexConsumer vt = null;
        for (Models.Prop p : m.props) {
            ps.pushPose();
            ps.translate(p.x, p.y, p.z);
            if (p.zAxis) ps.mulPose(Axis.XP.rotationDegrees(90));
            float ang = Mth.lerp(pt, d.propAngO, d.propAng) * p.dir;
            ps.mulPose(Axis.YP.rotationDegrees(ang));
            draw(p.blades, ps.last(), vc, light, 1f);
            if (thr > 0.25f) {
                if (vt == null) vt = buf.getBuffer(RenderType.entityTranslucent(tex));
                draw(p.disc, ps.last(), vt, light, Mth.clamp((thr - 0.2f) * 0.55f, 0f, 0.4f));
            }
            ps.popPose();
        }
        ps.popPose();
    }
}
