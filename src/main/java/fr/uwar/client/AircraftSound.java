package fr.uwar.client;

import fr.uwar.Aircraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class AircraftSound extends AbstractTickableSoundInstance {
    private final Aircraft a;

    public AircraftSound(Aircraft a, SoundEvent ev) {
        super(ev, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.a = a;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0001f;
        this.x = a.getX();
        this.y = a.getY();
        this.z = a.getZ();
    }

    @Override
    public void tick() {
        if (a.isRemoved()) {
            stop();
            return;
        }
        x = a.getX();
        y = a.getY();
        z = a.getZ();
        if (!a.engineOn()) {
            volume = 0.0001f;
            return;
        }
        float t = a.thr();
        if (a.kind.heli()) {
            volume = 3.0f;
            pitch = 0.85f + t * 0.3f;
        } else {
            volume = 3.2f * (0.4f + 0.6f * Math.min(1f, t + (a.boosting() ? 0.4f : 0f)));
            pitch = 0.7f + t * 0.45f + (a.boosting() ? 0.15f : 0f);
        }
    }
}
