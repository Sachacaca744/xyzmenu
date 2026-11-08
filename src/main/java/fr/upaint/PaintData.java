package fr.upaint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Peintures d'un monde : chunk -> bloc -> 6 faces. Sauvegarde dans data/upaint.dat. */
public class PaintData extends SavedData {
    public record Paint(String tex, int color) {}

    public record Entry(long pos, int face, String tex, int color) {}

    private final Map<Long, Map<Long, Paint[]>> byChunk = new HashMap<>();

    public static PaintData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PaintData::load, PaintData::new, "upaint");
    }

    public static PaintData load(CompoundTag tag) {
        PaintData d = new PaintData();
        ListTag list = tag.getList("E", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            int f = t.getByte("f");
            if (f < 0 || f > 5) continue;
            d.put(BlockPos.of(t.getLong("p")), f, new Paint(t.getString("t"), t.getInt("c")));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map<Long, Paint[]> m : byChunk.values()) {
            for (Map.Entry<Long, Paint[]> e : m.entrySet()) {
                for (int f = 0; f < 6; f++) {
                    Paint p = e.getValue()[f];
                    if (p == null) continue;
                    CompoundTag t = new CompoundTag();
                    t.putLong("p", e.getKey());
                    t.putByte("f", (byte) f);
                    t.putString("t", p.tex());
                    t.putInt("c", p.color());
                    list.add(t);
                }
            }
        }
        tag.put("E", list);
        return tag;
    }

    private static long ck(BlockPos p) {
        return ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4);
    }

    private void put(BlockPos pos, int face, Paint p) {
        byChunk.computeIfAbsent(ck(pos), k -> new HashMap<>())
                .computeIfAbsent(pos.asLong(), k -> new Paint[6])[face] = p;
    }

    public Paint get(BlockPos pos, int face) {
        Map<Long, Paint[]> m = byChunk.get(ck(pos));
        if (m == null) return null;
        Paint[] a = m.get(pos.asLong());
        return a == null ? null : a[face];
    }

    public List<Entry> entriesOf(ChunkPos cp) {
        List<Entry> out = new ArrayList<>();
        Map<Long, Paint[]> m = byChunk.get(cp.toLong());
        if (m == null) return out;
        for (Map.Entry<Long, Paint[]> e : m.entrySet()) {
            for (int f = 0; f < 6; f++) {
                Paint p = e.getValue()[f];
                if (p != null) out.add(new Entry(e.getKey(), f, p.tex(), p.color()));
            }
        }
        return out;
    }

    // ---------- operations serveur (avec envoi aux joueurs proches) ----------

    public static void set(ServerLevel level, BlockPos pos, Direction face, Paint paint) {
        PaintData d = get(level);
        Paint old = d.get(pos, face.get3DDataValue());
        if (paint.equals(old)) return;
        d.put(pos, face.get3DDataValue(), paint);
        d.setDirty();
        broadcast(level, pos, face.get3DDataValue(), paint.tex(), paint.color());
    }

    public static boolean remove(ServerLevel level, BlockPos pos, Direction face) {
        PaintData d = get(level);
        Map<Long, Paint[]> m = d.byChunk.get(ck(pos));
        if (m == null) return false;
        Paint[] a = m.get(pos.asLong());
        int f = face.get3DDataValue();
        if (a == null || a[f] == null) return false;
        a[f] = null;
        cleanup(d, m, pos, a);
        d.setDirty();
        broadcast(level, pos, f, "", 0);
        return true;
    }

    public static void clearBlock(ServerLevel level, BlockPos pos) {
        PaintData d = get(level);
        Map<Long, Paint[]> m = d.byChunk.get(ck(pos));
        if (m == null) return;
        Paint[] a = m.get(pos.asLong());
        if (a == null) return;
        for (int f = 0; f < 6; f++) {
            if (a[f] != null) {
                a[f] = null;
                broadcast(level, pos, f, "", 0);
            }
        }
        m.remove(pos.asLong());
        if (m.isEmpty()) d.byChunk.remove(ck(pos));
        d.setDirty();
    }

    private static void cleanup(PaintData d, Map<Long, Paint[]> m, BlockPos pos, Paint[] a) {
        for (Paint p : a) {
            if (p != null) return;
        }
        m.remove(pos.asLong());
        if (m.isEmpty()) d.byChunk.remove(ck(pos));
    }

    private static void broadcast(ServerLevel level, BlockPos pos, int face, String tex, int color) {
        Net.CH.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)),
                new Net.DeltaMsg(pos.asLong(), face, tex, color));
    }

    public static void sendChunk(ServerPlayer sp, ServerLevel level, ChunkPos cp) {
        List<Entry> list = get(level).entriesOf(cp);
        if (list.isEmpty()) return;
        Net.CH.send(PacketDistributor.PLAYER.with(() -> sp), new Net.ChunkMsg(cp.x, cp.z, list));
    }
}
