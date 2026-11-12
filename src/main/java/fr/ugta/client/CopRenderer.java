package fr.ugta.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.ugta.Cop;
import fr.ugta.Ugta;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class CopRenderer extends HumanoidMobRenderer<Cop, HumanoidModel<Cop>> {
    private static final ResourceLocation COP = new ResourceLocation(Ugta.MODID, "textures/entity/cop.png");
    private static final ResourceLocation ARMY = new ResourceLocation(Ugta.MODID, "textures/entity/army.png");

    public CopRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public void render(Cop e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        model.rightArmPose = e.isAiming() ? HumanoidModel.ArmPose.BOW_AND_ARROW : HumanoidModel.ArmPose.ITEM;
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        super.render(e, yaw, pt, ps, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(Cop e) {
        return e.isArmy() ? ARMY : COP;
    }
}
