package fr.ugta;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.server.level.ServerLevel;

public class VehicleItem extends Item {
    private final Kind kind;

    public VehicleItem(Kind kind, Properties p) {
        super(p);
        this.kind = kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        if (c.getLevel() instanceof ServerLevel lv) {
            Vehicle v = Reg.create(kind, lv);
            if (v == null) return InteractionResult.FAIL;
            BlockPos p = c.getClickedPos().relative(c.getClickedFace());
            v.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, c.getRotation(), 0);
            lv.addFreshEntity(v);
            if (c.getPlayer() != null && !c.getPlayer().isCreative()) c.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
    }
}
