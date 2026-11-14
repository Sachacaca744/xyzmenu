package fr.udrone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.udrone.Shell;
import fr.udrone.Udrone;
import fr.udrone.W;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class ShellRenderer extends EntityRenderer<Shell> {
    private static final ResourceLocation TEX = new ResourceLocation(Udrone.MODID, "textures/entity/weapons.png");

    public ShellRenderer(EntityRendererProvider.Context c) {
        super(c);
    }

    @Override
    public ResourceLocation getTextureLocation(Shell s) {
        return TEX;
    }

    @Override
    public void render(Shell s, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        W w = s.weapon();
        Vec3 v = s.getDeltaMovement();
        float yy = (float) (Mth.atan2(-v.x, v.z) * Mth.RAD_TO_DEG);
        float pp = (float) (-Math.toDegrees(Math.atan2(v.y, Math.hypot(v.x, v.z))));
        if (v.lengthSqr() < 1e-4) {
            yy = 0;
            pp = 90;
        }
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-yy));
        ps.mulPose(Axis.XP.rotationDegrees(pp));
        Mesh m = w == W.MISSILE ? Models.missile : w == W.BOMB ? Models.bomb : Models.shell;
        DroneRenderer.draw(m, ps.last(), buf.getBuffer(RenderType.entityCutoutNoCull(TEX)), light, 1f);
        ps.popPose();
    }
}
