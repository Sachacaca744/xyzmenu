package fr.ugta;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Passant (PNJ) : se promene sur les trottoirs et fuit les armes. */
public class Ped extends PathfinderMob {
    public static final int VARIANTS = 8;
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(Ped.class, EntityDataSerializers.INT);

    public Ped(EntityType<? extends Ped> type, Level level) {
        super(type, level);
        entityData.set(VARIANT, random.nextInt(VARIANTS));
    }

    public static AttributeSupplier.Builder attrs() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(VARIANT, 0);
    }

    public int variant() {
        return entityData.get(VARIANT);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("variant", variant());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        if (t.contains("variant")) entityData.set(VARIANT, t.getInt("variant"));
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.7));
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 10f, 1.0, 1.6, p -> p instanceof Player pl && Wanted.isWeapon(pl.getMainHandItem())));
        goalSelector.addGoal(3, new DodgeCarGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return true;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource s) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    /** S'ecarte des vehicules qui roulent vite. */
    private class DodgeCarGoal extends Goal {
        private Vec3 away;

        @Override
        public boolean canUse() {
            if (tickCount % 4 != 0) return false;
            for (Vehicle v : level().getEntitiesOfClass(Vehicle.class, getBoundingBox().inflate(8, 3, 8))) {
                double mv = Math.abs(v.getX() - v.xo) + Math.abs(v.getZ() - v.zo);
                if (mv > 0.25) {
                    Vec3 d = position().subtract(v.position());
                    Vec3 side = new Vec3(-d.z, 0, d.x).normalize().scale(6);
                    away = position().add(side.add(d.normalize().scale(3)));
                    return true;
                }
            }
            return false;
        }

        @Override
        public void start() {
            getNavigation().moveTo(away.x, away.y, away.z, 1.8);
        }

        @Override
        public boolean canContinueToUse() {
            return !getNavigation().isDone();
        }
    }
}
