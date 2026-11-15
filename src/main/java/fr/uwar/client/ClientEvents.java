package fr.uwar.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.uwar.Aircraft;
import fr.uwar.Kind;
import fr.uwar.Net;
import fr.uwar.Reg;
import fr.uwar.Uwar;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public class ClientEvents {
    public static final String CAT = "key.categories.uwar";
    public static final KeyMapping WEAPON = new KeyMapping("key.uwar.weapon", InputConstants.KEY_G, CAT);
    public static final KeyMapping RELOAD = new KeyMapping("key.uwar.reload", InputConstants.KEY_R, CAT);

    @Mod.EventBusSubscriber(modid = Uwar.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            for (Kind k : Kind.values()) e.registerEntityRenderer(Reg.AIRCRAFTS.get(k).get(), AircraftRenderer::new);
            e.registerEntityRenderer(Reg.SHELL.get(), ShellRenderer::new);
        }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(WEAPON);
            e.register(RELOAD);
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("cockpit", (gui, g, pt, w, h) -> {
                if (!Minecraft.getInstance().options.hideGui) Hud.render(g, w, h, pt);
            });
        }
    }

    @Mod.EventBusSubscriber(modid = Uwar.MODID, value = Dist.CLIENT)
    public static class ForgeBus {
        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            if (ClientHooks.mine() == null) {
                while (WEAPON.consumeClick() || RELOAD.consumeClick()) {
                }
                return;
            }
            while (WEAPON.consumeClick()) Net.CH.sendToServer(new Net.CmdMsg(Net.C_WEAPON));
            while (RELOAD.consumeClick()) Net.CH.sendToServer(new Net.CmdMsg(Net.C_RELOAD));
        }

        @SubscribeEvent
        public static void join(EntityJoinLevelEvent e) {
            if (!e.getLevel().isClientSide() || !(e.getEntity() instanceof Aircraft a)) return;
            Minecraft.getInstance().getSoundManager().play(new AircraftSound(a, a.kind.heli() ? Reg.HELI_LOOP.get() : Reg.JET_LOOP.get()));
        }

        @SubscribeEvent
        public static void move(MovementInputUpdateEvent e) {
            if (ClientHooks.mine() == null) return;
            var in = e.getInput();
            in.forwardImpulse = 0;
            in.leftImpulse = 0;
            in.up = in.down = in.left = in.right = false;
            in.jumping = false;
        }

        @SubscribeEvent
        public static void interact(InputEvent.InteractionKeyMappingTriggered e) {
            if (ClientHooks.mine() != null && (e.isAttack() || e.isUseItem())) {
                e.setCanceled(true);
                e.setSwingHand(false);
            }
        }

        @SubscribeEvent
        public static void hand(RenderHandEvent e) {
            if (ClientHooks.mine() != null) e.setCanceled(true);
        }

        @SubscribeEvent
        public static void overlay(RenderGuiOverlayEvent.Pre e) {
            if (ClientHooks.mine() == null) return;
            var id = e.getOverlay().id();
            if (id.equals(VanillaGuiOverlay.CROSSHAIR.id()) || id.equals(VanillaGuiOverlay.MOUNT_HEALTH.id())
                    || id.equals(VanillaGuiOverlay.JUMP_BAR.id()))
                e.setCanceled(true);
        }
    }
}
