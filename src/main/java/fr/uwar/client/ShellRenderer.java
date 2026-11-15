package fr.uwar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.uwar.Shell;
import fr.uwar.Uwar;
import fr.uwar.W;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class ShellRenderer extends EntityRenderer<Shell> {
    private static final ResourceLocation TEX = new ResourceLocation(Uwar.MODID, "textures/entity/weapons.png");

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
        Mesh m = w.missileLike() ? Models.missile : w.bombLike() ? Models.bomb : Models.rocket;
        if (w.missileLike() || w.bombLike()) ps.scale(w.visScale(), w.visScale(), w.visScale());
        AircraftRenderer.draw(m, ps.last(), buf.getBuffer(RenderType.entityCutoutNoCull(TEX)), light, 1f);
        ps.popPose();
    }
}
