package fr.utaxi.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.utaxi.BoothBlock;
import fr.utaxi.TaxiBE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.state.BlockState;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Dessine le Corvaillus au-dessus de la cabine. Le modele 3D et la texture sont lus dans Cobblemon
 * (cherche "corviknight" dans ses ressources). Sans Cobblemon : un simple oiseau de secours.
 */
public class StatueRenderer implements BlockEntityRenderer<TaxiBE> {
    /** Hauteur de la statue, en blocs. */
    private static final float TARGET_HEIGHT = 2.6F;

    private static boolean tried = false;
    private static GeoModel model;
    private static ResourceLocation texture;

    private static final String FALLBACK = "{\"minecraft:geometry\":[{\"description\":{\"texture_width\":16,\"texture_height\":16},\"bones\":["
            + "{\"name\":\"body\",\"pivot\":[0,10,0],\"cubes\":[{\"origin\":[-5,6,-4],\"size\":[10,10,8],\"uv\":[0,0]}]},"
            + "{\"name\":\"head\",\"parent\":\"body\",\"pivot\":[0,16,0],\"cubes\":[{\"origin\":[-3,16,-5],\"size\":[6,6,6],\"uv\":[0,0]},{\"origin\":[-1,17,-9],\"size\":[2,2,4],\"uv\":[0,0]}]},"
            + "{\"name\":\"wl\",\"parent\":\"body\",\"pivot\":[5,14,0],\"cubes\":[{\"origin\":[5,4,-3],\"size\":[2,12,6],\"uv\":[0,0]}]},"
            + "{\"name\":\"wr\",\"parent\":\"body\",\"pivot\":[-5,14,0],\"cubes\":[{\"origin\":[-7,4,-3],\"size\":[2,12,6],\"uv\":[0,0]}]},"
            + "{\"name\":\"tail\",\"parent\":\"body\",\"pivot\":[0,8,4],\"cubes\":[{\"origin\":[-3,2,4],\"size\":[6,8,3],\"uv\":[0,0]}]},"
            + "{\"name\":\"legs\",\"parent\":\"body\",\"pivot\":[0,6,0],\"cubes\":[{\"origin\":[-4,0,-1],\"size\":[2,6,2],\"uv\":[0,0]},{\"origin\":[2,0,-1],\"size\":[2,6,2],\"uv\":[0,0]}]}]}]}";

    public StatueRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public boolean shouldRenderOffScreen(TaxiBE be) {
        return true;
    }

    private static void load() {
        tried = true;
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        try {
            Map<ResourceLocation, Resource> geos = rm.listResources("bedrock/pokemon/models",
                    rl -> rl.getPath().contains("corviknight") && rl.getPath().endsWith(".geo.json"));
            ResourceLocation bestGeo = null;
            for (ResourceLocation rl : geos.keySet()) {
                if (bestGeo == null || rl.getPath().length() < bestGeo.getPath().length()) bestGeo = rl;
            }
            Map<ResourceLocation, Resource> texs = rm.listResources("textures/pokemon",
                    rl -> rl.getPath().contains("corviknight") && rl.getPath().endsWith(".png"));
            ResourceLocation bestTex = null;
            for (ResourceLocation rl : texs.keySet()) {
                String p = rl.getPath();
                if (p.contains("shiny") || p.contains("emissive") || p.contains("layer") || p.contains("glow")) continue;
                if (bestTex == null || p.length() < bestTex.getPath().length()) bestTex = rl;
            }
            if (bestGeo != null && bestTex != null) {
                try (InputStream in = geos.get(bestGeo).open()) {
                    model = GeoModel.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
                texture = bestTex;
                return;
            }
        } catch (Exception e) {
            model = null;
        }
        // secours (Cobblemon absent ou format inattendu)
        model = GeoModel.parse(FALLBACK);
        texture = new ResourceLocation("minecraft", "textures/block/black_concrete.png");
    }

    @Override
    public void render(TaxiBE be, float partial, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        if (!tried) {
            try {
                load();
            } catch (Exception e) {
                model = null;
            }
        }
        if (model == null) {
            return;
        }
        BlockState st = be.getBlockState();
        if (!(st.getBlock() instanceof BoothBlock)) {
            return;
        }
        Direction f = st.getValue(BoothBlock.FACING);
        float hpx = model.maxY - model.minY;
        float k = TARGET_HEIGHT / Math.max(hpx, 1F);
        float bob = (float) Math.sin((be.getLevel() == null ? 0 : be.getLevel().getGameTime() + partial) / 20.0) * 0.03F;

        ps.pushPose();
        ps.translate(0.5, 1.0 + bob, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(180F - f.toYRot()));
        ps.scale(k, k, k);
        ps.translate(-(model.minX + model.maxX) / 2F, -model.minY, -(model.minZ + model.maxZ) / 2F);
        model.render(ps, buf.getBuffer(RenderType.entityCutoutNoCull(texture)), light);
        ps.popPose();
    }
}
