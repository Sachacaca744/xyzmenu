package fr.upolice;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class Util {
    public static DamageSource src(LivingEntity user) {
        return user instanceof Player p ? user.level().damageSources().playerAttack(p)
                : user.level().damageSources().mobAttack(user);
    }

    /** Degats qui ne tuent jamais (laisse au moins 1 PV). */
    public static void hurt(LivingEntity t, LivingEntity by, float dmg) {
        float d = Math.min(dmg, t.getHealth() - 1.0f);
        if (d > 0) {
            t.invulnerableTime = 0;
            t.hurt(src(by), d);
        }
    }

    public static Vec3 end(LivingEntity user, Vec3 o, Vec3 d, double range) {
        Vec3 e = o.add(d.scale(range));
        BlockHitResult bh = user.level().clip(new ClipContext(o, e, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        return bh.getType() == HitResult.Type.MISS ? e : bh.getLocation();
    }

    /** Entite visee (les blocs arretent le tir). */
    public static EntityHitResult pick(LivingEntity user, Vec3 o, Vec3 d, double range) {
        Vec3 stop = end(user, o, d, range);
        return ProjectileUtil.getEntityHitResult(user.level(), user, o, stop, new AABB(o, stop).inflate(1.0),
                e -> e.isPickable() && !e.isSpectator() && e != user, 0.3f);
    }

    public static void trace(ServerLevel sl, Vec3 o, Vec3 end, ParticleOptions p, double step, int max) {
        double dist = o.distanceTo(end);
        Vec3 d = end.subtract(o).normalize();
        int n = (int) Math.min(max, dist / step);
        for (int i = 1; i <= n; i++) {
            Vec3 q = o.add(d.scale(i * step));
            sl.sendParticles(p, q.x, q.y, q.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    public static void calm(LivingEntity t) {
        if (t instanceof Mob m) m.setTarget(null);
    }
}
