package fr.udrone;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Station de controle : liste les drones lies, carte, telemetrie et commandes. */
public class StationBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 14, 16);

    public StationBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new StationBE(p, s);
    }

    @Override
    public InteractionResult use(BlockState st, Level l, BlockPos pos, Player p, InteractionHand h, BlockHitResult hit) {
        if (l.isClientSide) return InteractionResult.SUCCESS;
        if (!(p instanceof ServerPlayer sp) || !(l.getBlockEntity(pos) instanceof StationBE be)) return InteractionResult.PASS;
        ItemStack held = p.getItemInHand(h);
        ServerLevel sl = (ServerLevel) l;
        if (held.getItem() instanceof ControllerItem) {
            int n = 0;
            for (UUID u : ControllerItem.list(held)) {
                Entity e = sl.getEntity(u);
                if (e instanceof Drone d && be.link(u, d.kind.ordinal())) n++;
            }
            p.displayClientMessage(Component.literal(n + " drone(s) lie(s) a la station (" + be.drones.size() + "/6)."), true);
            return InteractionResult.CONSUME;
        }
        sendData(sp, pos, be, true);
        return InteractionResult.CONSUME;
    }

    public static void sendData(ServerPlayer sp, BlockPos pos, StationBE be, boolean open) {
        ServerLevel sl = sp.serverLevel();
        List<Net.Entry> list = new ArrayList<>();
        for (Map.Entry<UUID, Integer> e : be.drones.entrySet()) {
            Entity en = sl.getEntity(e.getKey());
            if (en instanceof Drone d && d.state() != Drone.DEAD) {
                list.add(new Net.Entry(e.getKey(), e.getValue(), true, (float) d.getX(), (float) d.getY(), (float) d.getZ(),
                        d.getYRot(), d.battery(), d.hp(), d.state(), d.ammoPacked(), d.sel(), d.heat(), d.signal(), d.piloted()));
            } else {
                list.add(new Net.Entry(e.getKey(), e.getValue(), false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false));
            }
        }
        Net.CH.send(PacketDistributor.PLAYER.with(() -> sp), new Net.StationMsg(pos, open, list));
    }
}
