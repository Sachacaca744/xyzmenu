package fr.upolice;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ItemParticleOption;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Lanceur de filet : immobilise la cible 6 secondes. */
public class NetGun extends Item {
    public NetGun(Properties p) {
        super(p);
    }

    static void bind(LivingEntity t, int ticks) {
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 10));
        t.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, 128));
        t.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 2));
        Util.calm(t);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (p.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(st);
        if (level instanceof ServerLevel sl) {
            Vec3 o = p.getEyePosition(), d = p.getLookAngle();
            double range = 15;
            EntityHitResult eh = Util.pick(p, o, d, range);
            Vec3 end = eh != null ? eh.getLocation() : Util.end(p, o, d, range);
            var cob = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COBWEB));
            Util.trace(sl, o, end, cob, 1.0, 20);
            if (eh != null && eh.getEntity() instanceof LivingEntity t) {
                bind(t, 120);
                sl.sendParticles(cob, t.getX(), t.getY() + t.getBbHeight() / 2, t.getZ(), 40, 0.4, 0.6, 0.4, 0.05);
            }
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1f, 0.7f);
            st.hurtAndBreak(1, p, x -> x.broadcastBreakEvent(hand));
        }
        p.getCooldowns().addCooldown(this, 60);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal("Immobilise la cible 6 secondes (15 blocs).").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Non létal").withStyle(ChatFormatting.GREEN));
    }
}
