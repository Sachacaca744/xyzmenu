package fr.udrone.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.udrone.Drone;
import fr.udrone.Kind;
import fr.udrone.Net;
import fr.udrone.Reg;
import fr.udrone.Udrone;
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
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public class ClientEvents {
    public static final String CAT = "key.categories.udrone";
    public static final KeyMapping EXIT = new KeyMapping("key.udrone.exit", InputConstants.KEY_X, CAT);
    public static final KeyMapping WEAPON = new KeyMapping("key.udrone.weapon", InputConstants.KEY_R, CAT);
    public static final KeyMapping RTB = new KeyMapping("key.udrone.rtb", InputConstants.KEY_H, CAT);
    public static final KeyMapping NEXT = new KeyMapping("key.udrone.next", InputConstants.KEY_N, CAT);
    public static final KeyMapping ZOOM = new KeyMapping("key.udrone.zoom", InputConstants.KEY_Z, CAT);

    @Mod.EventBusSubscriber(modid = Udrone.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            for (Kind k : Kind.values()) e.registerEntityRenderer(Reg.DRONES.get(k).get(), DroneRenderer::new);
            e.registerEntityRenderer(Reg.SHELL.get(), ShellRenderer::new);
        }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(EXIT);
            e.register(WEAPON);
            e.register(RTB);
            e.register(NEXT);
            e.register(ZOOM);
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("fpv", (gui, g, pt, w, h) -> {
                if (!Minecraft.getInstance().options.hideGui) Hud.render(g, w, h, pt);
            });
        }
    }

    @Mod.EventBusSubscriber(modid = Udrone.MODID, value = Dist.CLIENT)
    public static class ForgeBus {
        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            ClientCtl.tick();
            if (!ClientCtl.active()) {
                while (EXIT.consumeClick() || WEAPON.consumeClick() || RTB.consumeClick() || NEXT.consumeClick() || ZOOM.consumeClick()) {
                }
                return;
            }
            while (EXIT.consumeClick()) Net.CH.sendToServer(new Net.CmdMsg(Net.C_EXIT));
            while (WEAPON.consumeClick()) Net.CH.sendToServer(new Net.CmdMsg(Net.C_WEAPON));
            while (RTB.consumeClick()) Net.CH.sendToServer(new Net.CmdMsg(Net.C_RTB));
            while (NEXT.consumeClick()) Net.CH.sendToServer(new Net.CmdMsg(Net.C_NEXT));
            while (ZOOM.consumeClick()) ClientCtl.zoomIdx = (ClientCtl.zoomIdx + 1) % ClientCtl.ZOOMS.length;
        }

        @SubscribeEvent
        public static void join(EntityJoinLevelEvent e) {
            if (!e.getLevel().isClientSide() || !(e.getEntity() instanceof Drone d)) return;
            var sm = Minecraft.getInstance().getSoundManager();
            switch (d.kind) {
                case REAPER -> sm.play(new DroneSound(d, Reg.REAPER_LOOP.get(), 1.0f));
                case HEAVY -> sm.play(new DroneSound(d, Reg.HEAVY_LOOP.get(), 1.0f));
                case FAST -> sm.play(new DroneSound(d, Reg.QUAD_LOOP.get(), 1.35f));
                case MINI -> sm.play(new DroneSound(d, Reg.QUAD_LOOP.get(), 1.2f));
                case SURVEIL -> sm.play(new DroneSound(d, Reg.QUAD_LOOP.get(), 0.8f));
                default -> sm.play(new DroneSound(d, Reg.QUAD_LOOP.get(), 1.0f));
            }
        }

        @SubscribeEvent
        public static void move(MovementInputUpdateEvent e) {
            if (!ClientCtl.active()) return;
            var in = e.getInput();
            in.forwardImpulse = 0;
            in.leftImpulse = 0;
            in.up = in.down = in.left = in.right = false;
            in.jumping = false;
            in.shiftKeyDown = false;
        }

        @SubscribeEvent
        public static void interact(InputEvent.InteractionKeyMappingTriggered e) {
            if (ClientCtl.active()) {
                e.setCanceled(true);
                e.setSwingHand(false);
            }
        }

        @SubscribeEvent
        public static void hand(RenderHandEvent e) {
            if (ClientCtl.active()) e.setCanceled(true);
        }

        @SubscribeEvent
        public static void overlay(RenderGuiOverlayEvent.Pre e) {
            if (!ClientCtl.active()) return;
            var id = e.getOverlay().id();
            if (id.equals(VanillaGuiOverlay.HOTBAR.id()) || id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
                    || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id()) || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())
                    || id.equals(VanillaGuiOverlay.AIR_LEVEL.id()) || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
                    || id.equals(VanillaGuiOverlay.JUMP_BAR.id()) || id.equals(VanillaGuiOverlay.ITEM_NAME.id())
                    || id.equals(VanillaGuiOverlay.CROSSHAIR.id()) || id.equals(VanillaGuiOverlay.MOUNT_HEALTH.id()))
                e.setCanceled(true);
        }

        @SubscribeEvent
        public static void fov(ViewportEvent.ComputeFov e) {
            if (ClientCtl.active()) e.setFOV(e.getFOV() / ClientCtl.zoom());
        }
    }
}
