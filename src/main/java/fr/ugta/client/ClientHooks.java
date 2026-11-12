package fr.ugta.client;

import fr.ugta.Vehicle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/** Lecture du clavier pour piloter un vehicule (cote client). */
public class ClientHooks {
    public static void poll(Vehicle v) {
        Options o = Minecraft.getInstance().options;
        if (Minecraft.getInstance().screen != null) return;
        v.in_f = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
        v.in_t = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
        v.in_u = (o.keyJump.isDown() ? 1 : 0) - (o.keySprint.isDown() ? 1 : 0);
        v.in_b = o.keyJump.isDown();
    }
}
