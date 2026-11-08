package fr.upaint;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Boussole : clic droit = change la meteo. */
public class WeatherCompass extends Item {
    public enum Kind { SUN, RAIN, STORM }

    private final Kind kind;

    public WeatherCompass(Kind kind, Properties props) {
        super(props);
        this.kind = kind;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && level.getServer() != null) {
            ServerLevel over = level.getServer().overworld();
            String msg;
            switch (kind) {
                case SUN -> {
                    over.setWeatherParameters(12000, 0, false, false);
                    msg = "Le soleil revient.";
                }
                case RAIN -> {
                    over.setWeatherParameters(0, 6000, true, false);
                    msg = "La pluie se met à tomber.";
                }
                default -> {
                    over.setWeatherParameters(0, 6000, true, true);
                    msg = "L'orage éclate !";
                }
            }
            player.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.AQUA));
            player.getCooldowns().addCooldown(this, 100);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
