package fr.uking;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class FiefStoneBlock extends Block {
    public FiefStoneBlock(Properties p) { super(p); }

    @Override
    public void setPlacedBy(Level l, BlockPos pos, BlockState st, LivingEntity placer, ItemStack stack) {
        if (l.isClientSide || !(placer instanceof ServerPlayer sp)) return;
        String err = Fiefs.get(l).found(sp, pos);
        if (err != null) {
            l.removeBlock(pos, false);
            sp.displayClientMessage(Component.literal("§c" + err), false);
            if (!sp.isCreative()) { ItemStack s = new ItemStack(Reg.FIEF_STONE_ITEM.get()); if (!sp.getInventory().add(s)) sp.drop(s, false); }
        }
    }

    @Override
    public InteractionResult use(BlockState st, Level l, BlockPos pos, Player p, InteractionHand h, BlockHitResult hit) {
        if (l.isClientSide) return InteractionResult.SUCCESS;
        Fiefs fs = Fiefs.get(l);
        Fief f = fs.byPos(pos);
        if (f != null && p instanceof ServerPlayer sp) fs.openGui(sp, f);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState st, Level l, BlockPos pos, BlockState ns, boolean moved) {
        if (!l.isClientSide && !st.is(ns.getBlock())) Fiefs.get(l).removeAt(pos);
        super.onRemove(st, l, pos, ns, moved);
    }
}
