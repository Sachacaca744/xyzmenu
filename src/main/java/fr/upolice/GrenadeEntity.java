package fr.upolice;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Grenade lancee : se pose au sol puis agit selon son type. */
public class GrenadeEntity extends ThrowableItemProjectile {
    private boolean landed = false;
    private int age = 0;

    public GrenadeEntity(EntityType<? extends GrenadeEntity> t, Level l) {
        super(t, l);
    }

    public GrenadeEntity(Level l, LivingEntity thrower) {
        super(Reg.GRENADE.get(), thrower, l);
    }

    @Override
    protected Item getDefaultItem() {
        return Reg.GRENADE_TEAR.get();
    }

    private GrenadeItem.Kind kind() {
        return getItem().getItem() instanceof GrenadeItem g ? g.kind : GrenadeItem.Kind.TEAR;
    }

    @Override
    protected void onHit(HitResult r) {
        if (landed || level().isClientSide) return;
        landed = true;
        Vec3 p = r.getLocation();
        if (r instanceof BlockHitResult bh) p = p.add(bh.getDirection().getStepX() * 0.2, bh.getDirection().getStepY() * 0.2, bh.getDirection().getStepZ() * 0.2);
        setPos(p);
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        age = 0;
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    @Override
    public void tick() {
        if (!landed) {
            super.tick();
            if (!level().isClientSide && tickCount > 200) discard();
            return;
        }
        baseTick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) return;
        age++;
        // retombe si pose en l'air
        if (sl.getBlockState(blockPosition().below()).isAir()) setPos(getX(), getY() - 0.25, getZ());
        switch (kind()) {
            case TEAR -> tear(sl);
            case SMOKE -> smoke(sl);
            case FLASH -> flash(sl);
            case BURST -> burst(sl);
        }
    }

    private void tear(ServerLevel sl) {
        if (age > 200) {
            discard();
            return;
        }
        var dust = new DustParticleOptions(new Vector3f(0.95f, 0.95f, 0.65f), 3.0f);
        sl.sendParticles(dust, getX(), getY() + 0.8, getZ(), 12, 2.4, 0.9, 2.4, 0.0);
        sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.8, getZ(), 4, 2.0, 0.8, 2.0, 0.01);
        if (age % 10 == 0) {
            for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5.0), e -> e.isAlive() && e.distanceTo(this) <= 5.0)) {
                t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                Util.calm(t);
            }
        }
    }

    private void smoke(ServerLevel sl) {
        if (age > 300) {
            discard();
            return;
        }
        if (age % 2 == 0) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0, getZ(), 10, 2.8, 1.3, 2.8, 0.005);
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.0, getZ(), 5, 2.5, 1.2, 2.5, 0.003);
        }
    }

    private void flash(ServerLevel sl) {
        if (age < 8) return;
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6f, 1.7f);
        sl.sendParticles(ParticleTypes.FLASH, getX(), getY() + 0.5, getZ(), 3, 0.2, 0.2, 0.2, 0.0);
        sl.sendParticles(ParticleTypes.FIREWORK, getX(), getY() + 0.5, getZ(), 40, 1.0, 1.0, 1.0, 0.2);
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(10.0), e -> e.isAlive() && e.distanceTo(this) <= 10.0)) {
            double f = 1.0 - t.distanceTo(this) / 12.0;
            int dur = (int) (40 + 140 * f);
            t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, dur, 0));
            t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, dur, 0));
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur / 2, 1));
            Util.calm(t);
        }
        discard();
    }

    private void burst(ServerLevel sl) {
        if (age < 4) return;
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.3f);
        sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.5, getZ(), 40, 0.6, 0.4, 0.6, 0.25);
        sl.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.5, getZ(), 40, 0.6, 0.4, 0.6, 0.6);
        LivingEntity thrower = getOwner() instanceof LivingEntity o ? o : null;
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(6.0), e -> e.isAlive() && e.distanceTo(this) <= 6.0)) {
            double dx = t.getX() - getX(), dz = t.getZ() - getZ();
            t.knockback(1.6, -dx, -dz);
            if (thrower != null) Util.hurt(t, thrower, 3f);
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        }
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putBoolean("landed", landed);
        t.putInt("age", age);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        landed = t.getBoolean("landed");
        age = t.getInt("age");
        if (landed) setNoGravity(true);
    }
}
