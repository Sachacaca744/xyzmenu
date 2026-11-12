package fr.ugta;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** Policier (ou soldat de l'armee a 5 etoiles). */
public class Cop extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> AIMING = SynchedEntityData.defineId(Cop.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ARMY = SynchedEntityData.defineId(Cop.class, EntityDataSerializers.BOOLEAN);

    private int shootCd, burst, burstGap, warnTicks, bustTicks, speakCd;
    private UUID warnTarget;

    public Cop(EntityType<? extends Cop> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        setMaxUpStep(1.0f);
        equipFor(false);
    }

    public static AttributeSupplier.Builder attrs() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.FOLLOW_RANGE, 100.0)
                .add(Attributes.ARMOR, 4.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(AIMING, false);
        entityData.define(ARMY, false);
    }

    public boolean isAiming() {
        return entityData.get(AIMING);
    }

    private void setAiming(boolean b) {
        if (b != isAiming()) entityData.set(AIMING, b);
    }

    public boolean isArmy() {
        return entityData.get(ARMY);
    }

    public void setArmy(boolean b) {
        entityData.set(ARMY, b);
        equipFor(b);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(b ? 40.0 : 30.0);
        setHealth(getMaxHealth());
    }

    private void equipFor(boolean army) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(army ? Reg.RIFLE_PROP.get() : Reg.PISTOL_PROP.get()));
        setDropChance(EquipmentSlot.MAINHAND, 0f);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putBoolean("army", isArmy());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        if (t.getBoolean("army")) setArmy(true);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new EnterCarGoal());
        goalSelector.addGoal(2, new PursueGoal());
        goalSelector.addGoal(3, new HoldGoal());
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.5));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
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

    // ---------------------------------------------------------------- logique

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (shootCd > 0) shootCd--;
        if (speakCd > 0) speakCd--;
        if (tickCount % 5 == 0) pickTarget();
        LivingEntity t = getTarget();
        if (t instanceof Player p && t.isAlive()) combat(p);
        else warnLogic();
    }

    private void pickTarget() {
        Player best = null;
        double bd = 100.0 * 100.0;
        for (Player p : level().players()) {
            if (p.isSpectator() || !p.isAlive() || Wanted.stars(p) <= 0) continue;
            double d = distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        if (best != getTarget()) setTarget(best);
    }

    private boolean isDriver() {
        return isPassenger() && getVehicle() instanceof Vehicle v && v.getControllingPassenger() == this;
    }

    private void combat(Player t) {
        warnTarget = null;
        warnTicks = 0;
        double d = distanceTo(t);
        boolean los = hasLineOfSight(t);
        boolean can = Wanted.canShoot(this, t);
        boolean aim = can && los && d < 40 && !isDriver();
        setAiming(aim);
        if (aim) {
            getLookControl().setLookAt(t, 30f, 30f);
            if (isArmy()) {
                if (burst > 0) {
                    if (--burstGap <= 0) {
                        fire(t, 3.0f, 0.05, d);
                        burst--;
                        burstGap = 3;
                    }
                } else if (shootCd <= 0) {
                    burst = 3;
                    burstGap = 0;
                    shootCd = 30;
                }
            } else if (shootCd <= 0) {
                fire(t, 3.5f, 0.035, d);
                shootCd = 18 + random.nextInt(10);
            }
            bustTicks = 0;
        } else if (!isPassenger() && d < 2.8 && los) {
            boolean fast = t.getVehicle() instanceof Vehicle v && Math.abs(v.getX() - v.xo) + Math.abs(v.getZ() - v.zo) > 0.3;
            if (!fast && ++bustTicks >= 30 && t instanceof ServerPlayer sp) {
                bustTicks = 0;
                Wanted.bust(sp, this);
            }
        } else {
            bustTicks = Math.max(0, bustTicks - 2);
        }
    }

    private void fire(Player t, float dmg, double spread, double d) {
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 target = t.position().add(0, t.getBbHeight() * 0.6, 0);
        Gun.shoot(sl, this, getEyePosition(), target, dmg, spread * (1.0 + d / 30.0), Reg.SHOT.get(), isArmy() ? 0.85f : 1.1f);
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    /** Sans cible : surveille les joueurs armes et leur ordonne de poser leur arme. */
    private void warnLogic() {
        setAiming(false);
        if (isPassenger() || tickCount % 5 != 0) return;
        Player w = null;
        double bd = 20.0 * 20.0;
        for (Player p : level().players()) {
            if (p.isSpectator() || !p.isAlive() || !Wanted.isWeapon(p.getMainHandItem())) continue;
            double d = distanceToSqr(p);
            if (d < bd && hasLineOfSight(p)) {
                bd = d;
                w = p;
            }
        }
        if (w != null) {
            if (warnTarget == null || !warnTarget.equals(w.getUUID())) {
                warnTarget = w.getUUID();
                warnTicks = 0;
                speakCd = 0;
            }
            warnTicks += 5;
            setAiming(true);
            getLookControl().setLookAt(w, 30f, 30f);
            getNavigation().stop();
            if (speakCd <= 0) {
                w.sendSystemMessage(Component.literal("§9[Police] §fPosez votre arme ! Tout de suite !"));
                speakCd = 60;
            }
            if (warnTicks > 160 && w instanceof ServerPlayer sp) {
                Wanted.raise(sp, 1, "Menace avec une arme");
                warnTarget = null;
                warnTicks = 0;
            }
        } else if (warnTarget != null) {
            Player p = level().getPlayerByUUID(warnTarget);
            if (p instanceof ServerPlayer sp && p.isAlive() && Wanted.isWeapon(p.getMainHandItem()) && warnTicks > 20) {
                Wanted.raise(sp, 2, "Fuite avec une arme");
            } else if (p != null && !Wanted.isWeapon(p.getMainHandItem())) {
                p.sendSystemMessage(Component.literal("§9[Police] §fMerci. Circulez."));
            }
            warnTarget = null;
            warnTicks = 0;
        }
    }

    // ---------------------------------------------------------------- objectifs

    private class HoldGoal extends Goal {
        HoldGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return warnTarget != null && getTarget() == null && !isPassenger();
        }

        @Override
        public void tick() {
            getNavigation().stop();
        }
    }

    private class PursueGoal extends Goal {
        PursueGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive() && !isPassenger();
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) return;
            double d = distanceTo(t);
            boolean can = t instanceof Player p && Wanted.canShoot(Cop.this, p);
            if (can && hasLineOfSight(t) && d < 24) {
                getNavigation().stop();
            } else if (tickCount % 8 == 0 || getNavigation().isDone()) {
                getNavigation().moveTo(t, 1.25);
            }
        }
    }

    private class EnterCarGoal extends Goal {
        private Vehicle car;

        EnterCarGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            if (t == null || isPassenger() || distanceToSqr(t) < 35 * 35) return false;
            List<Vehicle> l = level().getEntitiesOfClass(Vehicle.class, getBoundingBox().inflate(40),
                    v -> v.kind == Kind.POLICE && v.getPassengers().size() < v.kind.seats);
            car = null;
            double bd = 1e9;
            for (Vehicle v : l) {
                double d = distanceToSqr(v);
                if (d < bd) {
                    bd = d;
                    car = v;
                }
            }
            return car != null;
        }

        @Override
        public boolean canContinueToUse() {
            return car != null && car.isAlive() && car.getPassengers().size() < car.kind.seats && !isPassenger()
                    && getTarget() != null && distanceToSqr(car) < 60 * 60;
        }

        @Override
        public void tick() {
            if (distanceTo(car) < 3.2) {
                startRiding(car);
            } else if (tickCount % 10 == 0 || getNavigation().isDone()) {
                BlockPos p = car.blockPosition();
                getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 1.35);
            }
        }
    }
}
