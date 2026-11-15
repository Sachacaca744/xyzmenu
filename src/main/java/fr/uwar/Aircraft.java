package fr.uwar;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/** Helicoptere d'attaque ou avion de chasse pilotable. Vol calcule cote client du pilote, armes/carburant/degats cote serveur. */
public class Aircraft extends Entity {
    static final EntityDataAccessor<Float> HP = SynchedEntityData.defineId(Aircraft.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Float> FUEL = SynchedEntityData.defineId(Aircraft.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Float> THR = SynchedEntityData.defineId(Aircraft.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Integer> SEL = SynchedEntityData.defineId(Aircraft.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> LOCK = SynchedEntityData.defineId(Aircraft.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Boolean> ENGINE = SynchedEntityData.defineId(Aircraft.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Integer> LOADOUT = SynchedEntityData.defineId(Aircraft.class, EntityDataSerializers.INT);
    @SuppressWarnings("unchecked")
    static final EntityDataAccessor<Integer>[] AM = new EntityDataAccessor[W.values().length];

    static {
        for (int i = 0; i < AM.length; i++) AM[i] = SynchedEntityData.defineId(Aircraft.class, EntityDataSerializers.INT);
    }

    static final EntityDataAccessor<Boolean> BOOST = SynchedEntityData.defineId(Aircraft.class, EntityDataSerializers.BOOLEAN);

    public final Kind kind;

    // commandes (ecrites par le clavier du pilote, ou par le serveur a partir des messages)
    public float inF, inS, inU, aimYaw, aimPitch;
    public boolean inBoost, inFire, inBrake;
    private int lastInputTick;

    // physique
    public float speedF, thrust;
    public float roll, rollO, rotor, rotorO;
    public float flapO;
    /** Choc mesure par la physique du pilote (vitesse perdue), envoye au serveur qui applique les degats. */
    public float pendingImpact;

    // serveur
    private int cooldown, crashCd, lockTick, clickCd;
    private boolean alt;
    private Vec3 lastPos;
    public Vec3 vel = Vec3.ZERO;
    public int shots;

    // lerp
    private int lerpSteps;
    private double lx, ly, lz;
    private float lyr, lxr;

    /** Crochet de test : un faux joueur pilote, la physique tourne alors cote serveur. */
    public Player testPilot;

    public Aircraft(EntityType<?> t, Level l, Kind k) {
        super(t, l);
        this.kind = k;
        this.noCulling = true;
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(HP, 100f);
        entityData.define(FUEL, 0f);
        entityData.define(THR, 0f);
        entityData.define(SEL, 0);
        entityData.define(LOCK, -1);
        entityData.define(ENGINE, false);
        entityData.define(LOADOUT, W.CANNON.bit());
        for (EntityDataAccessor<Integer> a : AM) entityData.define(a, 0);
        entityData.define(BOOST, false);
    }

    // ---------------------------------------------------------------- etat

    public float hp() { return entityData.get(HP); }
    public float fuel() { return entityData.get(FUEL); }
    public float thr() { return entityData.get(THR); }
    public boolean engineOn() { return entityData.get(ENGINE); }
    public boolean boosting() { return entityData.get(BOOST); }
    public int lockId() { return entityData.get(LOCK); }

    public int loadout() { return entityData.get(LOADOUT); }
    public boolean has(W w) { return (loadout() & w.bit()) != 0; }

    public int slotsUsed() { return Integer.bitCount(loadout() & ~W.CANNON.bit()); }

    public W selected() {
        W w = W.values()[Mth.clamp(entityData.get(SEL), 0, W.values().length - 1)];
        return has(w) ? w : W.CANNON;
    }

    /** Armes de la configuration actuelle, dans l'ordre. */
    public java.util.List<W> loaded() {
        java.util.List<W> l = new java.util.ArrayList<>();
        for (W w : W.values()) if (has(w)) l.add(w);
        return l;
    }

    public int ammo(W w) { return entityData.get(AM[w.ordinal()]); }

    /** Capacite pour cette arme (0 si elle n'est pas installee). */
    public int cap(W w) { return has(w) ? kind.cap[w.ordinal()] : 0; }

    public void setAmmo(W w, int n) { entityData.set(AM[w.ordinal()], Mth.clamp(n, 0, kind.cap[w.ordinal()])); }

    public void setFuel(float f) { entityData.set(FUEL, Mth.clamp(f, 0f, kind.fuelCap)); }
    public void setHp(float h) { entityData.set(HP, Mth.clamp(h, 0f, kind.maxHp)); }

    public void nextWeapon(int d) {
        int n = W.values().length, idx = selected().ordinal();
        for (int k = 0; k < n; k++) {
            idx = ((idx + d) % n + n) % n;
            if (has(W.values()[idx])) break;
        }
        entityData.set(SEL, idx);
        entityData.set(LOCK, -1);
        level().playSound(null, getX(), getY(), getZ(), Reg.BEEP.get(), SoundSource.NEUTRAL, 0.5f, 1.6f);
    }

    /** Change la configuration d'armement. Retourne un message d'erreur, ou null si OK. */
    public String setLoadout(Player p, int mask) {
        mask |= W.CANNON.bit();
        for (W w : W.values()) if ((mask & w.bit()) != 0 && !kind.canCarry(w)) return w.label + " : impossible sur cet appareil";
        if (Integer.bitCount(mask & ~W.CANNON.bit()) > kind.slots) return "Trop d'armes : " + kind.slots + " emplacements maximum";
        if (!grounded() && !p.getAbilities().instabuild) return "Pose l'appareil au sol pour changer l'armement";
        int old = loadout();
        if (mask == old) return null;
        for (W w : W.values()) {
            if ((old & w.bit()) != 0 && (mask & w.bit()) == 0) {
                int items = ammo(w) / w.perItem;
                setAmmo(w, 0);
                if (items > 0 && w != W.CANNON) {
                    ItemStack st = new ItemStack(Reg.AMMO.get(w).get(), items);
                    if (!p.getInventory().add(st)) p.drop(st, false);
                }
            }
        }
        entityData.set(LOADOUT, mask);
        entityData.set(LOCK, -1);
        if (!has(W.values()[entityData.get(SEL)])) entityData.set(SEL, W.CANNON.ordinal());
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.5f, 1.2f);
        return null;
    }

    public void setInput(float thr, boolean boost, boolean fire, boolean up, float yaw, float pitch, float impact) {
        lastInputTick = tickCount;
        if (impact > pendingImpact) pendingImpact = Math.min(impact, 4f);
        inFire = fire;
        inBoost = boost;
        aimYaw = yaw;
        aimPitch = pitch;
        if (!level().isClientSide) {
            entityData.set(THR, Mth.clamp(thr, 0f, 1f));
            entityData.set(BOOST, boost && !kind.heli() && fuel() > 20f);
        }
    }

    // ---------------------------------------------------------------- objet

    public static ItemStack makeStack(Kind k, boolean full) {
        ItemStack s = new ItemStack(Reg.AIRCRAFT_ITEMS.get(k).get());
        if (full) {
            CompoundTag t = s.getOrCreateTagElement("ac");
            t.putFloat("Hp", k.maxHp);
            t.putFloat("Fuel", k.fuelCap);
            t.putInt("Lo", k.defaultLoadout);
            int[] a = new int[W.values().length];
            for (W w : W.values()) if ((k.defaultLoadout & w.bit()) != 0) a[w.ordinal()] = k.cap[w.ordinal()];
            t.putIntArray("Am", a);
            s.setHoverName(Component.literal(k.name + " (equipe)").withStyle(st -> st.withItalic(false)));
        }
        return s;
    }

    public void applyStack(ItemStack s) {
        CompoundTag t = s.getTagElement("ac");
        if (t == null) {
            entityData.set(LOADOUT, kind.defaultLoadout);
            setHp(kind.maxHp);
            setFuel(kind.fuelCap * 0.4f);
            setAmmo(W.CANNON, 150);
            return;
        }
        entityData.set(LOADOUT, t.contains("Lo") ? (t.getInt("Lo") | W.CANNON.bit()) : kind.defaultLoadout);
        setHp(t.getFloat("Hp"));
        setFuel(t.getFloat("Fuel"));
        int[] a = t.getIntArray("Am");
        for (W w : W.values()) if (a.length > w.ordinal()) setAmmo(w, a[w.ordinal()]);
    }

    public ItemStack toStack() {
        ItemStack s = new ItemStack(Reg.AIRCRAFT_ITEMS.get(kind).get());
        CompoundTag t = s.getOrCreateTagElement("ac");
        t.putFloat("Hp", hp());
        t.putFloat("Fuel", fuel());
        t.putInt("Lo", loadout());
        int[] a = new int[W.values().length];
        for (W w : W.values()) a[w.ordinal()] = ammo(w);
        t.putIntArray("Am", a);
        return s;
    }

    // ---------------------------------------------------------------- ravitaillement

    /** Utilise un objet de ravitaillement sur l'appareil. true si utilise. */
    public boolean supply(Player p, ItemStack st) {
        if (st.getItem() instanceof SupplyItem si) {
            String msg = null;
            boolean used = false;
            switch (si.type) {
                case AMMO -> {
                    W w = si.weapon;
                    if (cap(w) <= 0) msg = w.label + " : non installe (touche 5 pour configurer l'armement)";
                    else if (ammo(w) >= cap(w)) msg = w.label + " : deja plein (" + ammo(w) + ")";
                    else {
                        setAmmo(w, ammo(w) + w.perItem);
                        used = true;
                        msg = w.label + " : " + ammo(w) + " / " + cap(w);
                    }
                }
                case FUEL -> {
                    if (fuel() >= kind.fuelCap - 1) msg = "Reservoir deja plein";
                    else {
                        setFuel(fuel() + SupplyItem.FUEL_PER_CAN);
                        used = true;
                        msg = "Carburant : " + Math.round(fuel() * 100 / kind.fuelCap) + " %";
                    }
                }
                case REPAIR -> {
                    if (hp() >= kind.maxHp - 0.5f) msg = "Appareil deja en parfait etat";
                    else {
                        setHp(hp() + SupplyItem.REPAIR_HP);
                        used = true;
                        msg = "Etat : " + Math.round(hp() * 100 / kind.maxHp) + " %";
                    }
                }
            }
            if (!p.level().isClientSide) {
                if (used && !p.getAbilities().instabuild) st.shrink(1);
                if (used) level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.4f, 1.5f);
                if (msg != null) p.displayClientMessage(Component.literal(msg), true);
            }
            return true;
        }
        return false;
    }

    /** Touche R : recharge tout ce qui est possible depuis l'inventaire du pilote. */
    public void reloadFrom(Player p) {
        int used = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            while (!st.isEmpty() && st.getItem() instanceof SupplyItem si && canTake(si)) {
                if (!supply(p, st)) break;
                used++;
                if (p.getAbilities().instabuild) break;
            }
        }
        for (W w : loaded()) sb.append(w.shortName).append(' ').append(ammo(w)).append('/').append(cap(w)).append("  ");
        p.displayClientMessage(Component.literal(used == 0 ? "Rien a charger dans l'inventaire | " + sb
                : "Rechargement termine | " + sb + "Carburant " + Math.round(fuel() * 100 / kind.fuelCap) + " %"), true);
    }

    private boolean canTake(SupplyItem si) {
        return switch (si.type) {
            case AMMO -> cap(si.weapon) > 0 && ammo(si.weapon) < cap(si.weapon);
            case FUEL -> fuel() < kind.fuelCap - 1;
            case REPAIR -> hp() < kind.maxHp - 0.5f;
        };
    }

    // ---------------------------------------------------------------- interaction

    @Override
    public boolean canBeCollidedWith() { return true; }

    @Override
    public boolean isPickable() { return !isRemoved(); }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean fireImmune() { return true; }

    @Override
    public ItemStack getPickResult() { return toStack(); }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (st.getItem() instanceof SupplyItem) {
            supply(p, st);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (p.isSecondaryUseActive()) {
            if (!getPassengers().isEmpty()) return InteractionResult.PASS;
            if (!level().isClientSide) {
                ItemEntity ie = new ItemEntity(level(), getX(), getY() + 0.5, getZ(), toStack());
                level().addFreshEntity(ie);
                discard();
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!level().isClientSide) {
            if (hp() <= 0) return InteractionResult.PASS;
            return p.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean canAddPassenger(Entity e) { return getPassengers().size() < kind.seats; }

    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof LivingEntity le ? le : null;
    }

    private double[] seat(int i) {
        // {lateral(gauche +), hauteur, avant}
        if (kind == Kind.HELI) return i == 0 ? new double[]{0, 0.45, 1.25} : new double[]{0, 0.30, 2.55};
        if (kind == Kind.MINI) return new double[]{0, 0.05, 0.9};
        return new double[]{0, 0.42, 1.7};
    }

    @Override
    protected void positionRider(Entity p, MoveFunction cb) {
        if (!hasPassenger(p)) return;
        double[] s = seat(Math.max(0, getPassengers().indexOf(p)));
        double a = getYRot() * Mth.DEG_TO_RAD, b = getXRot() * Mth.DEG_TO_RAD;
        double fx = -Math.sin(a), fz = Math.cos(a);
        cb.accept(p, getX() + fx * s[2] * Math.cos(b), getY() + s[1] - Math.sin(b) * s[2], getZ() + fz * s[2] * Math.cos(b));
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity p) {
        double a = getYRot() * Mth.DEG_TO_RAD;
        double lxv = Math.cos(a), lzv = Math.sin(a);
        double d = kind.w * 0.5 + 1.0;
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
        if (src.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) return false;
        if (src.getEntity() instanceof Player p && p.getAbilities().instabuild && p.isShiftKeyDown()) {
            ejectPassengers();
            discard();
            return true;
        }
        if (src.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) || src.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
            // blindage leger
        } else {
            amount = Math.max(0f, amount - 1f);
        }
        float h = hp() - amount;
        entityData.set(HP, Math.max(0f, h));
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.25f, 1.7f);
        if (h <= 0f) destroy(src.getEntity());
        return true;
    }

    private boolean dying;

    private void destroy(Entity killer) {
        if (dying) return;
        dying = true;
        List<Entity> pass = List.copyOf(getPassengers());
        ejectPassengers();
        if (level() instanceof ServerLevel sl) {
            Blast.boom(sl, position().add(0, kind.h * 0.5, 0), 4.5f, 18f, this, killer);
            for (Entity e : pass) e.hurt(sl.damageSources().explosion(this, killer), 14f);
        }
        discard();
    }

    // ---------------------------------------------------------------- tick

    @Override
    public boolean isControlledByLocalInstance() {
        LivingEntity c = getControllingPassenger();
        if (c instanceof Player p) return p.isLocalPlayer() || (testPilot != null && p == testPilot);
        return !level().isClientSide;
    }

    @Override
    public void tick() {
        super.tick();
        tickLerp();
        if (!level().isClientSide) serverTick();
        LivingEntity c = getControllingPassenger();
        if (isControlledByLocalInstance()) {
            boolean piloted = c instanceof Player;
            if (level().isClientSide) {
                inF = inS = inU = 0;
                inBrake = false;
                if (piloted) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.uwar.client.ClientHooks.poll(this));
                else inFire = inBoost = false;
            } else if (testPilot == null) {
                inF = inS = inU = 0;
            }
            fly(piloted);
        } else {
            setDeltaMovement(Vec3.ZERO);
        }
        if (level().isClientSide) animate();
    }

    private void animate() {
        rotorO = rotor;
        rollO = rollO + 0f;
        float t = thr();
        if (engineOn()) rotor += kind.heli() ? 58f : 30f + t * 60f;
        else rotor = rotor + (kind.heli() ? Math.max(0f, 18f - (tickCount % 1000) * 0.1f) * 0f : 0f);
        if (rotor > 3600f) {
            rotor -= 3600f;
            rotorO -= 3600f;
        }
        if (engineOn() && !kind.heli() && boosting()) {
            double a = getYRot() * Mth.DEG_TO_RAD;
            level().addParticle(ParticleTypes.FLAME, getX() + Math.sin(a) * 6.0 * kind.sc, getY() + kind.sc, getZ() - Math.cos(a) * 6.0 * kind.sc, 0, 0, 0);
        }
        if (engineOn() && kind.heli() && grounded()) {
            double ang = random.nextDouble() * Math.PI * 2;
            level().addParticle(ParticleTypes.CLOUD, getX() + Math.cos(ang) * 4.5, getY() + 0.2, getZ() + Math.sin(ang) * 4.5,
                    Math.cos(ang) * 0.12, 0.01, Math.sin(ang) * 0.12);
        }
    }

    public boolean grounded() {
        return onGround() || !level().noCollision(this, getBoundingBox().move(0, -0.2, 0));
    }

    private void fly(boolean piloted) {
        boolean on = piloted && engineOn() && fuel() > 0f;
        if (!piloted) {
            inF = inS = inU = 0;
            inBoost = false;
        }
        rollO = roll;
        if (kind.heli()) flyHeli(on);
        else flyJet(on);
    }

    private void flyHeli(boolean on) {
        float dyaw = Mth.wrapDegrees(aimYaw - getYRot());
        if (on) setYRot(getYRot() + Mth.clamp(dyaw, -4.5f, 4.5f));
        double a = getYRot() * Mth.DEG_TO_RAD;
        double fx = -Math.sin(a), fz = Math.cos(a), lxv = Math.cos(a), lzv = Math.sin(a);
        Vec3 v = getDeltaMovement();
        double vx, vy, vz;
        if (on) {
            double tx = fx * inF * 0.62 + lxv * inS * 0.36;
            double tz = fz * inF * 0.62 + lzv * inS * 0.36;
            double ty = inU * 0.34;
            vx = v.x + (tx - v.x) * 0.045;
            vz = v.z + (tz - v.z) * 0.045;
            vy = v.y + (ty - v.y) * 0.12;
        } else {
            vx = v.x * 0.96;
            vz = v.z * 0.96;
            vy = Math.max(v.y - 0.035, -0.32);
        }
        setDeltaMovement(vx, vy, vz);
        double hb = Math.hypot(vx, vz);
        move(MoverType.SELF, getDeltaMovement());
        double hbAfter = Math.hypot(getDeltaMovement().x, getDeltaMovement().z);
        float imp = 0f;
        if (horizontalCollision) imp = (float) (hb - hbAfter);
        if (verticalCollision && vy < -0.45) imp = Math.max(imp, (float) -vy);
        if (imp > 0.4f) pendingImpact = Math.max(pendingImpact, imp);
        if (grounded() && getDeltaMovement().y < 0) setDeltaMovement(getDeltaMovement().multiply(0.8, 0, 0.8));
        setXRot(Mth.lerp(0.08f, getXRot(), on ? (float) (inF * 13) : 0f));
        float rt = on ? -inS * 10f + Mth.clamp(dyaw, -20f, 20f) * 0.4f : 0f;
        roll = Mth.lerp(0.1f, roll, rt);
        speedF = (float) getDeltaMovement().length();
        thrust = on ? Math.max(Math.abs(inF), Math.max(Math.abs(inS), Math.abs(inU))) : 0f;
    }

    private void flyJet(boolean on) {
        boolean ground = grounded();
        if (on) {
            thrust += inF * 0.02f;
            if (inBrake) thrust -= 0.05f;
            thrust = Mth.clamp(thrust, 0f, 1f);
        } else thrust = Math.max(0f, thrust - 0.01f);
        boolean boost = on && inBoost && fuel() > 20f;
        double tgt = on ? (0.04 + thrust * 1.5) * (boost ? 1.5 : 1.0) * kind.speedMul : 0.0;
        if (tgt > speedF) speedF += (float) ((tgt - speedF) * 0.03 + 0.002);
        else speedF += (float) ((tgt - speedF) * 0.015);
        if (ground) {
            speedF *= (inBrake || thrust < 0.02f) ? 0.96f : 0.997f;
        } else if (!on) speedF -= 0.004f;
        speedF = Math.max(0f, speedF);

        float dyaw = Mth.wrapDegrees(aimYaw - getYRot());
        if (on || speedF > 0.3f) {
            float yr = (ground ? (speedF > 0.03f ? 2.0f : 0f) : 3.2f) * kind.turn;
            setYRot(getYRot() + Mth.clamp(dyaw, -yr, yr));
            float pt = Mth.clamp(aimPitch, -70f, 70f);
            if (ground && speedF < kind.liftoff) pt = 0f;
            setXRot(getXRot() + Mth.clamp(pt - getXRot(), -2.6f * kind.turn, 2.6f * kind.turn));
        }
        float lift = Mth.clamp((speedF - kind.stall) / 0.55f, 0f, 1f);
        if (!ground) setXRot(Math.min(85f, getXRot() + (1f - lift) * 1.6f));
        double yr = getYRot() * Mth.DEG_TO_RAD, xr = getXRot() * Mth.DEG_TO_RAD;
        double dx = -Math.sin(yr) * Math.cos(xr), dy = -Math.sin(xr), dz = Math.cos(yr) * Math.cos(xr);
        double vy = dy * speedF - (1f - lift) * 0.12;
        setDeltaMovement(dx * speedF, vy, dz * speedF);
        move(MoverType.SELF, getDeltaMovement());
        if (horizontalCollision && speedF > 0.3f) {
            pendingImpact = Math.max(pendingImpact, speedF * 0.8f);
            speedF *= 0.3f;
        } else if (verticalCollision && dy * speedF < -0.35) {
            pendingImpact = Math.max(pendingImpact, (float) (-dy * speedF));
            speedF *= 0.3f;
        }
        float rt = on ? Mth.clamp(dyaw, -30f, 30f) * 1.6f * Math.min(kind.turn, 1.5f) : 0f;
        roll = Mth.lerp(0.12f, roll, rt);
    }

    private void serverTick() {
        LivingEntity c = getControllingPassenger();
        boolean on = c != null && fuel() > 0f && hp() > 0f;
        if (on != engineOn()) entityData.set(ENGINE, on);
        if (testPilot == null && c != null && tickCount - lastInputTick > 6) {
            inFire = false;
            inBoost = false;
        }
        if (c == null) {
            if (thr() != 0f) entityData.set(THR, 0f);
            if (boosting()) entityData.set(BOOST, false);
            inFire = false;
        }
        if (isInLava()) hurt(level().damageSources().inFire(), 1.5f);
        if (getY() < level().getMinBuildHeight() - 20) discard();

        Vec3 p = position();
        vel = lastPos == null ? Vec3.ZERO : p.subtract(lastPos);
        lastPos = p;
        if (crashCd > 0) crashCd--;
        float imp = pendingImpact;
        pendingImpact = 0f;
        if (imp > 0.35f && crashCd == 0 && !isRemoved() && c != null) {
            crashCd = 10;
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 0.8f, 1.4f);
            if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 2, 1, 0.5, 1, 0);
            if (imp > 0.8) for (Entity e : getPassengers()) e.hurt(level().damageSources().flyIntoWall(), (imp - 0.7f) * 30f);
            hurt(level().damageSources().generic(), imp * 80f);
            if (isRemoved()) return;
        }

        // carburant
        if (on) {
            float use = kind.fuelRate * (0.15f + 0.45f * thr() + (boosting() ? 1.1f : 0f)) * Cfg.FUEL_USE.get().floatValue();
            setFuel(fuel() - use);
        }
        // fumee quand abime
        if (hp() < kind.maxHp * 0.35f && level() instanceof ServerLevel sl && tickCount % 3 == 0) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.2, getZ(), 2, 0.3, 0.2, 0.3, 0.02);
            if (hp() < kind.maxHp * 0.15f) sl.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.2, getZ(), 2, 0.3, 0.2, 0.3, 0.02);
        }

        if (cooldown > 0) cooldown--;
        if (clickCd > 0) clickCd--;
        if (on) {
            if (lockWeapon(selected()) && tickCount % 4 == 0) updateLock(selected());
            if (inFire && cooldown <= 0) fire(c);
        } else if (lockId() != -1) entityData.set(LOCK, -1);
    }

    // ---------------------------------------------------------------- armes

    public Vec3 aimDir() {
        float y = kind.heli() ? aimYaw : getYRot();
        float x = kind.heli() ? aimPitch : getXRot();
        if (kind.heli()) x = Mth.clamp(x, -25f, 70f);
        double yr = y * Mth.DEG_TO_RAD, xr = x * Mth.DEG_TO_RAD;
        return new Vec3(-Math.sin(yr) * Math.cos(xr), -Math.sin(xr), Math.cos(yr) * Math.cos(xr));
    }

    private Vec3 leftVec() {
        double a = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(Math.cos(a), 0, Math.sin(a));
    }

    public static boolean lockWeapon(W w) {
        return w == W.MISSILE || w == W.IR || w == W.FLAK || w == W.BUSTER || w == W.CRUISE;
    }

    private void updateLock(W w) {
        double minCos = switch (w) {
            case IR -> 0.78;
            case FLAK -> 0.90;
            case BUSTER -> 0.965;
            case CRUISE -> 0.96;
            default -> 0.94;
        };
        double range = switch (w) {
            case IR -> 180;
            case CRUISE -> 170;
            case FLAK -> 150;
            case BUSTER -> 100;
            default -> 135;
        };
        Vec3 o = position().add(0, kind.h * 0.5, 0), d = aimDir();
        Entity best = null;
        double bs = -1;
        AABB box = new AABB(o, o).inflate(range);
        for (Entity e : level().getEntities(this, box, this::lockable)) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(o);
            double dist = to.length();
            if (dist < 8 || dist > range) continue;
            double cos = to.normalize().dot(d);
            if (cos < minCos) continue;
            BlockHitResult br = level().clip(new ClipContext(o, e.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (br.getType() != HitResult.Type.MISS) continue;
            double score = cos + (e instanceof Enemy ? 0.02 : 0);
            if (e instanceof Aircraft ac) score += w == W.FLAK ? 0.3 : 0.01;
            if (w == W.IR) {
                score += heat(e);
            }
            if (score > bs) {
                bs = score;
                best = e;
            }
        }
        int id = best == null ? -1 : best.getId();
        if (id != lockId()) {
            entityData.set(LOCK, id);
            if (id != -1) level().playSound(null, getX(), getY(), getZ(), Reg.BEEP.get(), SoundSource.NEUTRAL, 0.6f, 1.2f);
        }
    }

    /** Signature thermique d'une cible (pour le missile infrarouge). */
    public static double heat(Entity e) {
        double h = 0;
        if (e instanceof Aircraft ac && ac.engineOn()) h += 0.5 + (ac.boosting() ? 0.3 : 0.0) + ac.thr() * 0.2;
        if (e.isOnFire()) h += 0.25;
        if (e instanceof net.minecraft.world.entity.monster.Blaze || e instanceof net.minecraft.world.entity.monster.MagmaCube) h += 0.3;
        return h;
    }

    private boolean lockable(Entity e) {
        if (!e.isAlive() || e.isSpectator() || hasPassenger(e) || e.getVehicle() == this) return false;
        if (e instanceof Aircraft a) return a != this;
        if (e instanceof Player && !Cfg.LOCK_PLAYERS.get()) return false;
        if (e instanceof ArmorStand) return false;
        return e instanceof LivingEntity;
    }

    private UUID lockedUuid(ServerLevel sl) {
        if (lockId() == -1) return null;
        Entity t = sl.getEntity(lockId());
        return t == null ? null : t.getUUID();
    }

    private Shell launch(ServerLevel sl, W w, LivingEntity pilot, Vec3 o, Vec3 v, UUID target, Vec3 point) {
        Shell s = new Shell(Reg.SHELL.get(), sl);
        s.setPos(o.x, o.y, o.z);
        s.setup(w, pilot, this, v, target);
        if (point != null) s.setPoint(point);
        sl.addFreshEntity(s);
        return s;
    }

    /** Point vise au sol (pour croisiere / balistique) : impact du rayon de visee, sinon le sol 170 blocs devant. */
    public Vec3 aimPoint(Level lv, Vec3 from, Vec3 dir) {
        double range = 170;
        Vec3 end = from.add(dir.scale(range));
        BlockHitResult br = lv.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (br.getType() != HitResult.Type.MISS) return br.getLocation();
        if (!lv.isClientSide) {
            net.minecraft.core.BlockPos bp = lv.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, net.minecraft.core.BlockPos.containing(end));
            return new Vec3(end.x, bp.getY(), end.z);
        }
        return end;
    }

    private void fire(LivingEntity pilot) {
        W w = selected();
        if (!(level() instanceof ServerLevel sl)) return;
        boolean unl = Cfg.UNLIMITED_AMMO.get();
        if (!unl && ammo(w) <= 0) {
            cooldown = 12;
            if (clickCd == 0) {
                clickCd = 20;
                level().playSound(null, getX(), getY(), getZ(), Reg.BEEP.get(), SoundSource.NEUTRAL, 0.6f, 0.6f);
                if (pilot instanceof Player p) p.displayClientMessage(Component.literal("Plus de munitions : " + w.label + " (R pour recharger)"), true);
            }
            return;
        }
        Vec3 dir = aimDir();
        Vec3 left = leftVec();
        alt = !alt;
        double side = alt ? 1 : -1;
        Vec3 base = position().add(0, kind.h * 0.55, 0);
        switch (w) {
            case CANNON -> fireCannon(sl, pilot, base.add(dir.scale(2.6 * Math.max(kind.sc, 0.8f))).add(0, kind.heli() ? -0.7 : -0.1, 0), dir);
            case ROCKET -> {
                Vec3 o = base.add(left.scale(side * (kind.heli() ? 1.7 : 3.6 * kind.sc))).add(dir.scale(2.2)).add(0, -0.4, 0);
                launch(sl, w, pilot, o, dir.scale(w.speed).add(vel.scale(0.4)), null, null);
                sl.playSound(null, o.x, o.y, o.z, Reg.LAUNCH.get(), SoundSource.NEUTRAL, 1.6f, 1.2f);
            }
            case MISSILE, IR, FLAK, BUSTER -> {
                Vec3 o = base.add(left.scale(side * (kind.heli() ? 2.5 : 5.0 * kind.sc))).add(dir.scale(1.5)).add(0, -0.6, 0);
                launch(sl, w, pilot, o, dir.scale(0.9).add(vel.scale(0.5)), lockedUuid(sl), null);
                sl.playSound(null, o.x, o.y, o.z, Reg.LAUNCH.get(), SoundSource.NEUTRAL, w == W.BUSTER ? 2.6f : 2.0f, w == W.BUSTER ? 0.6f : 0.9f);
            }
            case CRUISE -> {
                Vec3 o = base.add(0, 0.2, 0).add(dir.scale(2.0));
                Vec3 pt = aimPoint(sl, base, dir);
                Vec3 h = new Vec3(dir.x, 0, dir.z).normalize();
                launch(sl, w, pilot, o, h.scale(0.5).add(0, 0.9, 0), lockedUuid(sl), pt);
                sl.playSound(null, o.x, o.y, o.z, Reg.LAUNCH.get(), SoundSource.NEUTRAL, 3.0f, 0.5f);
            }
            case BALLISTIC -> {
                Vec3 o = base.add(0, 1.2, 0).add(dir.scale(1.0));
                Vec3 pt = aimPoint(sl, base, dir);
                double dx = pt.x - o.x, dz = pt.z - o.z;
                double d = Math.max(25.0, Math.hypot(dx, dz));
                double th = Math.toRadians(60), g = Shell.BALLISTIC_G;
                double den = Math.max(2.0, d * Math.tan(th) - (pt.y - o.y));
                double v = Math.sqrt(g * d * d / (2 * Math.cos(th) * Math.cos(th) * den));
                double hl = Math.max(1e-4, Math.hypot(dx, dz));
                Vec3 vv = new Vec3(dx / hl * v * Math.cos(th), v * Math.sin(th), dz / hl * v * Math.cos(th));
                launch(sl, w, pilot, o, vv, null, pt);
                sl.playSound(null, o.x, o.y, o.z, Reg.LAUNCH.get(), SoundSource.NEUTRAL, 3.2f, 0.45f);
            }
            case BOMB, BAY, NUKE -> {
                Vec3 o = base.add(left.scale(side * (w == W.BAY || w == W.NUKE ? 0.5 : 0.8))).add(0, -1.1, 0);
                launch(sl, w, pilot, o, vel, null, null);
                if (w == W.NUKE) {
                    if (pilot instanceof Player pp) pp.displayClientMessage(Component.literal("BOMBE NUCLEAIRE LARGUEE - eloignez-vous a plus de 2 km !"), true);
                    for (net.minecraft.server.level.ServerPlayer sp2 : sl.getServer().getPlayerList().getPlayers())
                        sp2.displayClientMessage(Component.literal("ALERTE : bombe nucleaire larguee !"), false);
                }
                sl.playSound(null, o.x, o.y, o.z, SoundEvents.TNT_PRIMED, SoundSource.NEUTRAL, 1.0f, w == W.BAY ? 0.6f : 0.8f);
            }
        }
        shots++;
        if (!unl) setAmmo(w, ammo(w) - 1);
        cooldown = w.cooldown;
    }

    private void fireCannon(ServerLevel sl, LivingEntity pilot, Vec3 from, Vec3 dir) {
        Vec3 end = from.add(dir.scale(110));
        BlockHitResult br = sl.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (br.getType() != HitResult.Type.MISS) end = br.getLocation();
        EntityHitResult er = ProjectileUtil.getEntityHitResult(sl, this, from, end, new AABB(from, end).inflate(1.0),
                e -> e.isAlive() && !e.isSpectator() && e != pilot && !hasPassenger(e) && e != this
                        && (e instanceof LivingEntity || e instanceof Aircraft) && !(e instanceof ArmorStand));
        if (er != null) {
            end = er.getLocation();
            Entity t = er.getEntity();
            t.invulnerableTime = 0;
            DamageSource ds = pilot instanceof Player pp ? sl.damageSources().playerAttack(pp) : sl.damageSources().mobAttack(pilot);
            float dmg = (float) (W.CANNON.dmg * Cfg.DAMAGE_MULT.get());
            if (t instanceof LivingEntity) t.hurt(sl.damageSources().thrown(this, pilot), dmg);
            else t.hurt(ds, dmg);
            sl.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 4, 0.2, 0.2, 0.2, 0.2);
        } else if (br.getType() != HitResult.Type.MISS) {
            sl.sendParticles(ParticleTypes.SMOKE, end.x, end.y, end.z, 3, 0.1, 0.1, 0.1, 0.02);
            sl.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 3, 0.1, 0.1, 0.1, 0.15);
        }
        double len = end.subtract(from).length();
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(1f, 0.82f, 0.25f), 1.1f);
        for (double d = 2; d < len; d += 3.5) {
            Vec3 q = from.add(dir.scale(d));
            sl.sendParticles(dust, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
        sl.playSound(null, from.x, from.y, from.z, Reg.GUN.get(), SoundSource.NEUTRAL, 1.4f, 0.9f + random.nextFloat() * 0.2f);
    }

    // ---------------------------------------------------------------- lerp / sauvegarde

    @Override
    public void lerpTo(double x, double y, double z, float yr, float xr, int steps, boolean teleport) {
        lx = x;
        ly = y;
        lz = z;
        lyr = yr;
        lxr = xr;
        lerpSteps = 4;
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

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        setHp(t.getFloat("Hp"));
        setFuel(t.getFloat("Fuel"));
        entityData.set(LOADOUT, t.contains("Lo") ? (t.getInt("Lo") | W.CANNON.bit()) : kind.defaultLoadout);
        for (W w : W.values()) setAmmo(w, t.getInt("A" + w.ordinal()));
        entityData.set(SEL, t.getInt("Sel"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putFloat("Hp", hp());
        t.putFloat("Fuel", fuel());
        for (W w : W.values()) t.putInt("A" + w.ordinal(), ammo(w));
        t.putInt("Sel", entityData.get(SEL));
        t.putInt("Lo", loadout());
    }

    @Override
    public void remove(RemovalReason r) {
        super.remove(r);
    }

    public UUID uuidForTest() { return getUUID(); }
}
