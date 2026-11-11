package fr.upolice;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.Consumer;

/** Arme de tir non letale (LBD 40, marqueur de peinture...). */
public class ShotItem extends Item {
    public record Spec(float damage, int cooldown, double range, float knock, Consumer<LivingEntity> onHit,
                       SoundEvent sound, float pitch, boolean paint, String desc) {
    }

    private final Spec spec;

    public ShotItem(Spec spec, Properties p) {
        super(p);
        this.spec = spec;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (p.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(st);
        if (level instanceof ServerLevel sl) {
            Vec3 o = p.getEyePosition(), d = p.getLookAngle();
            EntityHitResult eh = Util.pick(p, o, d, spec.range());
            Vec3 end = eh != null ? eh.getLocation() : Util.end(p, o, d, spec.range());
            ParticleOptions part;
            if (spec.paint()) {
                float r = sl.random.nextFloat(), g = sl.random.nextFloat(), b = sl.random.nextFloat();
                part = new DustParticleOptions(new Vector3f(Math.max(r, 0.2f), g, b), 1.4f);
            } else {
                part = ParticleTypes.POOF;
            }
            Util.trace(sl, o, end, part, 1.2, 40);
            if (eh != null && eh.getEntity() instanceof LivingEntity t) {
                Util.hurt(t, p, spec.damage());
                if (spec.knock() > 0) t.knockback(spec.knock(), -d.x, -d.z);
                if (spec.onHit() != null) spec.onHit().accept(t);
                if (spec.paint()) sl.sendParticles(part, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 25, 0.4, 0.5, 0.4, 0.05);
            } else if (spec.paint()) {
                sl.sendParticles(part, end.x, end.y, end.z, 20, 0.3, 0.3, 0.3, 0.05);
            }
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), spec.sound(), SoundSource.PLAYERS, 1f, spec.pitch());
            st.hurtAndBreak(1, p, x -> x.broadcastBreakEvent(hand));
        }
        p.getCooldowns().addCooldown(this, spec.cooldown());
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal(spec.desc()).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Non létal").withStyle(ChatFormatting.GREEN));
    }
}
