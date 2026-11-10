package fr.ustruct.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.ustruct.Reg;
import fr.ustruct.Ustruct;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Ustruct.MODID, value = Dist.CLIENT)
public class SelRenderer {
    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (ClientSel.a == null && ClientSel.b == null) return;
        if (!mc.level.dimension().location().toString().equals(ClientSel.dim)) return;
        boolean holding = mc.player.getMainHandItem().is(Reg.WAND.get()) || mc.player.getOffhandItem().is(Reg.WAND.get())
                || mc.player.getMainHandItem().is(Reg.TABLET.get()) || mc.player.getOffhandItem().is(Reg.TABLET.get());
        if (!holding) return;
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lines());
        if (ClientSel.a != null) box(ps, vc, cam, ClientSel.a, ClientSel.a, 1f, 0.2f, 0.2f);
        if (ClientSel.b != null) box(ps, vc, cam, ClientSel.b, ClientSel.b, 0.2f, 0.5f, 1f);
        if (ClientSel.a != null && ClientSel.b != null) box(ps, vc, cam, ClientSel.a, ClientSel.b, 0.2f, 1f, 0.4f);
        buf.endBatch(RenderType.lines());
    }

    private static void box(PoseStack ps, VertexConsumer vc, Vec3 cam, BlockPos a, BlockPos b, float r, float g, float bl) {
        double x0 = Math.min(a.getX(), b.getX()) - cam.x, y0 = Math.min(a.getY(), b.getY()) - cam.y, z0 = Math.min(a.getZ(), b.getZ()) - cam.z;
        double x1 = Math.max(a.getX(), b.getX()) + 1 - cam.x, y1 = Math.max(a.getY(), b.getY()) + 1 - cam.y, z1 = Math.max(a.getZ(), b.getZ()) + 1 - cam.z;
        LevelRenderer.renderLineBox(ps, vc, x0, y0, z0, x1, y1, z1, r, g, bl, 1f);
    }
}
