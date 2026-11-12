package fr.ugta.client;

import fr.ugta.Ped;
import fr.ugta.Ugta;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class PedRenderer extends HumanoidMobRenderer<Ped, HumanoidModel<Ped>> {
    private static final ResourceLocation[] TEX = new ResourceLocation[Ped.VARIANTS];

    static {
        for (int i = 0; i < TEX.length; i++) TEX[i] = new ResourceLocation(Ugta.MODID, "textures/entity/ped_" + i + ".png");
    }

    public PedRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(Ped e) {
        return TEX[Math.floorMod(e.variant(), TEX.length)];
    }
}
