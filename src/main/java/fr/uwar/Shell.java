package fr.uwar;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/** Roquette / missile guide / bombe. */
public class Shell extends Projectile {
    static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(Shell.class, EntityDataSerializers.INT);
    private int life;
    private UUID target;
    private UUID srcAircraft;
    private Vec3 point;
    private Vec3 launchPos;
    private double travelled;
    private double cruiseY;
    private boolean diving;
    public static final double BALLISTIC_G = 0.04;
    public static final double FLAK_RANGE = 200;
    private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> KEEP =
            net.minecraft.server.level.TicketType.create("uwar_shell", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 40);

    public Shell(EntityType<? extends Shell> t, Level l) {
        super(t, l);
        this.noCulling = true;
    }

    public W weapon() {
        return W.values()[Math.max(0, Math.min(W.values().length - 1, entityData.get(KIND)))];
    }

    public void setup(W w, Entity owner, Aircraft from, Vec3 vel, UUID target) {
        entityData.set(KIND, w.ordinal());
        setOwner(owner);
        setDeltaMovement(vel);
        this.target = target;
        this.srcAircraft = from == null ? null : from.getUUID();
    }

    public void setPoint(Vec3 p) {
        this.point = p;
        this.cruiseY = Math.max(getY(), p.y) + 28;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, W.ROCKET.ordinal());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        if (srcAircraft != null && e.getUUID().equals(srcAircraft) && life < 60) return false;
        if (e == getOwner() || (getOwner() != null && e == getOwner().getVehicle())) return false;
        if (e instanceof Aircraft a && getOwner() != null && a.hasPassenger(getOwner())) return false;
        return super.canHitEntity(e);
    }

    @Override
    public void tick() {
        super.tick();
        life++;
        W w = weapon();
        Vec3 v = getDeltaMovement();
        if (!level().isClientSide) {
            if (launchPos == null) launchPos = position();
            if (life % 5 == 1 && level() instanceof ServerLevel kl) {
                // garde la zone chargee autour du projectile : il continue de voler meme loin du joueur
                var cp = new net.minecraft.world.level.ChunkPos(blockPosition());
                kl.getChunkSource().addRegionTicket(KEEP, cp, 2, cp);
            }
            switch (w) {
                case MISSILE, FLAK, FLAK5, LRM, BUSTER -> {
                    double sp = Math.min(w.speed, v.length() + 0.18);
                    Vec3 dir = v.lengthSqr() > 1e-4 ? v.normalize() : Vec3.ZERO;
                    double gain = w == W.FLAK ? 0.26 : w == W.FLAK5 ? 0.22 : w == W.LRM ? 0.10 : w == W.BUSTER ? 0.12 : 0.20;
                    Entity t = target != null && level() instanceof ServerLevel sl ? sl.getEntity(target) : null;
                    if (t != null && t.isAlive() && life > 4) {
                        Vec3 want = t.getBoundingBox().getCenter().subtract(position()).normalize();
                        dir = dir.scale(1 - gain).add(want.scale(gain)).normalize();
                        if (w.flak() && t.getBoundingBox().getCenter().distanceTo(position()) < (w == W.FLAK5 ? 10.0 : 7.5)) {
                            explode();
                            return;
                        }
                    }
                    v = dir.scale(sp);
                }
                case IR -> {
                    double sp = Math.min(w.speed, v.length() + 0.2);
                    Vec3 dir = v.lengthSqr() > 1e-4 ? v.normalize() : Vec3.ZERO;
                    Entity t = target != null && level() instanceof ServerLevel sl ? sl.getEntity(target) : null;
                    if ((t == null || !t.isAlive()) && life > 6 && life % 3 == 0) {
                        // recherche autonome de chaleur
                        Entity best = null;
                        double bs = 0.0;
                        for (Entity e : level().getEntities(this, getBoundingBox().inflate(70), x -> x.isAlive() && !x.isSpectator() && (x instanceof LivingEntity || x instanceof Aircraft)
                                && x != getOwner() && (getOwner() == null || x != getOwner().getVehicle()))) {
                            Vec3 to = e.getBoundingBox().getCenter().subtract(position());
                            double cos = dir.dot(to.normalize());
                            if (cos < 0.5) continue;
                            double sc = cos + Aircraft.heat(e) * 2 - to.length() / 200.0;
                            if (best == null || sc > bs) {
                                best = e;
                                bs = sc;
                            }
                        }
                        if (best != null) target = best.getUUID();
                        t = best;
                    }
                    if (t != null && t.isAlive() && life > 4) {
                        Vec3 want = t.getBoundingBox().getCenter().subtract(position()).normalize();
                        dir = dir.scale(0.72).add(want.scale(0.28)).normalize();
                    }
                    v = dir.scale(sp);
                }
                case CRUISE -> {
                    Vec3 tp = point;
                    if (target != null && level() instanceof ServerLevel sl) {
                        Entity t = sl.getEntity(target);
                        if (t != null && t.isAlive()) tp = t.getBoundingBox().getCenter();
                    }
                    double hx = tp.x - getX(), hz = tp.z - getZ();
                    double hd = Math.hypot(hx, hz);
                    if (hd < 38) diving = true;
                    Vec3 want;
                    if (diving) want = tp.subtract(position()).normalize().scale(1.9);
                    else {
                        double vy = Mth.clamp((cruiseY - getY()) * 0.15, -0.5, 0.7);
                        Vec3 hv = new Vec3(hx / Math.max(1e-4, hd), 0, hz / Math.max(1e-4, hd)).scale(w.speed);
                        want = new Vec3(hv.x, vy, hv.z);
                    }
                    v = v.scale(0.8).add(want.scale(0.2));
                }
                case BALLISTIC -> v = v.add(0, -BALLISTIC_G, 0);
                case ROCKET -> v = v.add(0, -0.012, 0);
                default -> v = v.add(0, -0.05, 0).scale(0.995);
            }
            setDeltaMovement(v);
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3 p = hit.getLocation();
                setPos(p.x, p.y, p.z);
                explode();
                return;
            }
            setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
            travelled += v.length();
            if (w.range() > 0 && travelled >= w.range()) {
                explode();
                return;
            }
            if (life > 900 || getY() < level().getMinBuildHeight() - 10) explode();
        } else {
            if (w == W.ROCKET) v = v.add(0, -0.012, 0);
            else if (w == W.BALLISTIC) v = v.add(0, -BALLISTIC_G, 0);
            else if (w.bombLike()) v = v.add(0, -0.05, 0).scale(0.995);
            setDeltaMovement(v);
            setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
            if (w.bombLike()) {
                if (life % 3 == 0) level().addParticle(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 0, 0, 0);
            } else if (w != W.CANNON) {
                level().addParticle(ParticleTypes.FLAME, getX() - v.x * 0.6, getY() - v.y * 0.6, getZ() - v.z * 0.6, 0, 0, 0);
                level().addParticle(ParticleTypes.LARGE_SMOKE, getX() - v.x, getY() - v.y, getZ() - v.z, 0, 0.01, 0);
                level().addParticle(ParticleTypes.SMOKE, getX() - v.x * 1.5, getY() - v.y * 1.5, getZ() - v.z * 1.5, 0, 0.01, 0);
            }
        }
    }

    private void explode() {
        if (level() instanceof ServerLevel sl) {
            W w = weapon();
            if (w == W.NUKE) {
                Nuke.detonate(sl, position(), getOwner());
                discard();
                return;
            }
            if (w.special()) {
                Payload.detonate(sl, w, position(), getOwner());
                discard();
                return;
            }
            Blast.boom(sl, position(), w.radius, w.dmg, this, getOwner(), w.heavy);
        }
        discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
    }

    @Override
    protected void onHitBlock(BlockHitResult r) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        entityData.set(KIND, t.getInt("K"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putInt("K", entityData.get(KIND));
    }
}
