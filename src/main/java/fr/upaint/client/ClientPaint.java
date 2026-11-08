package fr.upaint.client;

import fr.upaint.PaintData;
import fr.upaint.Upaint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Upaint.MODID, value = Dist.CLIENT)
public class ClientPaint {
    public static final Map<Long, Map<Long, PaintData.Paint[]>> CHUNKS = new HashMap<>();

    private static long ck(BlockPos p) {
        return ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4);
    }

    public static void replaceChunk(int cx, int cz, List<PaintData.Entry> list) {
        Map<Long, PaintData.Paint[]> m = new HashMap<>();
        for (PaintData.Entry e : list) {
            if (e.face() < 0 || e.face() > 5) continue;
            m.computeIfAbsent(e.pos(), k -> new PaintData.Paint[6])[e.face()] = new PaintData.Paint(e.tex(), e.color());
        }
        if (m.isEmpty()) CHUNKS.remove(ChunkPos.asLong(cx, cz));
        else CHUNKS.put(ChunkPos.asLong(cx, cz), m);
    }

    public static void delta(long pos, int face, String tex, int color) {
        if (face < 0 || face > 5) return;
        BlockPos bp = BlockPos.of(pos);
        Map<Long, PaintData.Paint[]> m = CHUNKS.computeIfAbsent(ck(bp), k -> new HashMap<>());
        PaintData.Paint[] a = m.computeIfAbsent(pos, k -> new PaintData.Paint[6]);
        a[face] = tex.isEmpty() ? null : new PaintData.Paint(tex, color);
        boolean any = false;
        for (PaintData.Paint p : a) any |= p != null;
        if (!any) m.remove(pos);
        if (m.isEmpty()) CHUNKS.remove(ck(bp));
    }

    @SubscribeEvent
    public static void onUnload(ChunkEvent.Unload e) {
        if (e.getLevel().isClientSide()) {
            CHUNKS.remove(e.getChunk().getPos().toLong());
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        CHUNKS.clear();
    }
}
