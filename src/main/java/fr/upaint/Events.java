package fr.upaint;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class Events {
    @SubscribeEvent
    public static void onWatch(ChunkWatchEvent.Watch e) {
        PaintData.sendChunk(e.getPlayer(), e.getLevel(), e.getPos());
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (e.getLevel() instanceof ServerLevel sl) {
            PaintData.clearBlock(sl, e.getPos());
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getLevel() instanceof ServerLevel sl) {
            PaintData.clearBlock(sl, e.getPos());
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate e) {
        if (e.getLevel() instanceof ServerLevel sl) {
            for (BlockPos p : e.getAffectedBlocks()) {
                PaintData.clearBlock(sl, p);
            }
        }
    }
}
