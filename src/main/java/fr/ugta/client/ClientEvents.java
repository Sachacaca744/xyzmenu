package fr.ugta.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.ugta.Kind;
import fr.ugta.Net;
import fr.ugta.Reg;
import fr.ugta.Ugta;
import fr.ugta.Vehicle;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public class ClientEvents {
    public static final KeyMapping HORN = new KeyMapping("key.ugta.horn", InputConstants.KEY_H, "key.categories.ugta");

    @Mod.EventBusSubscriber(modid = Ugta.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            for (Kind k : Kind.values()) e.registerEntityRenderer(Reg.VEH.get(k).get(), VehicleRenderer::new);
            e.registerEntityRenderer(Reg.COP.get(), CopRenderer::new);
            e.registerEntityRenderer(Reg.PED.get(), PedRenderer::new);
        }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(HORN);
        }
    }

    @Mod.EventBusSubscriber(modid = Ugta.MODID, value = Dist.CLIENT)
    public static class ForgeBus {
        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (HORN.consumeClick()) {
                if (mc.player != null && mc.player.getVehicle() instanceof Vehicle) Net.CH.sendToServer(new Net.HornMsg());
            }
        }

        @SubscribeEvent
        public static void join(EntityJoinLevelEvent e) {
            if (!e.getLevel().isClientSide() || !(e.getEntity() instanceof Vehicle v)) return;
            var sm = Minecraft.getInstance().getSoundManager();
            switch (v.kind.cat) {
                case 0 -> {
                    sm.play(new VehicleSound(v, Reg.ENGINE.get(), 0));
                    if (v.kind == Kind.POLICE) sm.play(new VehicleSound(v, Reg.SIREN.get(), 1));
                }
                case 1 -> sm.play(new VehicleSound(v, Reg.PROP.get(), 3));
                default -> {
                    sm.play(new VehicleSound(v, Reg.ROTOR.get(), 2));
                    if (v.kind == Kind.POLICE_HELI) sm.play(new VehicleSound(v, Reg.SIREN.get(), 1));
                }
            }
        }
    }
}
