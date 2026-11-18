package fr.uking;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Catapulte : tir automatique sur les ennemis, ou manuel (clic droit main vide, vise ou le joueur regarde). */
public class Catapult extends Mob {
    static final EntityDataAccessor<Integer> AMMO = SynchedEntityData.defineId(Catapult.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Long> SHOT = SynchedEntityData.defineId(Catapult.class, EntityDataSerializers.LONG);
    public static final int MAX_AMMO = 12, MIN_RANGE = 14, MAX_RANGE = 85;
    String owner = "none";
    long lastFire = -1000;

    public Catapult(EntityType<? extends Catapult> t, Level l) {
        super(t, l);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attrs() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 45).add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 90);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(AMMO, 0);
        entityData.define(SHOT, Long.MIN_VALUE / 2);
    }

    public int ammo() { return entityData.get(AMMO); }
    public void setAmmo(int n) { entityData.set(AMMO, Mth.clamp(n, 0, MAX_AMMO)); }
    public long shot() { return entityData.get(SHOT); }
    public String ownerKey() { return owner; }
    public void setOwnerKey(String k) { owner = k; }

    @Override public boolean isPushable() { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override protected int getExperienceReward() { return 0; }
    @Override protected Component getTypeName() { return Component.translatable("uking.unit.catapult"); }

    public boolean canFire() {
        return level().getGameTime() - lastFire >= 70 && (ammo() > 0 || Cfg.FREE_AMMO.get());
    }

    /** Tire un rocher vers target. */
    public boolean fireAt(Vec3 target) {
        if (!(level() instanceof ServerLevel sl) || !canFire()) return false;
        Vec3 from = position().add(0, 2.4, 0);
        double dx = target.x - getX(), dz = target.z - getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        Boulder b = Reg.BOULDER_ENTITY.get().create(sl);
        b.setOwner(this);
        b.launchAt(from.add(Math.sin(-Math.toRadians(yaw)) * 0.5, 0, Math.cos(Math.toRadians(yaw)) * 0.5), target);
        sl.addFreshEntity(b);
        if (!Cfg.FREE_AMMO.get()) setAmmo(ammo() - 1);
        lastFire = sl.getGameTime();
        entityData.set(SHOT, lastFire);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.NEUTRAL, 2f, 0.5f);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_BREAK, SoundSource.NEUTRAL, 2f, 0.6f);
        return true;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || tickCount % 10 != 0 || !canFire() || !(level() instanceof ServerLevel sl)) return;
        Fiefs fs = Fiefs.get(sl);
        LivingEntity best = null;
        double bd = 1e9;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(blockPosition()).inflate(MAX_RANGE, 40, MAX_RANGE))) {
            if (!Soldier.hostileTo(fs, owner, e)) continue;
            double d = e.distanceTo(this);
            if (d < MIN_RANGE || d > MAX_RANGE) continue;
            if (d < bd) { bd = d; best = e; }
        }
        if (best != null) {
            Vec3 v = best.getDeltaMovement();
            fireAt(best.position().add(v.x * 18, 0, v.z * 18));
        }
    }

    @Override
    protected InteractionResult mobInteract(Player p, InteractionHand h) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        var st = p.getItemInHand(h);
        if (st.is(Reg.BOULDER.get())) {
            int add = Math.min(st.getCount(), MAX_AMMO - ammo());
            if (add <= 0) { p.displayClientMessage(Component.literal("Catapulte pleine (" + ammo() + "/" + MAX_AMMO + ")."), true); return InteractionResult.CONSUME; }
            if (!p.isCreative()) st.shrink(add);
            setAmmo(ammo() + add);
            p.displayClientMessage(Component.literal("Munitions : " + ammo() + "/" + MAX_AMMO), true);
            return InteractionResult.CONSUME;
        }
        if (st.isEmpty() && p.getUUID().toString().equals(owner)) {
            Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(95));
            HitResult hr = level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            Vec3 tgt = hr.getType() == HitResult.Type.MISS ? end : hr.getLocation();
            double d = Math.sqrt((tgt.x - getX()) * (tgt.x - getX()) + (tgt.z - getZ()) * (tgt.z - getZ()));
            if (d < MIN_RANGE) p.displayClientMessage(Component.literal("Cible trop proche (min " + MIN_RANGE + " blocs). Visez plus loin."), true);
            else if (!canFire()) p.displayClientMessage(Component.literal(ammo() <= 0 && !Cfg.FREE_AMMO.get() ? "Plus de rochers : clic droit avec des rochers." : "Rechargement..."), true);
            else fireAt(tgt);
            return InteractionResult.CONSUME;
        }
        p.displayClientMessage(Component.literal("Munitions : " + ammo() + "/" + MAX_AMMO + " - clic droit main vide : tirer où vous visez."), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putString("owner", owner);
        t.putInt("ammo", ammo());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        owner = t.getString("owner");
        setAmmo(t.getInt("ammo"));
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }
}
