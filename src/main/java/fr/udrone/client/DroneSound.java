package fr.udrone.client;

import fr.udrone.Drone;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class DroneSound extends AbstractTickableSoundInstance {
    private final Drone d;
    private final float base;

    public DroneSound(Drone d, SoundEvent ev, float base) {
        super(ev, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.d = d;
        this.base = base;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0001f;
        this.x = d.getX();
        this.y = d.getY();
        this.z = d.getZ();
    }

    @Override
    public void tick() {
        if (d.isRemoved()) {
            stop();
            return;
        }
        x = d.getX();
        y = d.getY();
        z = d.getZ();
        float t = d.throttle();
        double sp = Math.sqrt(Math.pow(d.getX() - d.xo, 2) + Math.pow(d.getY() - d.yo, 2) + Math.pow(d.getZ() - d.zo, 2));
        volume = t < 0.02f ? 0.0001f : (d.kind.wing ? 1.8f : 0.7f + (d.kind.id.startsWith("heavy") ? 0.5f : 0f)) * Math.min(1f, t + 0.2f);
        pitch = base * (0.75f + t * 0.35f + (float) Math.min(0.25, sp * 0.4));
    }
}
