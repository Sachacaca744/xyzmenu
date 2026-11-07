package fr.uitems;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;

/** Epee du Fondateur. Le coup mortel est gere dans Events (il faut le niveau OP 4). */
public class FounderSword extends SwordItem {
    public FounderSword(Properties properties) {
        super(Tiers.NETHERITE, 7, -2.2F, properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
