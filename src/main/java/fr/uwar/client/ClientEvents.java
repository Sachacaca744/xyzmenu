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
import net.minecraftforge.client.event.ViewportEvent;
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
    public static final KeyMapping LOADOUT = new KeyMapping("key.uwar.loadout", InputConstants.KEY_5, CAT);
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
            e.register(LOADOUT);
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
        static int lastSlot = -1;

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mcc = Minecraft.getInstance();
            if (mcc.player != null && mcc.player.getVehicle() instanceof Aircraft ac) {
                if (LOADOUT.consumeClick() && mcc.screen == null) {
                    if (lastSlot >= 0) mcc.player.getInventory().selected = lastSlot;
                    mcc.setScreen(new LoadoutScreen(ac));
                }
                lastSlot = mcc.player.getInventory().selected;
            } else {
                while (LOADOUT.consumeClick()) {
                }
                lastSlot = -1;
            }
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

        private static java.lang.reflect.Method MOVE;
        private static boolean moveTried;

        /** Recule la camera de la 3e personne : sinon elle est a l'interieur du fuselage. */
        @SubscribeEvent
        public static void camDist(ViewportEvent.ComputeCameraAngles e) {
            var cam = e.getCamera();
            if (!cam.isDetached() || cam.getEntity() == null || !(cam.getEntity().getVehicle() instanceof Aircraft a)) return;
            if (!moveTried) {
                moveTried = true;
                for (String n : new String[]{"m_90568_", "move"}) {
                    try {
                        MOVE = net.minecraft.client.Camera.class.getDeclaredMethod(n, double.class, double.class, double.class);
                        MOVE.setAccessible(true);
                        break;
                    } catch (Throwable t) {
                        MOVE = null;
                    }
                }
                System.out.println("UWAR: reglage camera 3e personne " + (MOVE != null ? "ok" : "indisponible"));
            }
            if (MOVE == null) return;
            try {
                MOVE.invoke(cam, a.kind.heli() ? -9.0 : -11.0 * Math.max(0.7, a.kind.sc) * a.kind.camMul(), a.kind.heli() ? 1.5 : 1.8 * Math.max(0.7, a.kind.sc) * a.kind.camMul(), 0.0);
            } catch (Throwable t) {
                MOVE = null;
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
