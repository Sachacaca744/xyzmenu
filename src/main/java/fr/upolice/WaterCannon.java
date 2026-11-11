package fr.upolice;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Canon a eau : jet continu qui repousse et eteint les flammes. */
public class WaterCannon extends Item {
    public WaterCannon(Properties p) {
        super(p);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack s) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack s) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        p.startUsingItem(hand);
        return InteractionResultHolder.consume(p.getItemInHand(hand));
    }

    @Override
    public void onUseTick(Level level, LivingEntity e, ItemStack s, int remaining) {
        if (!(level instanceof ServerLevel sl)) return;
        Vec3 o = e.getEyePosition().add(e.getLookAngle().scale(0.6)), d = e.getLookAngle();
        double range = 13;
        EntityHitResult eh = Util.pick(e, o, d, range);
        Vec3 end = eh != null ? eh.getLocation() : Util.end(e, o, d, range);
        Util.trace(sl, o, end, ParticleTypes.SPLASH, 0.7, 24);
        Util.trace(sl, o, end, ParticleTypes.FALLING_WATER, 1.1, 14);
        if (eh != null && eh.getEntity() instanceof LivingEntity t) {
            t.knockback(0.55, -d.x, -d.z);
            t.clearFire();
            if (e.tickCount % 10 == 0) {
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
                Util.calm(t);
            }
            sl.sendParticles(ParticleTypes.SPLASH, t.getX(), t.getY() + 1, t.getZ(), 10, 0.3, 0.5, 0.3, 0.1);
        }
        if (e.tickCount % 8 == 0) sl.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.35f, 1.6f);
        if (e.tickCount % 20 == 0) s.hurtAndBreak(1, e, x -> x.broadcastBreakEvent(InteractionHand.MAIN_HAND));
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal("Jet d'eau continu (maintiens le clic droit) : repousse et éteint.").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Non létal").withStyle(ChatFormatting.GREEN));
    }
}
