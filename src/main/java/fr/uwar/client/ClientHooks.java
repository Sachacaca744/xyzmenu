package fr.uwar.client;

import fr.uwar.Aircraft;
import fr.uwar.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/** Lecture du clavier / de la souris pour piloter (cote client). */
public class ClientHooks {
    public static void poll(Aircraft a) {
        Minecraft mc = Minecraft.getInstance();
        Options o = mc.options;
        if (mc.player == null) return;
        boolean ok = mc.screen == null;
        a.inF = ok ? (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0) : 0;
        a.inS = ok ? (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0) : 0;
        a.inU = ok ? (o.keyJump.isDown() ? 1 : 0) - (o.keySprint.isDown() ? 1 : 0) : 0;
        a.inBoost = ok && o.keyJump.isDown();
        a.inBrake = ok && o.keySprint.isDown();
        a.inFire = ok && o.keyAttack.isDown();
        a.aimYaw = mc.player.getYRot();
        a.aimPitch = mc.player.getXRot();
        Net.CH.sendToServer(new Net.InputMsg(a.thrust, a.inBoost, a.inFire, a.inU > 0, a.aimYaw, a.aimPitch));
    }

    public static Aircraft mine() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof Aircraft a && a.getControllingPassenger() == mc.player) return a;
        return null;
    }
}
