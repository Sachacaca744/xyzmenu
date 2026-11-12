package fr.ugta;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;
import java.util.UUID;

/** Voiture, camion, avion ou helicoptere. */
public class Vehicle extends Entity {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HP = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> ENGINE = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SIREN = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.BOOLEAN);

    public static final float MAX_HP = 80f;
    public final Kind kind;

    // commandes (clavier cote client, IA cote serveur)
    public float in_f, in_t, in_u;
    public boolean in_b;
    public double speed;
    public float throttle;
    public boolean temp;
    public UUID targetId;

    // animation (client)
    public float wheelRot, wheelRotO, rotor, rotorO;
    private int hornCd, stuck, reverse, leaving, crashCd;
    private double prevV;
    private float heliAngle;

    private int lerpSteps;
    private double lx, ly, lz;
    private float lyr, lxr;

    public Vehicle(EntityType<?> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
        this.blocksBuilding = true;
        setMaxUpStep(1.0f);
        heliAngle = level.random.nextFloat() * 6.28f;
        if (!level.isClientSide) {
            int[] cols = {0xC8281E, 0x1E5AC8, 0xE6E6E6, 0x202020, 0xE8B810, 0x2E8B3C, 0xE06A10, 0x8A8F96, 0x7A1E8C};
            entityData.set(COLOR, cols[level.random.nextInt(cols.length)]);
        }
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(COLOR, 0xC8281E);
        entityData.define(HP, MAX_HP);
        entityData.define(ENGINE, false);
        entityData.define(SIREN, false);
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public void setColor(int c) {
        entityData.set(COLOR, c);
    }

    public boolean engineOn() {
        return entityData.get(ENGINE);
    }

    public boolean siren() {
        return entityData.get(SIREN);
    }

    public void setSiren(boolean b) {
        entityData.set(SIREN, b);
    }

    public float hp() {
        return entityData.get(HP);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        if (t.contains("color")) entityData.set(COLOR, t.getInt("color"));
        if (t.contains("hp")) entityData.set(HP, t.getFloat("hp"));
        temp = t.getBoolean("temp");
        if (t.hasUUID("target")) targetId = t.getUUID("target");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putInt("color", color());
        t.putFloat("hp", hp());
        t.putBoolean("temp", temp);
        if (targetId != null) t.putUUID("target", targetId);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // ---------------------------------------------------------------- interaction

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Reg.vehicleItem(kind));
    }

    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        if (p.isSecondaryUseActive()) return InteractionResult.PASS;
        if (kind == Kind.POLICE_HELI) return InteractionResult.PASS;
        if (!level().isClientSide) return p.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return getPassengers().size() < kind.seats;
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof LivingEntity le ? le : null;
    }

    private double[] seat(int i) {
        // {lateral(gauche +), hauteur, avant}
        return switch (kind) {
            case TRUCK -> new double[]{i == 0 ? 0.55 : -0.55, 1.15, 1.9};
            case PLANE -> new double[]{0, 0.55, 0.3};
            case HELI, POLICE_HELI -> new double[]{0, 0.5, 0.5};
            case SPORTS -> new double[]{i == 0 ? 0.45 : -0.45, 0.1, 0.2};
            default -> new double[]{i == 0 ? 0.45 : -0.45, 0.2, 0.3};
        };
    }

    @Override
    protected void positionRider(Entity p, MoveFunction cb) {
        if (!hasPassenger(p)) return;
        double[] s = seat(Math.max(0, getPassengers().indexOf(p)));
        double a = getYRot() * Mth.DEG_TO_RAD;
        double fx = -Math.sin(a), fz = Math.cos(a), lxv = Math.cos(a), lzv = Math.sin(a);
        cb.accept(p, getX() + fx * s[2] + lxv * s[0], getY() + s[1], getZ() + fz * s[2] + lzv * s[0]);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity p) {
        double a = getYRot() * Mth.DEG_TO_RAD;
        double lxv = Math.cos(a), lzv = Math.sin(a);
        double d = kind.w * 0.5 + 0.9;
        double[][] c = {{lxv * d, lzv * d}, {-lxv * d, -lzv * d}, {-Math.sin(a) * -3, Math.cos(a) * -3}};
        for (double[] o : c) {
            Vec3 pos = new Vec3(getX() + o[0], getY(), getZ() + o[1]);
            AABB bb = p.getBoundingBox().move(pos.subtract(p.position()));
            if (level().noCollision(p, bb)) return pos;
        }
        return new Vec3(getX(), getBoundingBox().maxY + 0.2, getZ());
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide || isRemoved()) return false;
        if (src.getEntity() != null && hasPassenger(src.getEntity())) return false;
        if (src.getEntity() instanceof Player p && p.getAbilities().instabuild && p.isShiftKeyDown()) {
            ejectPassengers();
            discard();
            return true;
        }
        float h = hp() - amount;
        entityData.set(HP, h);
        if (h <= 0) blowUp();
        return true;
    }

    private void blowUp() {
        ejectPassengers();
        if (level() instanceof ServerLevel sl) {
            sl.explode(this, getX(), getY() + 0.5, getZ(), 2.5f, Level.ExplosionInteraction.NONE);
        }
        discard();
    }

    public void horn() {
        if (hornCd > 0) return;
        hornCd = 12;
        level().playSound(null, getX(), getY(), getZ(), Reg.HORN.get(), SoundSource.NEUTRAL, 1.6f, kind == Kind.TRUCK ? 0.7f : 1.0f);
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();
        if (hornCd > 0) hornCd--;
        if (!level().isClientSide) serverTick();
        tickLerp();
        LivingEntity c = getControllingPassenger();
        if (isControlledByLocalInstance()) {
            in_f = in_t = in_u = 0;
            in_b = false;
            if (level().isClientSide) {
                if (c instanceof Player) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.ugta.client.ClientHooks.poll(this));
            } else {
                VehicleAI.drive(this, c);
            }
            physics(c);
        } else {
            setDeltaMovement(Vec3.ZERO);
        }
        if (level().isClientSide) animate();
    }

    private void animate() {
        double dx = getX() - xo, dz = getZ() - zo;
        double v = Math.sqrt(dx * dx + dz * dz);
        double fwd = dx * -Math.sin(getYRot() * Mth.DEG_TO_RAD) + dz * Math.cos(getYRot() * Mth.DEG_TO_RAD);
        wheelRotO = wheelRot;
        wheelRot += (float) (fwd >= 0 ? v : -v) * 55f;
        rotorO = rotor;
        boolean on = engineOn();
        rotor += on ? 62f : 0f;
        if (rotor > 3600f) {
            rotor -= 3600f;
            rotorO -= 3600f;
        }
    }

    private void serverTick() {
        LivingEntity c = getControllingPassenger();
        boolean on = c != null || kind == Kind.POLICE_HELI;
        if (on != engineOn()) entityData.set(ENGINE, on);
        if (!on && kind == Kind.POLICE_HELI) discard();
        if (temp && getPassengers().isEmpty() && level().getNearestPlayer(this, 140) == null) {
            discard();
            return;
        }
        if (isInLava() || isOnFire()) hurt(level().damageSources().inFire(), 0.5f);
        if (getY() < level().getMinBuildHeight() - 20) discard();

        double dx = getX() - xo, dz = getZ() - zo;
        double v = Math.sqrt(dx * dx + dz * dz);
        if (crashCd > 0) crashCd--;
        if (kind.cat != 2 && prevV > 0.5 && v < prevV * 0.4 && crashCd == 0) {
            crashCd = 10;
            float dmg = (float) ((prevV - 0.4) * 45);
            level().playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 0.7f, 1.6f);
            for (Entity p : getPassengers()) if (prevV > 0.75) p.hurt(level().damageSources().flyIntoWall(), (float) ((prevV - 0.7) * 28));
            hurt(level().damageSources().generic(), dmg);
        }
        prevV = v;

        // ecraser les pietons (voiture ou avion au sol / helico bas)
        if (v > 0.22 && !isRemoved()) {
            LivingEntity drv = getControllingPassenger();
            AABB box = getBoundingBox().inflate(0.35, 0.1, 0.35);
            List<LivingEntity> list = level().getEntitiesOfClass(LivingEntity.class, box,
                    e -> e.isAlive() && !hasPassenger(e) && !(e == drv) && !e.isPassenger());
            for (LivingEntity e : list) {
                DamageSource src = drv instanceof Player p ? level().damageSources().playerAttack(p)
                        : drv != null ? level().damageSources().mobAttack(drv) : level().damageSources().generic();
                e.invulnerableTime = 0;
                e.hurt(src, (float) (v * 20));
                Vec3 d = new Vec3(dx, 0, dz).normalize().scale(0.9);
                e.setDeltaMovement(d.x, 0.4, d.z);
                e.hurtMarked = true;
            }
        }
    }

    // ---------------------------------------------------------------- physique

    private void physics(LivingEntity c) {
        switch (kind.cat) {
            case 0 -> road(c);
            case 1 -> plane(c);
            default -> heli(c);
        }
    }

    private void road(LivingEntity c) {
        double max = kind.max;
        if (in_f > 0) speed += kind.acc * in_f;
        else if (in_f < 0) speed += kind.acc * in_f * (speed > 0.05 ? 1.8 : 0.8);
        if (in_b) speed *= 0.86;
        speed *= in_f == 0 ? 0.975 : 0.996;
        speed = Mth.clamp(speed, -max * 0.4, max);
        if (Math.abs(speed) < 0.004 && in_f == 0) speed = 0;
        float dy = -in_t * kind.turn * (float) Mth.clamp(Math.abs(speed) / 0.3, 0, 1);
        setYRot(getYRot() + dy);
        if (level().isClientSide && c instanceof Player p && dy != 0) {
            p.setYRot(p.getYRot() + dy);
            p.yRotO += dy;
            p.setYHeadRot(p.getYHeadRot() + dy);
        }
        Vec3 f = Vec3.directionFromRotation(0, getYRot());
        double vy = getDeltaMovement().y;
        if (isInWater()) {
            speed *= 0.85;
            vy = Math.min(vy + 0.04, 0.08);
        } else if (onGround()) vy = -0.08;
        else vy = (vy - 0.08) * 0.98;
        setDeltaMovement(f.x * speed, vy, f.z * speed);
        move(MoverType.SELF, getDeltaMovement());
        if (horizontalCollision) speed *= 0.3;
    }

    private void plane(LivingEntity c) {
        if (c == null) throttle = Math.max(0, throttle - 0.02f);
        else throttle = Mth.clamp(throttle + in_f * 0.012f, 0f, 1f);
        double target = throttle * kind.max;
        speed += (target - speed) * (target > speed ? 0.02 : 0.012);
        if (c != null) {
            float dyy = Mth.wrapDegrees(c.getYRot() - getYRot());
            setYRot(getYRot() + Mth.clamp(dyy, -2.2f, 2.2f));
            float tp = Mth.clamp(c.getXRot(), -40f, 40f);
            setXRot(getXRot() + Mth.clamp(tp - getXRot(), -1.8f, 1.8f));
        }
        if (speed < 0.5) {
            if (onGround()) setXRot(Math.max(getXRot(), 0f));
            else setXRot(Math.min(40f, getXRot() + 1.2f));
        }
        Vec3 f = Vec3.directionFromRotation(getXRot(), getYRot()).scale(speed);
        double stall = Mth.clamp((0.5 - speed) / 0.5, 0, 1);
        double vy = f.y - stall * 0.32;
        if (onGround() && vy < 0) vy = -0.08;
        setDeltaMovement(f.x, vy, f.z);
        move(MoverType.SELF, getDeltaMovement());
        if (horizontalCollision) speed *= 0.5;
    }

    private void heli(LivingEntity c) {
        boolean ai = kind == Kind.POLICE_HELI;
        boolean engine = c != null || ai;
        Vec3 v = getDeltaMovement();
        double vy = v.y;
        if (engine) {
            if (in_u > 0) vy = Math.min(vy + 0.05, 0.55);
            else if (in_u < 0) vy = Math.max(vy - 0.05, -0.5);
            else vy *= 0.85;
        } else vy = (vy - 0.06) * 0.98;
        if (c != null) {
            float dyy = Mth.wrapDegrees(c.getYRot() - getYRot());
            setYRot(getYRot() + Mth.clamp(dyy, -4f, 4f));
        }
        double a = getYRot() * Mth.DEG_TO_RAD;
        double hx, hz;
        if (ai) {
            hx = Mth.lerp(0.08, v.x, aiVx);
            hz = Mth.lerp(0.08, v.z, aiVz);
        } else {
            double ax = -Math.sin(a) * in_f * 0.05 + Math.cos(a) * in_t * 0.035;
            double az = Math.cos(a) * in_f * 0.05 + Math.sin(a) * in_t * 0.035;
            hx = (v.x + ax) * 0.94;
            hz = (v.z + az) * 0.94;
            double l = Math.sqrt(hx * hx + hz * hz);
            if (l > kind.max) {
                hx *= kind.max / l;
                hz *= kind.max / l;
            }
        }
        if (!engine && onGround()) {
            hx *= 0.5;
            hz *= 0.5;
        }
        setDeltaMovement(hx, vy, hz);
        move(MoverType.SELF, getDeltaMovement());
        setXRot(Mth.lerp(0.1f, getXRot(), engine ? in_f * 12f : 0f));
    }

    // utilise par l'IA de l'helicoptere de police
    public double aiVx, aiVz;

    public int leavingTicks() {
        return leaving;
    }

    public void addLeaving() {
        leaving++;
    }

    public float heliAngle() {
        return heliAngle;
    }

    public void setHeliAngle(float a) {
        heliAngle = a;
    }

    public int stuckTicks() {
        return stuck;
    }

    public void setStuck(int s) {
        stuck = s;
    }

    public int reverseTicks() {
        return reverse;
    }

    public void setReverse(int r) {
        reverse = r;
    }

    // ---------------------------------------------------------------- lerp (comme le bateau)

    @Override
    public void lerpTo(double x, double y, double z, float yr, float xr, int steps, boolean teleport) {
        lx = x;
        ly = y;
        lz = z;
        lyr = yr;
        lxr = xr;
        lerpSteps = 10;
    }

    private void tickLerp() {
        if (isControlledByLocalInstance()) {
            lerpSteps = 0;
            syncPacketPositionCodec(getX(), getY(), getZ());
        }
        if (lerpSteps > 0) {
            double d0 = getX() + (lx - getX()) / lerpSteps;
            double d1 = getY() + (ly - getY()) / lerpSteps;
            double d2 = getZ() + (lz - getZ()) / lerpSteps;
            setPos(d0, d1, d2);
            setRot(getYRot() + (float) Mth.wrapDegrees(lyr - getYRot()) / lerpSteps, getXRot() + (lxr - getXRot()) / lerpSteps);
            lerpSteps--;
        }
    }
}
