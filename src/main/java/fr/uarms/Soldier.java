package fr.uarms;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;

public class Soldier extends Monster {
    public Soldier(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder attrs() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 45.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.ARMOR, 2.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new SoldierAttackGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0f));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    private static final List<Supplier<Item>> WEAPONS = List.of(
            Reg.KNIFE::get, Reg.WAR_KNIFE::get, Reg.PISTOL::get, Reg.PISTOL::get, Reg.SMG::get, Reg.SMG::get,
            Reg.RIFLE::get, Reg.RIFLE::get, Reg.SHOTGUN::get, Reg.SNIPER::get, Reg.TASER::get,
            Reg.FLASHBALL::get, Reg.GOMME_COGNE::get, Reg.PEPPER::get, Reg.BATON::get);

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance diff, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        SpawnGroupData r = super.finalizeSpawn(level, diff, reason, data, tag);
        Item w = WEAPONS.get(random.nextInt(WEAPONS.size())).get();
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(w));
        setDropChance(EquipmentSlot.MAINHAND, 0.06f);
        float x = random.nextFloat();
        if (x < 0.5f) {
            setItemSlot(EquipmentSlot.HEAD, new ItemStack(x < 0.2f ? Items.IRON_HELMET : x < 0.35f ? Items.CHAINMAIL_HELMET : Items.LEATHER_HELMET));
            setDropChance(EquipmentSlot.HEAD, 0.03f);
        }
        if (random.nextFloat() < 0.5f) {
            setItemSlot(EquipmentSlot.CHEST, new ItemStack(random.nextBoolean() ? Items.CHAINMAIL_CHESTPLATE : Items.LEATHER_CHESTPLATE));
            setDropChance(EquipmentSlot.CHEST, 0.03f);
        }
        return r;
    }

    public boolean holdsGun() {
        return getMainHandItem().getItem() instanceof GunItem;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource s) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource s, int looting, boolean recent) {
        super.dropCustomDeathLoot(s, looting, recent);
        if (random.nextFloat() < 0.35f) spawnAtLocation(new ItemStack(Reg.BULLET.get(), 4 + random.nextInt(9)));
        if (random.nextFloat() < 0.12f) spawnAtLocation(new ItemStack(Reg.SHELL.get(), 3 + random.nextInt(5)));
        if (random.nextFloat() < 0.12f) spawnAtLocation(new ItemStack(Reg.RUBBER.get(), 3 + random.nextInt(5)));
    }

    // ---------------------------------------------------------------- IA de combat
    static class SoldierAttackGoal extends Goal {
        private final Soldier m;
        private int cd = 20, repath = 0;

        SoldierAttackGoal(Soldier m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = m.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            m.setAggressive(true);
        }

        @Override
        public void stop() {
            m.setAggressive(false);
            m.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = m.getTarget();
            if (t == null) return;
            m.getLookControl().setLookAt(t, 30f, 30f);
            double d = m.distanceTo(t);
            boolean los = m.getSensing().hasLineOfSight(t);
            Item it = m.getMainHandItem().getItem();
            cd--;
            if (it instanceof GunItem g) {
                double want = Math.min(g.spec().range() * 0.6, 28);
                if (!los || d > want) {
                    if (--repath <= 0) {
                        m.getNavigation().moveTo(t, 1.05);
                        repath = 10;
                    }
                } else {
                    m.getNavigation().stop();
                }
                if (los && d <= g.spec().range() && cd <= 0) {
                    Fire.mobFire(m, t, g);
                    m.swing(InteractionHand.MAIN_HAND);
                    cd = (int) (g.spec().cooldown() * (g.spec().auto() ? 3 : 1.5)) + m.getRandom().nextInt(10) + (g.spec().auto() ? 8 : 0);
                }
            } else if (it instanceof TaserItem || it instanceof SprayItem) {
                double reach = it instanceof TaserItem ? 5.5 : 4.0;
                if (d > reach - 1 || !los) {
                    if (--repath <= 0) {
                        m.getNavigation().moveTo(t, 1.1);
                        repath = 10;
                    }
                } else {
                    m.getNavigation().stop();
                }
                if (los && d <= reach && cd <= 0) {
                    if (it instanceof TaserItem) TaserItem.zap(m);
                    else SprayItem.spray(m);
                    m.swing(InteractionHand.MAIN_HAND);
                    cd = 60;
                }
            } else {
                if (--repath <= 0) {
                    m.getNavigation().moveTo(t, 1.15);
                    repath = 8;
                }
                if (d < 2.4 && los && cd <= 0) {
                    m.swing(InteractionHand.MAIN_HAND);
                    if (it instanceof BatonItem) BatonItem.hit(m, t);
                    else m.doHurtTarget(t);
                    cd = 18;
                }
            }
        }
    }
}
