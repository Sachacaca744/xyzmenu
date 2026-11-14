package fr.udrone.client;

import fr.udrone.Drone;
import fr.udrone.Net;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.world.entity.Entity;

/** Etat cote client du pilotage : camera sur le drone, lecture des touches, envoi des commandes. */
public class ClientCtl {
    private static int id = -1, wait;
    private static Drone cur;
    private static CameraType prevCam = CameraType.FIRST_PERSON;
    public static final float[] ZOOMS = {1f, 2f, 4f};
    public static int zoomIdx;

    public static boolean active() { return cur != null; }
    public static Drone drone() { return cur; }
    public static float zoom() { return ZOOMS[zoomIdx]; }

    public static void set(int newId) {
        if (newId < 0) {
            id = -1;
            end();
        } else {
            id = newId;
            wait = 0;
        }
    }

    private static void begin(Minecraft mc, Drone d) {
        cur = d;
        prevCam = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        d.localCtl = true;
        d.camSource = mc.player;
        mc.player.setYRot(d.getYRot());
        mc.player.yRotO = d.getYRot();
        mc.player.setXRot(0);
        mc.player.xRotO = 0;
        mc.setCameraEntity(d);
        mc.setScreen(null);
        zoomIdx = 0;
    }

    public static void end() {
        Minecraft mc = Minecraft.getInstance();
        if (cur != null) {
            cur.localCtl = false;
            cur.camSource = null;
        }
        if (cur != null && mc.player != null) {
            mc.setCameraEntity(mc.player);
            mc.options.setCameraType(prevCam);
        }
        cur = null;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            cur = null;
            id = -1;
            return;
        }
        if (cur == null && id >= 0) {
            Entity e = mc.level.getEntity(id);
            if (e instanceof Drone d) begin(mc, d);
            else if (++wait > 60) id = -1;
            return;
        }
        if (cur == null) return;
        if (cur.isRemoved() || cur.level() != mc.level) {
            end();
            id = -1;
            return;
        }
        if (mc.getCameraEntity() != cur) mc.setCameraEntity(cur);
        Options o = mc.options;
        float f = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
        float s = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
        float u = (o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0);
        boolean fire = o.keyAttack.isDown() && mc.screen == null;
        Net.CH.sendToServer(new Net.InputMsg(f, s, u, mc.player.getYRot(), mc.player.getXRot(), fire));
    }
}
