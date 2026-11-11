package fr.uarms;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Tirs "hitscan" communs aux joueurs et aux soldats. */
public class Fire {
    public static DamageSource src(LivingEntity shooter) {
        return shooter instanceof Player p ? shooter.level().damageSources().playerAttack(p)
                : shooter.level().damageSources().mobAttack(shooter);
    }

    /** Fait des degats sans jamais tuer (armes non letales). */
    public static boolean nonLethalHurt(LivingEntity target, DamageSource src, float dmg) {
        float d = Math.min(dmg, target.getHealth() - 1.0f);
        target.invulnerableTime = 0;
        if (d > 0) return target.hurt(src, d);
        return false;
    }

    private static boolean valid(LivingEntity shooter, Entity e) {
        if (!e.isPickable() || e.isSpectator() || e == shooter) return false;
        return !(shooter instanceof Soldier && e instanceof Soldier);
    }

    /** Premiere entite touchee sur un segment, en tenant compte des blocs. */
    public static EntityHitResult pick(LivingEntity shooter, Vec3 origin, Vec3 dir, double range) {
        Vec3 end = origin.add(dir.scale(range));
        BlockHitResult bh = shooter.level().clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 stop = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        return ProjectileUtil.getEntityHitResult(shooter.level(), shooter, origin, stop,
                new AABB(origin, stop).inflate(1.0), e -> valid(shooter, e), 0.3f);
    }

    public static void shoot(ServerLevel level, LivingEntity shooter, Vec3 origin, Vec3 dir, GunItem.Spec s, float dmgMult, float extraSpread) {
        RandomSource r = level.random;
        for (int i = 0; i < s.pellets(); i++) {
            double sp = s.spread() + extraSpread;
            Vec3 d = dir.add(r.nextGaussian() * sp, r.nextGaussian() * sp, r.nextGaussian() * sp).normalize();
            Vec3 end = origin.add(d.scale(s.range()));
            BlockHitResult bh = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
            Vec3 stop = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
            EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, shooter, origin, stop,
                    new AABB(origin, stop).inflate(1.0), e -> valid(shooter, e), 0.3f);
            if (eh != null) {
                stop = eh.getLocation();
                if (eh.getEntity() instanceof LivingEntity le) {
                    float dmg = s.damage() * dmgMult;
                    if (s.nonLethal()) {
                        nonLethalHurt(le, src(shooter), dmg);
                    } else {
                        if (stop.y > le.getEyeY() - 0.2) dmg *= 1.5f;
                        le.invulnerableTime = 0;
                        le.hurt(src(shooter), dmg);
                    }
                    if (s.knock() > 0) le.knockback(s.knock(), -d.x, -d.z);
                    if (s.onHit() != null) s.onHit().accept(le);
                }
            }
            // trace visuelle
            double dist = origin.distanceTo(stop);
            int n = (int) Math.min(40, dist / 1.5);
            for (int k = 1; k <= n; k++) {
                Vec3 p = origin.add(d.scale(k * 1.5));
                level.sendParticles(s.nonLethal() ? ParticleTypes.POOF : ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        level.sendParticles(ParticleTypes.SMOKE, origin.x + dir.x * 0.8, origin.y + dir.y * 0.8, origin.z + dir.z * 0.8, 3, 0.05, 0.05, 0.05, 0.01);
        SoundEvent snd = s.sound();
        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), snd, SoundSource.PLAYERS, 1.2f, s.pitch());
    }

    public static void mobFire(Soldier m, LivingEntity target, GunItem g) {
        if (!(m.level() instanceof ServerLevel sl)) return;
        Vec3 o = m.getEyePosition();
        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.6, 0);
        Vec3 dir = aim.subtract(o).normalize();
        shoot(sl, m, o, dir, g.spec(), 0.6f, 0.03f);
    }
}
