package fr.udrone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;

import java.util.Optional;
import java.util.UUID;

/** Drone generique : multirotor ou avion (MQ-9). Toute la physique tourne cote serveur. */
public class Drone extends Entity {
    public static final int LANDED = 0, FLYING = 1, RTB = 2, LOITER = 3, LOST = 4, DEAD = 5;
    public static final String[] STATE_NAMES = {"AU SOL", "EN VOL", "RETOUR BASE", "AUTOPILOTE", "SIGNAL PERDU", "DETRUIT"};

    static final EntityDataAccessor<Integer> BATT = SynchedEntityData.defineId(Drone.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Float> HP = SynchedEntityData.defineId(Drone.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(Drone.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> AMMO = SynchedEntityData.defineId(Drone.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> SEL = SynchedEntityData.defineId(Drone.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Float> HEAT = SynchedEntityData.defineId(Drone.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Float> THR = SynchedEntityData.defineId(Drone.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Float> SIG = SynchedEntityData.defineId(Drone.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Float> CYAW = SynchedEntityData.defineId(Drone.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Float> CPITCH = SynchedEntityData.defineId(Drone.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Optional<UUID>> PILOT = SynchedEntityData.defineId(Drone.class, EntityDataSerializers.OPTIONAL_UUID);
    static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(Drone.class, EntityDataSerializers.OPTIONAL_UUID);

    public final Kind kind;

    // --- serveur
    float inF, inS, inU, inYaw, inPitch;
    boolean inFire;
    long lastInput;
    int cd;
    boolean overheated, forcedRtb;
    UUID lastPilot;
    public BlockPos station;
    double spd, fallV, loiterY;
    float fpitch;
    int landHold;

    // --- client
    public float propAng, propAngO, tiltP, tiltPO, tiltR, tiltRO;
    public Entity camSource;
    public boolean localCtl;
    public boolean blinkOn;

    public Drone(EntityType<? extends Drone> t, Level l, Kind k) {
        super(t, l);
        this.kind = k;
        entityData.set(BATT, k.battery);
        entityData.set(HP, k.hp);
        entityData.set(AMMO, k.packedAmmo());
        this.noCulling = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(BATT, 0);
        entityData.define(HP, 1f);
        entityData.define(STATE, LANDED);
        entityData.define(AMMO, 0);
        entityData.define(SEL, 0);
        entityData.define(HEAT, 0f);
        entityData.define(THR, 0f);
        entityData.define(SIG, 1f);
        entityData.define(CYAW, 0f);
        entityData.define(CPITCH, 0f);
        entityData.define(PILOT, Optional.empty());
        entityData.define(OWNER, Optional.empty());
    }

    // ---------------------------------------------------------------- accesseurs
    public int battery() { return entityData.get(BATT); }
    public float hp() { return entityData.get(HP); }
    public int state() { return entityData.get(STATE); }
    public int ammo(int i) { return (entityData.get(AMMO) >> (8 * i)) & 255; }
    public int sel() { return entityData.get(SEL); }
    public int ammoPacked() { return entityData.get(AMMO); }
    public float heat() { return entityData.get(HEAT); }
    public float throttle() { return entityData.get(THR); }
    public float signal() { return entityData.get(SIG); }
    public float camYaw() { return entityData.get(CYAW); }
    public float camPitch() { return entityData.get(CPITCH); }
    public boolean piloted() { return entityData.get(PILOT).isPresent(); }
    public UUID ownerId() { return entityData.get(OWNER).orElse(null); }
    public boolean ownerIs(Entity e) { return e != null && e.getUUID().equals(ownerId()); }

    void setAmmo(int i, int v) {
        int p = entityData.get(AMMO);
        p = (p & ~(255 << (8 * i))) | ((Math.max(0, Math.min(255, v))) << (8 * i));
        entityData.set(AMMO, p);
    }

    void setState(int s) { entityData.set(STATE, s); }

    public double range() {
        return Math.min(kind.range * (station != null ? 1.25 : 1.0), 155.0);
    }

    @Override
    public float getViewYRot(float pt) {
        if (localCtl && camSource != null) return camSource.getViewYRot(pt);
        return camYaw();
    }

    @Override
    public float getViewXRot(float pt) {
        if (localCtl && camSource != null) return camSource.getViewXRot(pt);
        return camPitch();
    }

    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isNoGravity() { return true; }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public ItemStack getPickResult() {
        return makeStack(kind, null);
    }

    // ---------------------------------------------------------------- items
    public static ItemStack makeStack(Kind k, Drone d) {
        ItemStack s = new ItemStack(Reg.DRONE_ITEMS.get(k).get());
        CompoundTag t = s.getOrCreateTag();
        if (d == null) {
            t.putInt("Batt", k.battery);
            t.putFloat("Hp", k.hp);
            t.putInt("Ammo", k.packedAmmo());
        } else {
            t.putInt("Batt", d.battery());
            t.putFloat("Hp", d.hp());
            t.putInt("Ammo", d.entityData.get(AMMO));
        }
        return s;
    }

    // ---------------------------------------------------------------- sauvegarde
    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        entityData.set(BATT, t.getInt("Batt"));
        entityData.set(HP, t.getFloat("Hp"));
        entityData.set(AMMO, t.getInt("Ammo"));
        entityData.set(SEL, t.getInt("Sel"));
        int st = t.getInt("State");
        if (st == DEAD) st = LANDED;
        if (st != LANDED) st = kind.wing ? LOITER : FLYING;
        entityData.set(STATE, st);
        if (t.hasUUID("Owner")) entityData.set(OWNER, Optional.of(t.getUUID("Owner")));
        if (t.contains("St")) station = BlockPos.of(t.getLong("St"));
        loiterY = getY();
        spd = kind.vmax * 0.5;
        forcedRtb = t.getBoolean("Frtb");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putInt("Batt", battery());
        t.putFloat("Hp", hp());
        t.putInt("Ammo", entityData.get(AMMO));
        t.putInt("Sel", sel());
        t.putInt("State", state());
        t.putBoolean("Frtb", forcedRtb);
        if (ownerId() != null) t.putUUID("Owner", ownerId());
        if (station != null) t.putLong("St", station.asLong());
    }

    public static Drone spawn(ServerLevel sl, Kind k, Vec3 pos, Player owner, CompoundTag tag) {
        Drone d = Reg.DRONES.get(k).get().create(sl);
        if (d == null) return null;
        d.setPos(pos.x, pos.y, pos.z);
        d.setYRot(owner != null ? owner.getYRot() + 180f : 0f);
        d.yRotO = d.getYRot();
        if (tag != null && tag.contains("Batt")) {
            d.entityData.set(BATT, tag.getInt("Batt"));
            d.entityData.set(HP, tag.getFloat("Hp"));
            d.entityData.set(AMMO, tag.getInt("Ammo"));
        }
        if (owner != null) d.entityData.set(OWNER, Optional.of(owner.getUUID()));
        d.loiterY = pos.y;
        return d;
    }

    // ---------------------------------------------------------------- interaction
    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        ItemStack held = p.getItemInHand(hand);
        if (held.getItem() instanceof ControllerItem) {
            if (ownerId() == null) entityData.set(OWNER, Optional.of(p.getUUID()));
            boolean added = ControllerItem.bind(held, this);
            p.displayClientMessage(Component.literal(added ? "Drone lie a la telecommande : " + kind.title
                    : "Ce drone est deja lie (ou telecommande pleine)."), true);
            return InteractionResult.CONSUME;
        }
        if (held.is(Items.IRON_INGOT) && hp() < kind.hp) {
            entityData.set(HP, Math.min(kind.hp, hp() + kind.hp * 0.25f));
            if (!p.getAbilities().instabuild) held.shrink(1);
            level().playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.5f, 1.4f);
            p.displayClientMessage(Component.literal("Reparation : " + (int) hp() + "/" + (int) kind.hp + " PV"), true);
            return InteractionResult.CONSUME;
        }
        if (held.is(Items.REDSTONE) && state() == LANDED && battery() < kind.battery) {
            entityData.set(BATT, Math.min(kind.battery, battery() + kind.battery / 5));
            if (!p.getAbilities().instabuild) held.shrink(1);
            p.displayClientMessage(Component.literal("Recharge : " + battery() * 100 / kind.battery + " %"), true);
            return InteractionResult.CONSUME;
        }
        if (p.isShiftKeyDown() && !piloted()) {
            ItemStack s = makeStack(kind, this);
            if (!p.getInventory().add(s)) spawnAtLocation(s);
            discard();
            p.displayClientMessage(Component.literal("Drone recupere."), true);
            return InteractionResult.CONSUME;
        }
        p.displayClientMessage(Component.literal(kind.title + " | " + STATE_NAMES[state()] + " | batterie " + battery() * 100 / kind.battery
                + " % | PV " + (int) hp() + "/" + (int) kind.hp + " | portee " + (int) range() + " m"), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hurt(DamageSource s, float amt) {
        if (level().isClientSide || isRemoved() || state() == DEAD) return false;
        if (s.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) return false;
        entityData.set(HP, hp() - amt);
        ((ServerLevel) level()).sendParticles(ParticleTypes.CRIT, getX(), getY() + kind.h * 0.5, getZ(), 6, 0.2, 0.2, 0.2, 0.2);
        if (hp() <= 0) destroy();
        return true;
    }

    void destroy() {
        if (state() == DEAD) return;
        setState(DEAD);
        ServerLevel sl = (ServerLevel) level();
        release();
        Entity owner = ownerId() == null ? null : sl.getPlayerByUUID(ownerId());
        Blast.boom(sl, position().add(0, kind.h * 0.5, 0), kind.wing ? 2.5f : 1.5f, 3f, this, owner);
        ItemStack s = makeStack(kind, this);
        s.getOrCreateTag().putFloat("Hp", kind.hp * 0.2f);
        s.getOrCreateTag().putInt("Batt", 0);
        spawnAtLocation(s);
        discard();
    }

    @Override
    public void remove(RemovalReason r) {
        if (!level().isClientSide && piloted()) release();
        super.remove(r);
    }

    // ---------------------------------------------------------------- controle
    Player pilot(ServerLevel sl) {
        Optional<UUID> id = entityData.get(PILOT);
        return id.isPresent() ? sl.getPlayerByUUID(id.get()) : null;
    }

    public boolean takeControl(ServerPlayer p) {
        ServerLevel sl = (ServerLevel) level();
        if (state() == DEAD) return false;
        Optional<UUID> cur = entityData.get(PILOT);
        if (cur.isPresent() && !cur.get().equals(p.getUUID())) {
            p.displayClientMessage(Component.literal("Ce drone est deja pilote par un autre joueur."), true);
            return false;
        }
        if (state() != LANDED && battery() <= 0) {
            p.displayClientMessage(Component.literal("Batterie vide : plus de liaison de commande."), true);
            return false;
        }
        if (state() == LOST) {
            Vec3 src = linkSource(sl, null);
            if (src == null || position().distanceTo(src) > range() * 0.9) {
                p.displayClientMessage(Component.literal("Signal perdu : le drone rentre seul a la base."), true);
                return false;
            }
        }
        if (state() == RTB && !forcedRtb) setState(FLYING);
        if (state() == LOST) setState(FLYING);
        if (state() == LOITER) setState(FLYING);
        entityData.set(PILOT, Optional.of(p.getUUID()));
        lastPilot = p.getUUID();
        lastInput = sl.getGameTime();
        inF = inS = inU = 0;
        inYaw = getYRot();
        inPitch = 0;
        inFire = false;
        Pilot.CTRL.put(p.getUUID(), this);
        Net.CH.send(PacketDistributor.PLAYER.with(() -> p), new Net.ControlMsg(getId()));
        return true;
    }

    public void release() {
        Optional<UUID> id = entityData.get(PILOT);
        if (id.isEmpty()) return;
        entityData.set(PILOT, Optional.empty());
        Pilot.CTRL.remove(id.get(), this);
        inFire = false;
        inF = inS = inU = 0;
        if (level() instanceof ServerLevel sl) {
            Player p = sl.getPlayerByUUID(id.get());
            if (p instanceof ServerPlayer sp) Net.CH.send(PacketDistributor.PLAYER.with(() -> sp), new Net.ControlMsg(-1));
        }
        if (kind.wing && state() == FLYING && !onGround() && battery() > 0) {
            setState(LOITER);
            loiterY = getY();
        }
    }

    public void setInput(float f, float s, float u, float yaw, float pitch, boolean fire) {
        inF = Mth.clamp(f, -1, 1);
        inS = Mth.clamp(s, -1, 1);
        inU = Mth.clamp(u, -1, 1);
        inYaw = yaw;
        inPitch = Mth.clamp(pitch, -90, 90);
        inFire = fire;
        lastInput = level().getGameTime();
    }

    public void toggleRtb(ServerPlayer p) {
        int st = state();
        if (st == RTB) {
            if (forcedRtb) {
                p.displayClientMessage(Component.literal("Retour impose (batterie faible)."), true);
                return;
            }
            setState(kind.wing ? LOITER : FLYING);
            loiterY = getY();
            p.displayClientMessage(Component.literal("Retour annule."), true);
        } else if (st == FLYING || st == LOITER) {
            setState(RTB);
            p.displayClientMessage(Component.literal("Retour a la base engage."), true);
        }
    }

    public void nextWeapon() {
        if (kind.weapons.length == 0) return;
        entityData.set(SEL, (sel() + 1) % kind.weapons.length);
    }

    Vec3 linkSource(ServerLevel sl, Player pilot) {
        if (station != null && sl.isLoaded(station) && sl.getBlockEntity(station) instanceof StationBE)
            return Vec3.atCenterOf(station);
        if (pilot != null) return pilot.position();
        if (ownerId() != null) {
            Player o = sl.getPlayerByUUID(ownerId());
            if (o != null) return o.position();
        }
        return null;
    }

    static Vec3 look(float yaw, float pitch) {
        float y = yaw * Mth.DEG_TO_RAD, p = pitch * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(y) * Mth.cos(p), -Mth.sin(p), Mth.cos(y) * Mth.cos(p));
    }

    void notify(ServerLevel sl, UUID id, String msg, boolean sound) {
        if (id == null) return;
        Player p = sl.getPlayerByUUID(id);
        if (p == null) return;
        p.displayClientMessage(Component.literal(msg), true);
        if (sound) sl.playSound(null, p.getX(), p.getY(), p.getZ(), Reg.LOST.get(), SoundSource.PLAYERS, 0.8f, 1f);
    }

    // ---------------------------------------------------------------- tick
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) clientTick();
        else serverTick();
    }

    private void clientTick() {
        propAngO = propAng;
        tiltPO = tiltP;
        tiltRO = tiltR;
        float thr = throttle();
        propAng += thr * 70f;
        double vx = getX() - xo, vy = getY() - yo, vz = getZ() - zo;
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double fwd = vx * -Mth.sin(yaw) + vz * Mth.cos(yaw);
        double left = vx * Mth.cos(yaw) + vz * Mth.sin(yaw);
        float tp, tr;
        if (kind.wing) {
            tp = (float) (-Math.toDegrees(Math.atan2(vy, Math.max(0.05, Math.hypot(vx, vz)))) * 0.6);
            float dy = Mth.wrapDegrees(getYRot() - yRotO);
            tr = Mth.clamp(dy * 9f, -35f, 35f);
        } else {
            tp = (float) Mth.clamp(fwd / kind.vmax * 20.0, -22, 22);
            tr = (float) Mth.clamp(left / kind.vmax * 18.0, -20, 20);
        }
        tiltP += (tp - tiltP) * 0.25f;
        tiltR += (tr - tiltR) * 0.25f;
        blinkOn = (tickCount / 8) % 3 == 0;
        // particules de poussee
        if (thr > 0.15f && tickCount % 2 == 0) {
            if (kind.wing) {
                Vec3 back = new Vec3(Mth.sin(yaw), 0, -Mth.cos(yaw)).scale(2.9);
                level().addParticle(ParticleTypes.CLOUD, getX() + back.x, getY() + 0.5, getZ() + back.z, 0, 0, 0);
            } else if (getY() - level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) getX(), (int) getZ()) < 3.5 && thr > 0.3f) {
                level().addParticle(ParticleTypes.CLOUD, getX() + (random.nextDouble() - 0.5) * kind.w * 1.5, getY() - 0.05,
                        getZ() + (random.nextDouble() - 0.5) * kind.w * 1.5, 0, -0.05, 0);
            }
        }
        if (hp() < kind.hp * 0.3f && state() != LANDED && tickCount % 4 == 0)
            level().addParticle(ParticleTypes.SMOKE, getX(), getY() + kind.h, getZ(), 0, 0.03, 0);
    }

    private void serverTick() {
        ServerLevel sl = (ServerLevel) level();
        int st = state();
        if (st == DEAD) {
            discard();
            return;
        }
        int batt = battery();
        Player pilot = pilot(sl);
        if (piloted() && (pilot == null || !pilot.isAlive() || sl.getGameTime() - lastInput > 40)) {
            release();
            pilot = null;
            st = state();
        }
        if (pilot != null) {
            entityData.set(CYAW, inYaw);
            entityData.set(CPITCH, inPitch);
        }
        Vec3 src = linkSource(sl, pilot);
        double dist = src == null ? 1e9 : position().distanceTo(src);
        double range = range();
        float sig = src == null ? 0f : dist < range * 0.6 ? 1f : (float) Mth.clamp((range - dist) / (range * 0.4), 0, 1);
        if (Math.abs(sig - signal()) > 0.01f) entityData.set(SIG, sig);

        boolean airborne = st != LANDED;
        // perte / recuperation de liaison
        if (airborne) {
            if (st != LOST && dist >= range) {
                UUID who = pilot != null ? pilot.getUUID() : ownerId();
                if (pilot != null) lastPilot = pilot.getUUID();
                release();
                pilot = null;
                setState(LOST);
                st = LOST;
                notify(sl, who, "SIGNAL PERDU - retour automatique du drone", true);
            } else if (st == LOST && dist < range * 0.9) {
                setState(RTB);
                st = RTB;
                notify(sl, ownerId(), "Liaison retablie - retour en cours", false);
                Player lp = lastPilot == null ? null : sl.getPlayerByUUID(lastPilot);
                if (lp instanceof ServerPlayer sp && !forcedRtb && takeControl(sp)) {
                    st = state();
                    pilot = sp;
                    notify(sl, sp.getUUID(), "Liaison retablie - controle repris", false);
                }
            }
            // batterie faible
            if (!forcedRtb && st != RTB && st != LOST && batt < kind.battery * 0.12 && batt > 0) {
                forcedRtb = true;
                setState(RTB);
                st = RTB;
                notify(sl, pilot != null ? pilot.getUUID() : ownerId(), "BATTERIE FAIBLE - retour automatique", true);
            }
            int drain = 1;
            if (spd > kind.vmax * 0.6 || getDeltaMovement().horizontalDistance() > kind.vmax * 0.6) drain = 2;
            if (batt > 0) {
                batt = Math.max(0, batt - drain);
                entityData.set(BATT, batt);
            }
        }
        boolean power = batt > 0;
        if (power) fallV = 0;
        if (forcedRtb && batt > kind.battery * 0.4) forcedRtb = false;

        if (kind.wing) planeMove(sl, st, pilot, src, power);
        else multiMove(sl, st, pilot, src, power);

        // armes
        weapons(sl, pilot);

        // pad de recharge
        if (state() == LANDED) {
            BlockPos bp = blockPosition();
            boolean pad = level().getBlockState(bp).getBlock() instanceof PadBlock || level().getBlockState(bp.below()).getBlock() instanceof PadBlock;
            if (pad) {
                boolean did = false;
                if (battery() < kind.battery) {
                    entityData.set(BATT, Math.min(kind.battery, battery() + 30));
                    did = true;
                }
                if (hp() < kind.hp) {
                    entityData.set(HP, Math.min(kind.hp, hp() + kind.hp / 400f));
                    did = true;
                }
                if (tickCount % 20 == 0) {
                    for (int i = 0; i < kind.ammo.length; i++)
                        if (ammo(i) < kind.ammo[i]) {
                            setAmmo(i, ammo(i) + Math.max(1, kind.ammo[i] / 10));
                            did = true;
                        }
                }
                if (did && tickCount % 8 == 0) {
                    sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.1, getZ(), 3, kind.w * 0.4, 0.1, kind.w * 0.4, 0.05);
                    if (tickCount % 40 == 0)
                        sl.playSound(null, getX(), getY(), getZ(), Reg.CHARGE.get(), SoundSource.BLOCKS, 0.4f, 1f);
                }
            }
        }
        if (hp() < kind.hp * 0.3f && state() != LANDED && tickCount % 6 == 0)
            sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + kind.h, getZ(), 2, 0.1, 0.1, 0.1, 0.02);
    }

    private void slewThrottle(float target) {
        float t = throttle();
        t += (target - t) * 0.12f;
        if (Math.abs(t - throttle()) > 0.003f) entityData.set(THR, t);
    }

    private void hurtCrash(double impact) {
        float dmg = (float) (impact * 45.0);
        entityData.set(HP, hp() - dmg);
        ((ServerLevel) level()).sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.2, getZ(), 10, 0.3, 0.3, 0.3, 0.3);
        if (hp() <= 0) destroy();
    }

    // ------------------------------------------------------------ multirotor
    private void multiMove(ServerLevel sl, int st, Player pilot, Vec3 src, boolean power) {
        Vec3 v = getDeltaMovement();
        Vec3 target = Vec3.ZERO;
        float yawT = getYRot();
        double vmax = kind.vmax;
        float thrT = 0f;

        if (st == LANDED) {
            if (pilot != null && power && (inU > 0.2f) && battery() > kind.battery * 0.03) {
                setState(FLYING);
                st = FLYING;
            } else {
                Vec3 down = new Vec3(0, -0.1, 0);
                setDeltaMovement(down);
                move(MoverType.SELF, down);
                setDeltaMovement(Vec3.ZERO);
                slewThrottle(0f);
                return;
            }
        }
        if (!power) {
            fallV = Math.max(-1.2, fallV - 0.04);
            Vec3 f = new Vec3(v.x * 0.9, fallV, v.z * 0.9);
            setDeltaMovement(f);
            move(MoverType.SELF, f);
            if (onGround()) {
                if (fallV < -0.5) hurtCrash(-fallV * 0.5);
                fallV = 0;
                setState(LANDED);
                setDeltaMovement(Vec3.ZERO);
            }
            slewThrottle(0f);
            return;
        }
        if (st == FLYING && pilot != null) {
            float y = inYaw * Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-Mth.sin(y), 0, Mth.cos(y));
            Vec3 left = new Vec3(Mth.cos(y), 0, Mth.sin(y));
            Vec3 h = fwd.scale(inF).add(left.scale(inS));
            if (h.lengthSqr() > 1) h = h.normalize();
            target = new Vec3(h.x * vmax, inU * kind.vvert, h.z * vmax);
            yawT = inYaw;
            if (onGround() && inU < -0.25f) {
                if (++landHold > 8) {
                    setState(LANDED);
                    landHold = 0;
                    setDeltaMovement(Vec3.ZERO);
                    slewThrottle(0f);
                    return;
                }
            } else landHold = 0;
        } else if (st == RTB || st == LOST) {
            if (src == null) {
                target = new Vec3(0, -0.12, 0);
            } else {
                double dx = src.x - getX(), dz = src.z - getZ();
                double hd = Math.hypot(dx, dz);
                Vec3 dir = hd > 1e-3 ? new Vec3(dx / hd, 0, dz / hd) : Vec3.ZERO;
                // point d'arrivee : 1.3 bloc avant le joueur
                double stop = Math.max(0.0, hd - 1.3);
                double cruise = Math.max(getY(), src.y + 4.0);
                double sp, vy;
                if (hd > 4.0) {
                    sp = Math.min(vmax * 0.8, stop * 0.15 + 0.05);
                    vy = Mth.clamp((cruise - getY()) * 0.2, -kind.vvert, kind.vvert);
                } else {
                    sp = Math.min(0.12, stop * 0.15);
                    vy = -0.15;
                }
                if (horizontalCollision) vy = kind.vvert;
                target = new Vec3(dir.x * sp, vy, dir.z * sp);
                yawT = (float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG);
                if (hd < 1e-3) yawT = getYRot();
            }
            if (onGround() && target.y <= 0) {
                setState(LANDED);
                setDeltaMovement(Vec3.ZERO);
                slewThrottle(0f);
                return;
            }
        }
        v = v.add(target.subtract(v).scale(Math.min(1.0, kind.acc * 1.6)));
        setDeltaMovement(v);
        double before = v.horizontalDistance();
        move(MoverType.SELF, v);
        Vec3 after = getDeltaMovement();
        if (horizontalCollision) {
            double lost = before - after.horizontalDistance();
            if (lost > 0.32) hurtCrash(lost);
        }
        setYRot(Mth.approachDegrees(getYRot(), yawT, 12f));
        setXRot(0);
        thrT = (float) Mth.clamp(0.45 + 0.55 * (after.horizontalDistance() / vmax + Math.abs(after.y) / Math.max(0.05, kind.vvert)), 0.45, 1.0);
        slewThrottle(thrT);
    }

    // ------------------------------------------------------------ avion
    private void planeMove(ServerLevel sl, int st, Player pilot, Vec3 src, boolean power) {
        double vmax = kind.vmax, stall = vmax * 0.30;
        float thr = throttle();
        double turn = 0, climb = 0;
        float yaw = getYRot();
        boolean ground = onGround();

        if (st == LANDED) {
            if (pilot != null && power && inF > 0.1f) {
                setState(FLYING);
                st = FLYING;
            } else {
                Vec3 down = new Vec3(0, -0.1, 0);
                move(MoverType.SELF, down);
                setDeltaMovement(Vec3.ZERO);
                spd = 0;
                fpitch = 0;
                slewThrottle(0f);
                return;
            }
        }
        if (!power) {
            thr = 0;
        } else if (st == LOITER) {
            thr = 0.55f;
            turn = -1.6;
            climb = Mth.clamp((loiterY - getY()) * 0.05, -0.5, 0.5);
        } else if (st == RTB || st == LOST) {
            if (src == null) {
                thr = 0.4f;
                turn = -1.2;
            } else {
                double dx = src.x - getX(), dz = src.z - getZ();
                double hd = Math.hypot(dx, dz);
                float want = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
                float diff = Mth.wrapDegrees(want - yaw);
                turn = Mth.clamp(diff, -2.2, 2.2);
                double tAlt = src.y + Mth.clamp(hd * 0.3, 2, 40);
                climb = Mth.clamp((tAlt - getY()) * 0.06, -0.8, 0.6);
                thr = (float) (hd > 70 ? 0.8 : 0.5);
                if (hd < 16) {
                    // approche finale simplifiee : decroissance de vitesse + descente verticale
                    spd *= 0.93;
                    thr = 0;
                    Vec3 d = new Vec3(0, ground ? -0.1 : -0.14, 0);
                    Vec3 h = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD)).scale(spd);
                    move(MoverType.SELF, h.add(d));
                    if (onGround() && spd < 0.05) {
                        setState(LANDED);
                        spd = 0;
                        setDeltaMovement(Vec3.ZERO);
                    }
                    slewThrottle(0.1f);
                    return;
                }
            }
        } else if (pilot != null) {
            thr = Mth.clamp(thr + inF * 0.015f, 0f, 1f);
            turn = -inS * 1.9 * Mth.clamp(spd / stall, 0.0, 1.0);
            climb = inU;
            if (ground && spd < 0.04 && thr < 0.04f && inU < 0) {
                setState(LANDED);
                setDeltaMovement(Vec3.ZERO);
                return;
            }
        } else {
            setState(LOITER);
            loiterY = getY();
        }
        spd += (thr * vmax - spd) * (kind.acc * 0.6);
        if (!power) spd *= 0.995;
        if (spd > 0.08) yaw += (float) turn;
        setYRot(yaw);
        float fpT = (float) (spd >= stall * 1.1 ? climb * 28.0 : 0.0);
        fpitch += (fpT - fpitch) * 0.12f;
        float fp = fpitch * Mth.DEG_TO_RAD, yr = yaw * Mth.DEG_TO_RAD;
        Vec3 dir = new Vec3(-Mth.sin(yr) * Mth.cos(fp), Mth.sin(fp), Mth.cos(yr) * Mth.cos(fp));
        Vec3 v = dir.scale(spd);
        double lift = spd >= stall ? 1.0 : spd / stall;
        if (ground && (spd < stall * 1.05 || fpitch <= 0.5f)) v = new Vec3(v.x, -0.1, v.z);
        else if (!ground) {
            if (!power) {
                fallV = Math.max(-1.0, fallV - 0.03);
                v = v.add(0, fallV, 0);
            } else v = v.add(0, -(1 - lift) * 0.3, 0);
        }
        setDeltaMovement(v);
        move(MoverType.SELF, v);
        if (horizontalCollision && spd > 0.45) {
            hurtCrash(spd);
            spd *= 0.3;
        } else if (horizontalCollision) spd *= 0.5;
        if (!power && onGround() && fallV < -0.4) {
            hurtCrash(-fallV * 0.6);
            fallV = 0;
        }
        slewThrottle(thr);
    }

    // ------------------------------------------------------------ armes
    private void weapons(ServerLevel sl, Player pilot) {
        if (cd > 0) cd--;
        float heat = heat();
        float nh = Math.max(0, heat - 0.7f);
        if (nh != heat) entityData.set(HEAT, nh);
        if (overheated && nh < 35) overheated = false;
        if (!inFire || pilot == null || kind.weapons.length == 0 || cd > 0) return;
        int sel = Math.min(sel(), kind.weapons.length - 1);
        W w = kind.weapons[sel];
        int am = ammo(sel);
        if (am <= 0) {
            if (tickCount % 20 == 0) sl.playSound(null, getX(), getY(), getZ(), Reg.BEEP.get(), SoundSource.NEUTRAL, 0.6f, 0.7f);
            return;
        }
        if (w == W.MG && overheated) return;
        Vec3 dir = look(inYaw, inPitch);
        float yr = getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(Mth.cos(yr), 0, Mth.sin(yr)).scale(-1);
        double side = (am % 2 == 0) ? 1 : -1;
        switch (w) {
            case MG -> {
                Vec3 muz = position().add(0, kind.h * 0.3, 0).add(dir.scale(kind.w * 0.6 + 0.3));
                fireHitscan(sl, pilot, muz, dir);
                heat += 3.2f;
                entityData.set(HEAT, Math.min(100f, heat));
                if (heat >= 100f) {
                    overheated = true;
                    sl.playSound(null, getX(), getY(), getZ(), Reg.BEEP.get(), SoundSource.NEUTRAL, 1f, 0.5f);
                }
            }
            case SHELL, MISSILE -> {
                Vec3 muz = position().add(0, kind.h * 0.2, 0).add(dir.scale(kind.w * 0.6 + 0.4));
                if (w == W.MISSILE) muz = position().add(right.scale(side * 2.4)).add(0, -0.3, 0).add(dir.scale(1.2));
                Shell s = new Shell(Reg.SHELL.get(), sl);
                s.setPos(muz.x, muz.y, muz.z);
                UUID tgt = w == W.MISSILE ? lockTarget(sl, pilot, muz, dir) : null;
                s.setup(w, pilot, dir.scale(w == W.MISSILE ? 1.0 : w.speed), tgt);
                sl.addFreshEntity(s);
                sl.sendParticles(ParticleTypes.FLAME, muz.x, muz.y, muz.z, 8, 0.1, 0.1, 0.1, 0.05);
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, muz.x, muz.y, muz.z, 4, 0.1, 0.1, 0.1, 0.02);
                sl.playSound(null, muz.x, muz.y, muz.z, Reg.LAUNCH.get(), SoundSource.NEUTRAL, 2f, 1f);
            }
            case BOMB -> {
                Vec3 muz = position().add(0, -0.35, 0);
                if (kind.wing) muz = muz.add(right.scale(side * 1.2));
                Shell s = new Shell(Reg.SHELL.get(), sl);
                s.setPos(muz.x, muz.y, muz.z);
                s.setup(w, pilot, getDeltaMovement().scale(kind.wing ? 1.0 : 0.6), null);
                sl.addFreshEntity(s);
                sl.playSound(null, muz.x, muz.y, muz.z, Reg.LAUNCH.get(), SoundSource.NEUTRAL, 0.8f, 0.6f);
            }
            case CHARGE -> {
                setAmmo(sel, 0);
                setState(DEAD);
                release();
                Blast.boom(sl, position().add(0, kind.h * 0.5, 0), w.radius, w.dmg, this, pilot);
                discard();
                return;
            }
        }
        setAmmo(sel, am - 1);
        cd = w.cooldown;
    }

    private void fireHitscan(ServerLevel sl, Player pilot, Vec3 muz, Vec3 dir) {
        Vec3 end = muz.add(dir.scale(70));
        BlockHitResult bh = sl.clip(new ClipContext(muz, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 e2 = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        Entity hit = null;
        double best = 1e9;
        Vec3 hitPos = null;
        for (Entity e : sl.getEntities(this, new AABB(muz, e2).inflate(1.0),
                x -> (x instanceof LivingEntity || x instanceof Drone) && x.isAlive() && x != pilot && !(x instanceof Drone d && d.ownerIs(pilot)))) {
            Optional<Vec3> r = e.getBoundingBox().inflate(0.25).clip(muz, e2);
            if (r.isPresent()) {
                double dd = r.get().distanceToSqr(muz);
                if (dd < best) {
                    best = dd;
                    hit = e;
                    hitPos = r.get();
                }
            }
        }
        if (hit != null) {
            e2 = hitPos;
            hit.invulnerableTime = 0;
            hit.hurt(sl.damageSources().mobProjectile(this, pilot), (float) (W.MG.dmg * Cfg.DAMAGE_MULT.get()));
            sl.sendParticles(ParticleTypes.CRIT, e2.x, e2.y, e2.z, 4, 0.1, 0.1, 0.1, 0.2);
        } else if (bh.getType() != HitResult.Type.MISS) {
            sl.sendParticles(ParticleTypes.SMOKE, e2.x, e2.y, e2.z, 3, 0.05, 0.05, 0.05, 0.02);
            sl.sendParticles(ParticleTypes.CRIT, e2.x, e2.y, e2.z, 2, 0.05, 0.05, 0.05, 0.1);
        }
        if (tickCount % 4 == 0) {
            double len = muz.distanceTo(e2);
            var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1f, 0.8f, 0.2f), 0.7f);
            for (double t = 1.5; t < len && t < 40; t += 3.0) {
                Vec3 p = muz.add(dir.scale(t));
                sl.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        sl.sendParticles(ParticleTypes.FLAME, muz.x, muz.y, muz.z, 1, 0.02, 0.02, 0.02, 0.01);
        sl.playSound(null, muz.x, muz.y, muz.z, Reg.GUN.get(), SoundSource.NEUTRAL, 1.2f, 0.9f + random.nextFloat() * 0.2f);
    }

    private UUID lockTarget(ServerLevel sl, Player pilot, Vec3 muz, Vec3 dir) {
        Vec3 end = muz.add(dir.scale(120));
        Entity best = null;
        double bd = 1e9;
        for (Entity e : sl.getEntities(this, new AABB(muz, end).inflate(3.0),
                x -> x instanceof LivingEntity && x.isAlive() && x != pilot)) {
            Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
            Vec3 rel = c.subtract(muz);
            double along = rel.dot(dir);
            if (along < 2) continue;
            double off = rel.subtract(dir.scale(along)).length();
            if (off < 2.5 + along * 0.04 && along < bd) {
                bd = along;
                best = e;
            }
        }
        return best == null ? null : best.getUUID();
    }
}
