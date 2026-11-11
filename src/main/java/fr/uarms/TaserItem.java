package fr.uarms;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Taser : paralyse la cible quelques secondes (portee 7 blocs). */
public class TaserItem extends Item {
    public static final double RANGE = 7.0;

    public TaserItem(Properties p) {
        super(p);
    }

    public static void zap(LivingEntity user) {
        if (!(user.level() instanceof ServerLevel sl)) return;
        Vec3 o = user.getEyePosition(), d = user.getLookAngle();
        EntityHitResult eh = Fire.pick(user, o, d, RANGE);
        Vec3 end = eh != null ? eh.getLocation() : o.add(d.scale(RANGE));
        for (int i = 1; i <= 10; i++) {
            Vec3 p = o.add(end.subtract(o).scale(i / 10.0));
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.05);
        }
        sl.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5f, 2.0f);
        if (eh != null && eh.getEntity() instanceof LivingEntity t) {
            Fire.nonLethalHurt(t, Fire.src(user), 2f);
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 6));
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 2));
            t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0));
            t.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 3));
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, t.getX(), t.getY() + t.getBbHeight() / 2, t.getZ(), 20, 0.4, 0.5, 0.4, 0.1);
            if (t instanceof net.minecraft.world.entity.Mob m) m.setTarget(null);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (p.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(st);
        if (!level.isClientSide) {
            zap(p);
            st.hurtAndBreak(1, p, x -> x.broadcastBreakEvent(hand));
        }
        p.getCooldowns().addCooldown(this, 50);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }
}
