package fr.uarms;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GunItem extends Item {
    public record Spec(float damage, int cooldown, float range, int pellets, float spread, boolean auto,
                       Supplier<Item> ammo, boolean nonLethal, float knock, Consumer<LivingEntity> onHit,
                       SoundEvent sound, float pitch, String desc) {
    }

    private final Spec spec;

    public GunItem(Spec spec, Properties p) {
        super(p);
        this.spec = spec;
    }

    public Spec spec() {
        return spec;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack s) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack s) {
        return spec.auto() ? 72000 : 0;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (spec.auto()) p.startUsingItem(hand);
        fire(level, p);
        return InteractionResultHolder.consume(st);
    }

    @Override
    public void onUseTick(Level level, LivingEntity e, ItemStack s, int remaining) {
        if (spec.auto() && e instanceof Player p) fire(level, p);
    }

    private void fire(Level level, Player p) {
        if (!(level instanceof ServerLevel sl)) return;
        if (p.getCooldowns().isOnCooldown(this)) return;
        if (!p.isCreative()) {
            ItemStack ammo = ItemStack.EMPTY;
            for (ItemStack s : p.getInventory().items) {
                if (s.is(spec.ammo().get())) {
                    ammo = s;
                    break;
                }
            }
            if (ammo.isEmpty()) {
                p.displayClientMessage(Component.literal("Plus de munitions !").withStyle(ChatFormatting.RED), true);
                sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.DISPENSER_FAIL, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1.5f);
                p.getCooldowns().addCooldown(this, 10);
                return;
            }
            ammo.shrink(1);
        }
        Fire.shoot(sl, p, p.getEyePosition(), p.getLookAngle(), spec, 1f, 0f);
        p.getCooldowns().addCooldown(this, spec.cooldown());
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal(spec.desc()).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Munition : " + spec.ammo().get().getDescription().getString()).withStyle(ChatFormatting.DARK_GRAY));
        if (spec.nonLethal()) tip.add(Component.literal("Non létal").withStyle(ChatFormatting.GREEN));
    }
}
