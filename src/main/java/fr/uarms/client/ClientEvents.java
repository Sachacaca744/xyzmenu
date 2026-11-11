package fr.uarms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.uarms.KnifeItem;
import fr.uarms.Reg;
import fr.uarms.Uarms;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public class ClientEvents {
    @Mod.EventBusSubscriber(modid = Uarms.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(Reg.SOLDIER.get(), SoldierRenderer::new);
        }
    }

    @Mod.EventBusSubscriber(modid = Uarms.MODID, value = Dist.CLIENT)
    public static class ForgeBus {
        /** Animation du couteau : un coup d'estoc vers l'avant au lieu d'un grand revers. */
        @SubscribeEvent
        public static void hand(RenderHandEvent e) {
            if (!(e.getItemStack().getItem() instanceof KnifeItem)) return;
            float p = e.getSwingProgress();
            if (p <= 0f) return;
            float s = Mth.sin(p * (float) Math.PI);
            PoseStack ps = e.getPoseStack();
            ps.translate(0.06f * s, -0.03f * s, -0.6f * s);
            ps.mulPose(Axis.XP.rotationDegrees(-38f * s));
            ps.mulPose(Axis.ZP.rotationDegrees(10f * s));
        }

        /** Zoom du fusil de precision en s'accroupissant. */
        @SubscribeEvent
        public static void fov(ComputeFovModifierEvent e) {
            Player p = e.getPlayer();
            if (p.isCrouching() && p.getMainHandItem().is(Reg.SNIPER.get())) {
                e.setNewFovModifier(e.getNewFovModifier() * 0.3f);
            }
        }
    }
}
