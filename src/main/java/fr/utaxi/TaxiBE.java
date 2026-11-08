package fr.utaxi;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Sert uniquement a afficher la statue du Corvaillus au-dessus de la cabine (moitie haute). */
public class TaxiBE extends BlockEntity {
    public TaxiBE(BlockPos pos, BlockState state) {
        super(Reg.BE.get(), pos, state);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2.5).expandTowards(0, 3.5, 0);
    }
}
