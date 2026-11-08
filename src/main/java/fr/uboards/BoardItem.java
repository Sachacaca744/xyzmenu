package fr.uboards;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class BoardItem extends BlockItem {
    private final boolean screen;

    public BoardItem(Block block, Properties props, boolean screen) {
        super(block, props);
        this.screen = screen;
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag t = stack.getTagElement("BlockEntityTag");
        int w = 1, h = 1;
        if (t != null) {
            if (t.contains("W")) w = BoardBE.clamp(t.getInt("W"));
            if (t.contains("H")) h = BoardBE.clamp(t.getInt("H"));
        }
        return Component.literal((screen ? "Écran blanc " : "Tableau ") + w + "x" + h);
    }
}
