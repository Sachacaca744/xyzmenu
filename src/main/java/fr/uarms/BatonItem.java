package fr.uarms;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Matraque : coup non letal qui repousse et ralentit. */
public class BatonItem extends Item {
    public BatonItem(Properties p) {
        super(p);
    }

    public static void hit(LivingEntity user, LivingEntity t) {
        Fire.nonLethalHurt(t, Fire.src(user), 3f);
        double dx = t.getX() - user.getX(), dz = t.getZ() - user.getZ();
        t.knockback(0.8, -dx, -dz);
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
        user.level().playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1f, 0.9f);
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (!(entity instanceof LivingEntity le) || !le.isAlive()) return false;
        if (player.level().isClientSide) return true;
        if (player.getCooldowns().isOnCooldown(this)) return true;
        hit(player, le);
        player.getCooldowns().addCooldown(this, 14);
        return true;
    }
}
