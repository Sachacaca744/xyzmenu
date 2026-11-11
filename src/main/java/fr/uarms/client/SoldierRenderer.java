package fr.uarms.client;

import fr.uarms.Soldier;
import fr.uarms.Uarms;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

public class SoldierRenderer extends HumanoidMobRenderer<Soldier, HumanoidModel<Soldier>> {
    private static final ResourceLocation TEX = new ResourceLocation(Uarms.MODID, "textures/entity/soldier.png");

    public SoldierRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
    }

    @Override
    public void render(Soldier e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        HumanoidModel.ArmPose pose = e.getMainHandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY
                : (e.isAggressive() && e.holdsGun() ? HumanoidModel.ArmPose.BOW_AND_ARROW : HumanoidModel.ArmPose.ITEM);
        this.model.rightArmPose = pose;
        this.model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        super.render(e, yaw, pt, ps, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(Soldier e) {
        return TEX;
    }
}
