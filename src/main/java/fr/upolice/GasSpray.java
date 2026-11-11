package fr.upolice;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/** Bombe lacrymogene (gaz CS) : cone de 6 blocs. */
public class GasSpray extends Item {
    public GasSpray(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (p.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(st);
        if (level instanceof ServerLevel sl) {
            Vec3 o = p.getEyePosition(), d = p.getLookAngle();
            var dust = new DustParticleOptions(new Vector3f(0.95f, 0.95f, 0.7f), 1.8f);
            for (int i = 1; i <= 10; i++) {
                Vec3 q = o.add(d.scale(i * 0.6));
                sl.sendParticles(dust, q.x, q.y, q.z, 7, 0.1 + i * 0.06, 0.1 + i * 0.06, 0.1 + i * 0.06, 0.0);
                sl.sendParticles(ParticleTypes.CLOUD, q.x, q.y, q.z, 1, 0.1, 0.1, 0.1, 0.01);
            }
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1f, 1.3f);
            AABB box = p.getBoundingBox().expandTowards(d.scale(6)).inflate(1.8);
            for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive())) {
                Vec3 to = t.getEyePosition().subtract(o);
                if (to.length() > 6.5 || to.normalize().dot(d) < 0.78) continue;
                t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0));
                t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
                t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
                Util.calm(t);
            }
            st.hurtAndBreak(1, p, x -> x.broadcastBreakEvent(hand));
        }
        p.getCooldowns().addCooldown(this, 30);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal("Gaz lacrymogène en cône : aveugle, étourdit, ralentit.").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Non létal").withStyle(ChatFormatting.GREEN));
    }
}
