package fr.ugta.client;

import fr.ugta.Vehicle;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class VehicleSound extends AbstractTickableSoundInstance {
    private final Vehicle v;
    private final int mode; // 0 moteur, 1 sirene, 2 rotor, 3 helice

    public VehicleSound(Vehicle v, SoundEvent ev, int mode) {
        super(ev, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.v = v;
        this.mode = mode;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0001f;
        this.x = v.getX();
        this.y = v.getY();
        this.z = v.getZ();
    }

    @Override
    public void tick() {
        if (v.isRemoved()) {
            stop();
            return;
        }
        x = v.getX();
        y = v.getY();
        z = v.getZ();
        double sp = Math.hypot(v.getX() - v.xo, v.getZ() - v.zo);
        boolean on = v.engineOn();
        switch (mode) {
            case 0 -> {
                volume = on ? 0.35f + (float) Math.min(0.5, sp * 0.5) : 0.0001f;
                pitch = 0.6f + (float) Math.min(1.3, sp * 1.3);
            }
            case 1 -> {
                volume = v.siren() ? 1.0f : 0.0001f;
                pitch = 1.0f;
            }
            case 2 -> {
                volume = on ? 1.6f : 0.0001f;
                pitch = 0.9f + (float) Math.min(0.3, sp * 0.3);
            }
            default -> {
                volume = on ? 1.2f : 0.0001f;
                pitch = 0.7f + (float) Math.min(0.8, sp * 0.6);
            }
        }
    }
}
