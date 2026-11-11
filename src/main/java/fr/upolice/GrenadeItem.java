package fr.upolice;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GrenadeItem extends Item {
    public enum Kind {TEAR, SMOKE, FLASH, BURST}

    public final Kind kind;

    public GrenadeItem(Kind kind, Properties p) {
        super(p);
        this.kind = kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (p.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(st);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6f, 0.6f);
        if (!level.isClientSide) {
            GrenadeEntity g = new GrenadeEntity(level, p);
            ItemStack one = st.copy();
            one.setCount(1);
            g.setItem(one);
            g.shootFromRotation(p, p.getXRot(), p.getYRot(), -5.0f, 1.2f, 1.0f);
            level.addFreshEntity(g);
        }
        if (!p.isCreative()) st.shrink(1);
        p.getCooldowns().addCooldown(this, 20);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        String t = switch (kind) {
            case TEAR -> "Lancer : nuage de gaz lacrymogène pendant 10 s (aveugle, nausée, ralentit).";
            case SMOKE -> "Lancer : épais nuage de fumée pendant 15 s.";
            case FLASH -> "Lancer : détonation + flash : aveugle et étourdit dans 10 blocs.";
            case BURST -> "Lancer : onde de choc qui repousse tout le monde autour (non létale).";
        };
        tip.add(Component.literal(t).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Non létal").withStyle(ChatFormatting.GREEN));
    }
}
