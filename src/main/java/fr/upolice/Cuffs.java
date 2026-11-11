package fr.upolice;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Menottes : clic droit sur quelqu'un = immobilise 20 s. Accroupi + clic droit = libere. */
public class Cuffs extends Item {
    public Cuffs(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity t, InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResult.FAIL;
        if (player.isShiftKeyDown()) {
            t.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            t.removeEffect(MobEffects.JUMP);
            t.removeEffect(MobEffects.DIG_SLOWDOWN);
            t.removeEffect(MobEffects.WEAKNESS);
            player.displayClientMessage(Component.literal("Libéré.").withStyle(ChatFormatting.GREEN), true);
        } else {
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 10));
            t.addEffect(new MobEffectInstance(MobEffects.JUMP, 400, 128));
            t.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 400, 3));
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 3));
            Util.calm(t);
            player.displayClientMessage(Component.literal("Menotté ! (20 s)").withStyle(ChatFormatting.AQUA), true);
            stack.hurtAndBreak(1, player, x -> x.broadcastBreakEvent(hand));
        }
        player.getCooldowns().addCooldown(this, 40);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal("Clic droit sur une cible : menottée 20 s. Accroupi : la libère.").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Non létal").withStyle(ChatFormatting.GREEN));
    }
}
