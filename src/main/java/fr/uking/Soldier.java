package fr.uking;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.DyeableArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.EnumSet;
import java.util.UUID;

/** Soldat / paysan / seigneur. Un seul type d'entite, le role vient de UType. */
public class Soldier extends PathfinderMob implements RangedAttackMob {
    static final EntityDataAccessor<Integer> TYPE = SynchedEntityData.defineId(Soldier.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(Soldier.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> ORDER = SynchedEntityData.defineId(Soldier.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> FIEF = SynchedEntityData.defineId(Soldier.class, EntityDataSerializers.INT);
    public static final int O_FOLLOW = 0, O_HOLD = 1, O_DEFEND = 2, O_RALLY = 3;
    public static final int[] COLORS = {0x2D5BFF, 0xC62828, 0x2E7D32, 0xF9A825, 0x6A1B9A, 0x303030};

    BlockPos home, rally;
    String keyCache = "none";
    UUID ownerCache;

    public Soldier(EntityType<? extends Soldier> t, Level l) {
        super(t, l);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attrs() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.FOLLOW_RANGE, 30).add(Attributes.KNOCKBACK_RESISTANCE, 0.1);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(TYPE, 1);
        entityData.define(COLOR, 0);
        entityData.define(ORDER, 0);
        entityData.define(FIEF, -1);
    }

    public UType type() { return UType.of(entityData.get(TYPE)); }
    public int getFiefId() { return entityData.get(FIEF); }
    public int colorIdx() { return entityData.get(COLOR); }
    public int order() { return entityData.get(ORDER); }
    public void setOrder(int o) { entityData.set(ORDER, o); }
    public void setRally(BlockPos p) { rally = p; }
    public BlockPos getRally() { return rally; }
    public String key() { return keyCache; }

    @Override
    protected Component getTypeName() {
        return Component.translatable("uking.unit." + type().id);
    }

    public void setup(UType t, Fief f) {
        entityData.set(TYPE, t.ordinal());
        entityData.set(FIEF, f.id);
        entityData.set(COLOR, f.color);
        keyCache = f.key();
        ownerCache = f.owner;
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(t.hp);
        setHealth(t.hp);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(t.dmg);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(t.speed);
        if (t == UType.LORD) {
            getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.6);
            setCustomName(Component.literal(f.lord.isEmpty() ? "Seigneur" : f.lord));
            setCustomNameVisible(true);
        }
        home = blockPosition();
        setOrder(f.owner == null ? O_DEFEND : O_FOLLOW);
        equip(t, COLORS[Math.floorMod(f.color, COLORS.length)]);
    }

    static ItemStack dyed(net.minecraft.world.item.Item it, int rgb) {
        ItemStack s = new ItemStack(it);
        if (it instanceof DyeableArmorItem d) d.setColor(s, rgb);
        return s;
    }

    void equip(UType t, int rgb) {
        for (EquipmentSlot s : EquipmentSlot.values()) { setItemSlot(s, ItemStack.EMPTY); setDropChance(s, 0f); }
        switch (t) {
            case PEASANT -> setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
            case SPEARMAN -> {
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Reg.SPEAR.get()));
                setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
                setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, rgb));
            }
            case ARCHER -> {
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, rgb));
            }
            case CROSSBOW -> {
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Reg.CROSSBOW.get()));
                setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, rgb));
            }
            case KNIGHT -> {
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Reg.KNIGHT_SWORD.get()));
                setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            }
            case LORD -> {
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Reg.MACE.get()));
                setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
                setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            }
        }
    }

    // ---------------------------------------------------------------- IA
    boolean melee() { UType t = type(); return t.fighter() && !t.ranged; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.5) {
            @Override public boolean canUse() { return type() == UType.PEASANT && super.canUse(); }
        });
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true) {
            @Override public boolean canUse() { return melee() && gate() && super.canUse(); }
            @Override public boolean canContinueToUse() { return melee() && gate() && super.canContinueToUse(); }
        });
        goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 32, 22f) {
            @Override public boolean canUse() { return type() == UType.ARCHER && super.canUse(); }
            @Override public boolean canContinueToUse() { return type() == UType.ARCHER && super.canContinueToUse(); }
        });
        goalSelector.addGoal(2, new RangedAttackGoal(this, 0.95, 58, 28f) {
            @Override public boolean canUse() { return type() == UType.CROSSBOW && super.canUse(); }
            @Override public boolean canContinueToUse() { return type() == UType.CROSSBOW && super.canContinueToUse(); }
        });
        goalSelector.addGoal(4, new OrderMove());
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override public boolean canUse() { return type() == UType.PEASANT && super.canUse(); }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override public boolean canUse() { return type().fighter() && super.canUse(); }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 8, false, false, e -> type().fighter() && isEnemy(e)));
    }

    /** En mode "tenir la position" les corps a corps ne chargent que ce qui est tout proche. */
    boolean gate() {
        if (order() != O_HOLD) return true;
        LivingEntity t = getTarget();
        return t != null && distanceToSqr(t) < 16;
    }

    public boolean isEnemy(LivingEntity e) {
        if (e == this || !e.isAlive() || !(level() instanceof ServerLevel)) return false;
        return hostileTo(Fiefs.get(level()), keyCache, e);
    }

    public static boolean hostileTo(Fiefs fs, String key, Entity e) {
        if (e instanceof Soldier s) return !s.key().equals(key) && fs.war(key, s.key());
        if (e instanceof Catapult c) return fs.war(key, c.ownerKey());
        if (e instanceof ServerPlayer p) return !p.isCreative() && !p.isSpectator() && fs.war(key, p.getUUID().toString());
        return e instanceof Monster;
    }

    class OrderMove extends Goal {
        int cd;

        OrderMove() { setFlags(EnumSet.of(Flag.MOVE)); }

        BlockPos dest() {
            if (type() == UType.PEASANT || getTarget() != null) return null;
            switch (order()) {
                case O_FOLLOW -> {
                    if (ownerCache == null || !(level() instanceof ServerLevel sl)) return null;
                    ServerPlayer p = sl.getServer().getPlayerList().getPlayer(ownerCache);
                    if (p == null || p.level() != level()) return null;
                    return p.distanceToSqr(Soldier.this) > 49 ? p.blockPosition() : null;
                }
                case O_DEFEND -> {
                    return home != null && home.distSqr(blockPosition()) > 100 ? home : null;
                }
                case O_RALLY -> {
                    return rally != null && rally.distSqr(blockPosition()) > 9 ? rally : null;
                }
                default -> { return null; }
            }
        }

        @Override public boolean canUse() { return dest() != null; }
        @Override public boolean canContinueToUse() { return dest() != null; }

        @Override
        public void tick() {
            BlockPos d = dest();
            if (d == null) return;
            if (--cd <= 0) {
                cd = 10;
                getNavigation().moveTo(d.getX() + 0.5, d.getY(), d.getZ() + 0.5, 1.2);
            }
            if (order() == O_FOLLOW && distanceToSqr(d.getX(), d.getY(), d.getZ()) > 45 * 45 && onGround()) {
                teleportTo(d.getX() + 0.5, d.getY(), d.getZ() + 0.5);
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || tickCount % 20 != 3) return;
        Fief f = Fiefs.get(level()).byId(getFiefId());
        if (f != null) {
            keyCache = f.key();
            ownerCache = f.owner;
            if (entityData.get(COLOR) != f.color) entityData.set(COLOR, f.color);
        }
    }

    // ---------------------------------------------------------------- combat
    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        UType t = type();
        Arrow a = new Arrow(level(), this);
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        double dy = target.getY(0.33) - a.getY();
        double dist = Math.sqrt(dx * dx + dz * dz);
        boolean x = t == UType.CROSSBOW;
        a.shoot(dx, dy + dist * (x ? 0.08 : 0.2), dz, x ? 3.2f : 1.7f, x ? 1.5f : 4f);
        a.setBaseDamage(x ? 5.0 : 2.0);
        a.pickup = AbstractArrow.Pickup.DISALLOWED;
        level().addFreshEntity(a);
        playSound(x ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.SKELETON_SHOOT, 1f, 1f);
    }

    @Override
    public boolean hurt(DamageSource src, float amt) {
        Entity att = src.getEntity();
        if (att instanceof Soldier a && a.key().equals(keyCache)) return false;
        if (att instanceof Catapult c && c.ownerKey().equals(keyCache)) return false;
        if (att instanceof ServerPlayer p && level() instanceof ServerLevel sl) {
            String pk = p.getUUID().toString();
            if (pk.equals(keyCache)) return false;
            Fiefs fs = Fiefs.get(sl);
            Fief f = fs.byId(getFiefId());
            if (f != null && !fs.war(pk, keyCache) && !p.isCreative()) {
                fs.declareWar(pk, f.key());
                sl.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§c⚔ " + p.getGameProfile().getName() + " a attaqué " + f.name + " : c'est la guerre !"), false);
            }
        }
        return super.hurt(src, amt);
    }

    @Override
    public void die(DamageSource src) {
        super.die(src);
        if (!level().isClientSide && type() == UType.LORD && level() instanceof ServerLevel sl)
            sl.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§6" + (getCustomName() == null ? "Le seigneur" : getCustomName().getString()) + " est tombé !"), false);
    }

    @Override
    public void remove(RemovalReason r) {
        if (!level().isClientSide && r.shouldDestroy() && level() instanceof ServerLevel sl) {
            Fief f = Fiefs.get(sl).byId(getFiefId());
            if (f != null) {
                f.army.remove(getUUID());
                f.peasants.remove(getUUID());
                Fiefs.get(sl).setDirty();
            }
        }
        super.remove(r);
    }

    @Override
    protected InteractionResult mobInteract(Player p, InteractionHand h) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (p.getUUID().toString().equals(keyCache) && type().fighter() && p.getItemInHand(h).isEmpty()) {
            int o = (order() + 1) % 3;
            setOrder(o);
            home = blockPosition();
            p.displayClientMessage(Component.translatable("uking.order." + o), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override protected int getExperienceReward() { return 0; }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("type", entityData.get(TYPE));
        t.putInt("fief", entityData.get(FIEF));
        t.putInt("order", entityData.get(ORDER));
        if (home != null) t.put("home", NbtUtils.writeBlockPos(home));
        if (rally != null) t.put("rally", NbtUtils.writeBlockPos(rally));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        entityData.set(TYPE, t.getInt("type"));
        entityData.set(FIEF, t.getInt("fief"));
        entityData.set(ORDER, t.getInt("order"));
        if (t.contains("home")) home = NbtUtils.readBlockPos(t.getCompound("home"));
        if (t.contains("rally")) rally = NbtUtils.readBlockPos(t.getCompound("rally"));
        if (home == null) home = blockPosition();
        if (!level().isClientSide && level() instanceof ServerLevel sl) {
            Fief f = Fiefs.get(sl).byId(getFiefId());
            if (f != null) { keyCache = f.key(); ownerCache = f.owner; entityData.set(COLOR, f.color); }
        }
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }
}
