package fr.udrone;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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

/** Obus / bombe / missile virtuel. */
public class Shell extends Projectile {
    static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(Shell.class, EntityDataSerializers.INT);
    private int life;
    private UUID target;

    public Shell(EntityType<? extends Shell> t, Level l) {
        super(t, l);
        this.noCulling = true;
    }

    public W weapon() {
        return W.values()[Math.max(0, Math.min(W.values().length - 1, entityData.get(KIND)))];
    }

    public void setup(W w, Entity owner, Vec3 vel, UUID target) {
        entityData.set(KIND, w.ordinal());
        setOwner(owner);
        setDeltaMovement(vel);
        this.target = target;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, W.SHELL.ordinal());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        if (e instanceof Drone d && d.ownerIs(getOwner())) return false;
        return super.canHitEntity(e);
    }

    @Override
    public void tick() {
        super.tick();
        life++;
        W w = weapon();
        Vec3 v = getDeltaMovement();
        if (!level().isClientSide) {
            if (w == W.MISSILE) {
                double sp = Math.min(w.speed, v.length() + 0.2);
                Vec3 dir = v.lengthSqr() > 1e-4 ? v.normalize() : Vec3.ZERO;
                if (target != null && level() instanceof ServerLevel sl) {
                    Entity t = sl.getEntity(target);
                    if (t != null && t.isAlive()) {
                        Vec3 want = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(position()).normalize();
                        dir = dir.scale(0.82).add(want.scale(0.18)).normalize();
                    }
                }
                v = dir.scale(sp);
            } else if (w == W.SHELL) {
                v = v.add(0, -0.035, 0);
            } else {
                v = v.add(0, -0.05, 0).scale(0.99);
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
            if (life > 220 || getY() < level().getMinBuildHeight() - 10) explode();
        } else {
            if (w == W.SHELL) v = v.add(0, -0.035, 0);
            else if (w == W.BOMB) v = v.add(0, -0.05, 0).scale(0.99);
            setDeltaMovement(v);
            setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
            if (w == W.MISSILE) {
                level().addParticle(ParticleTypes.FLAME, getX() - v.x * 0.6, getY() - v.y * 0.6, getZ() - v.z * 0.6, 0, 0, 0);
                level().addParticle(ParticleTypes.LARGE_SMOKE, getX() - v.x, getY() - v.y, getZ() - v.z, 0, 0.01, 0);
                level().addParticle(ParticleTypes.SMOKE, getX() - v.x * 1.5, getY() - v.y * 1.5, getZ() - v.z * 1.5, 0, 0.01, 0);
            } else if (w == W.SHELL) {
                level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
            } else if (life % 3 == 0) {
                level().addParticle(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 0, 0, 0);
            }
        }
    }

    private void explode() {
        if (level() instanceof ServerLevel sl) {
            W w = weapon();
            Blast.boom(sl, position(), w.radius, w.dmg, this, getOwner());
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
