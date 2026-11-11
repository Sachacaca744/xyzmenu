package fr.uarms;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Pistolet a poivre : cone de gaz devant soi (aveugle, etourdit). */
public class SprayItem extends Item {
    public static final double RANGE = 5.0;

    public SprayItem(Properties p) {
        super(p);
    }

    public static void spray(LivingEntity user) {
        if (!(user.level() instanceof ServerLevel sl)) return;
        Vec3 o = user.getEyePosition(), d = user.getLookAngle();
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(1.0f, 0.45f, 0.1f), 1.6f);
        for (int i = 1; i <= 8; i++) {
            Vec3 p = o.add(d.scale(i * 0.6));
            sl.sendParticles(dust, p.x, p.y, p.z, 6, 0.15 + i * 0.05, 0.15 + i * 0.05, 0.15 + i * 0.05, 0.0);
            sl.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.01);
        }
        sl.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.6f);
        AABB box = user.getBoundingBox().expandTowards(d.scale(RANGE)).inflate(1.5);
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e != user && e.isAlive())) {
            Vec3 to = t.getEyePosition().subtract(o);
            if (to.length() > RANGE + 0.5) continue;
            if (to.normalize().dot(d) < 0.82) continue;
            if (user instanceof Soldier && t instanceof Soldier) continue;
            t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 160, 0));
            t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0));
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 1));
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 0));
            if (t instanceof Mob m) m.setTarget(null);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (p.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(st);
        if (!level.isClientSide) {
            spray(p);
            st.hurtAndBreak(1, p, x -> x.broadcastBreakEvent(hand));
        }
        p.getCooldowns().addCooldown(this, 30);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }
}
