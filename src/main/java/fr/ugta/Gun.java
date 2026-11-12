package fr.ugta;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Tir "hitscan" utilise par les policiers, l'armee et l'helicoptere. */
public class Gun {
    public static void shoot(ServerLevel level, Entity shooter, Vec3 origin, Vec3 target, float dmg, double spread, SoundEvent snd, float pitch) {
        Vec3 dir = target.subtract(origin).normalize();
        var r = level.random;
        dir = dir.add(r.nextGaussian() * spread, r.nextGaussian() * spread, r.nextGaussian() * spread).normalize();
        Vec3 end = origin.add(dir.scale(70));
        BlockHitResult bh = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 stop = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, shooter, origin, stop,
                new AABB(origin, stop).inflate(1.0), e -> e.isPickable() && !e.isSpectator() && e != shooter
                        && !(e instanceof Cop) && !(e instanceof Vehicle v && v.getPassengers().contains(shooter)), 0.3f);
        if (eh != null) {
            stop = eh.getLocation();
            Entity t = eh.getEntity();
            DamageSource src = shooter instanceof LivingEntity le ? level.damageSources().mobAttack(le) : level.damageSources().generic();
            t.invulnerableTime = 0;
            t.hurt(src, dmg);
            if (t instanceof LivingEntity lt) lt.knockback(0.25, -dir.x, -dir.z);
        }
        double dist = origin.distanceTo(stop);
        int n = (int) Math.min(30, dist / 2.0);
        for (int k = 1; k <= n; k++) {
            Vec3 p = origin.add(dir.scale(k * 2.0));
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.SMOKE, origin.x + dir.x, origin.y + dir.y, origin.z + dir.z, 2, 0.05, 0.05, 0.05, 0.0);
        level.playSound(null, origin.x, origin.y, origin.z, snd, SoundSource.HOSTILE, 1.4f, pitch);
    }
}
