package fr.uarms;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

/** Bouclier balistique : bloque balles et coups de face (comme un bouclier, tres resistant). */
public class ShieldItem extends Item {
    public ShieldItem(Properties p) {
        super(p);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack s) {
        return UseAnim.BLOCK;
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
    public boolean canPerformAction(ItemStack stack, ToolAction action) {
        return ToolActions.DEFAULT_SHIELD_ACTIONS.contains(action);
    }

    @Override
    public boolean isValidRepairItem(ItemStack s, ItemStack r) {
        return r.is(net.minecraft.world.item.Items.IRON_INGOT);
    }
}
