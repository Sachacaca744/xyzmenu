package fr.udrone;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Explosion personnalisee : effets visuels, son, degats aux entites (blocs selon la config). */
public class Blast {
    public static void boom(ServerLevel sl, Vec3 p, float radius, float dmg, Entity src, Entity owner) {
        sl.playSound(null, p.x, p.y, p.z, Reg.BOOM.get(), SoundSource.NEUTRAL, 4.0f, 0.9f + sl.random.nextFloat() * 0.2f);
        sl.sendParticles(ParticleTypes.FLASH, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        if (radius >= 3f) sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        else sl.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 3, radius * 0.3, radius * 0.3, radius * 0.3, 0);
        int fl = (int) (radius * 14);
        sl.sendParticles(ParticleTypes.FLAME, p.x, p.y + 0.3, p.z, fl, radius * 0.35, radius * 0.25, radius * 0.35, 0.12);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.6, p.z, fl, radius * 0.4, radius * 0.4, radius * 0.4, 0.06);
        sl.sendParticles(ParticleTypes.LAVA, p.x, p.y + 0.3, p.z, (int) (radius * 6), radius * 0.3, 0.2, radius * 0.3, 0.0);
        for (int i = 0; i < 28; i++) {
            double a = i * Math.PI * 2 / 28;
            sl.sendParticles(ParticleTypes.CLOUD, p.x + Math.cos(a) * 0.4, p.y + 0.15, p.z + Math.sin(a) * 0.4, 0,
                    Math.cos(a), 0.02, Math.sin(a), radius * 0.28);
        }
        DamageSource ds = sl.damageSources().explosion(src, owner);
        double mult = Cfg.DAMAGE_MULT.get();
        AABB box = new AABB(p, p).inflate(radius);
        List<Entity> list = sl.getEntities(src, box, e -> e instanceof LivingEntity || e instanceof Drone);
        for (Entity e : list) {
            if (e == src) continue;
            double d = e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(p);
            if (d > radius) continue;
            float f = (float) (1.0 - d / radius);
            e.hurt(ds, (float) (dmg * f * mult));
            Vec3 push = e.position().subtract(p).normalize().scale(0.7 * f).add(0, 0.35 * f, 0);
            e.push(push.x, push.y, push.z);
            e.hurtMarked = true;
            if (radius >= 3f && f > 0.5f) e.setSecondsOnFire(2);
        }
        if (Cfg.BLOCK_DAMAGE.get()) sl.explode(null, p.x, p.y, p.z, radius * 0.8f, Level.ExplosionInteraction.TNT);
    }
}
