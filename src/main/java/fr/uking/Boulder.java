package fr.uking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Rocher de catapulte : trajectoire balistique, degats de zone, destruction de blocs. */
public class Boulder extends ThrowableItemProjectile {
    public static final float G = 0.05f;

    public Boulder(EntityType<? extends Boulder> t, Level l) { super(t, l); }

    @Override protected Item getDefaultItem() { return Reg.BOULDER.get(); }
    @Override protected float getGravity() { return G; }

    /** Vitesse initiale (angle fixe) pour atteindre horizontalement d et dy en tenant compte de la trainee. */
    public static double speedFor(double d, double dy, double angle) {
        double lo = 0.3, hi = 5.0;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (range(mid, angle, dy) < d) lo = mid; else hi = mid;
        }
        return (lo + hi) / 2;
    }

    static double range(double s, double angle, double dy) {
        double x = 0, y = 0, vx = s * Math.cos(angle), vy = s * Math.sin(angle);
        for (int i = 0; i < 600; i++) {
            x += vx; y += vy;
            vx *= 0.99; vy *= 0.99; vy -= G;
            if (vy < 0 && y < dy) return x;
        }
        return x;
    }

    public void launchAt(Vec3 from, Vec3 target) {
        double dx = target.x - from.x, dz = target.z - from.z, d = Math.sqrt(dx * dx + dz * dz);
        double ang = Math.toRadians(d > 55 ? 48 : 58);
        double sp = Math.min(speedFor(Math.max(d, 1), target.y - from.y, ang), 4.4);
        setPos(from.x, from.y, from.z);
        setDeltaMovement(dx / Math.max(d, 0.001) * sp * Math.cos(ang), sp * Math.sin(ang), dz / Math.max(d, 0.001) * sp * Math.cos(ang));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
        if (!level().isClientSide && getY() < level().getMinBuildHeight() - 10) discard();
    }

    @Override
    protected void onHit(HitResult r) {
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) return;
        Vec3 p = r.getLocation();
        String key = getOwner() instanceof Catapult c ? c.ownerKey() : getOwner() != null ? getOwner().getUUID().toString() : "";
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(4.5))) {
            double d = e.position().distanceTo(p);
            if (d > 4.5) continue;
            if (e instanceof Soldier s && s.key().equals(key)) continue;
            if (e instanceof Catapult c && c.ownerKey().equals(key)) continue;
            float dmg = (float) (22 * (1 - d / 5.0));
            e.hurt(damageSources().thrown(this, getOwner() instanceof Catapult ? this : getOwner()), dmg);
            Vec3 k = e.position().subtract(p).normalize().scale(0.6).add(0, 0.4, 0);
            e.setDeltaMovement(e.getDeltaMovement().add(k));
        }
        if (Cfg.SIEGE_BLOCKS.get()) {
            Fiefs fs = Fiefs.get(sl);
            BlockPos c = BlockPos.containing(p);
            int R = 3;
            for (int x = -R; x <= R; x++) for (int y = -R; y <= R; y++) for (int z = -R; z <= R; z++) {
                if (x * x + y * y + z * z > 9) continue;
                BlockPos b = c.offset(x, y, z);
                BlockState st = sl.getBlockState(b);
                if (st.isAir() || st.getDestroySpeed(sl, b) < 0 || st.getDestroySpeed(sl, b) > 6) continue;
                Fief f = fs.at(b);
                if (f != null && !f.key().equals(key) && !fs.war(key, f.key())) continue;
                sl.setBlock(b, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.POOF, p.x, p.y + 0.5, p.z, 25, 1.5, 1, 1.5, 0.05);
        sl.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2f, 0.8f);
        discard();
    }
}
