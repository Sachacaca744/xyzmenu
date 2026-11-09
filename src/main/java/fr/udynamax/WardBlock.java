package fr.udynamax;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Bloc invisible, sans collision : interdit le Dynamax dans 100x100 blocs autour. */
public class WardBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(5, 5, 5, 11, 11, 11);

    public WardBlock() {
        super(BlockBehaviour.Properties.of().strength(1.0f, 3600000f).noCollission().noOcclusion().lightLevel(s -> 0));
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        return SHAPE;
    }

    @Override
    public float getShadeBrightness(BlockState s, BlockGetter g, BlockPos p) {
        return 1.0f;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter g, BlockPos p) {
        return true;
    }

    /** Seuls les OP (ou le creatif) peuvent le casser. */
    @Override
    public float getDestroyProgress(BlockState s, Player player, BlockGetter g, BlockPos p) {
        if (!player.isCreative() && !player.hasPermissions(2)) return 0.0f;
        return super.getDestroyProgress(s, player, g, p);
    }

    @Override
    public void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(s, level, pos, old, moving);
        if (level instanceof ServerLevel sl) WardData.get(sl).add(pos);
    }

    @Override
    public void onRemove(BlockState s, Level level, BlockPos pos, BlockState now, boolean moving) {
        if (!s.is(now.getBlock()) && level instanceof ServerLevel sl) WardData.get(sl).remove(pos);
        super.onRemove(s, level, pos, now, moving);
    }
}
