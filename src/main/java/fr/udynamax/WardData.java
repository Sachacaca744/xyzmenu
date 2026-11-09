package fr.udynamax;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/** Positions des blocs anti-Dynamax d'une dimension. */
public class WardData extends SavedData {
    private final Set<Long> wards = new HashSet<>();

    public static WardData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(WardData::load, WardData::new, "udynamax_wards");
    }

    public static WardData load(CompoundTag tag) {
        WardData d = new WardData();
        for (long l : tag.getLongArray("W")) d.wards.add(l);
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        long[] a = new long[wards.size()];
        int i = 0;
        for (long l : wards) a[i++] = l;
        tag.put("W", new LongArrayTag(a));
        return tag;
    }

    public void add(BlockPos p) {
        if (wards.add(p.asLong())) setDirty();
    }

    public void remove(BlockPos p) {
        if (wards.remove(p.asLong())) setDirty();
    }

    public Set<Long> all() {
        return wards;
    }

    /** Vrai s'il existe un bloc a moins de 50 blocs (carre de 100x100) en X et en Z. */
    public boolean covers(BlockPos p) {
        for (long l : wards) {
            BlockPos w = BlockPos.of(l);
            if (Math.abs(w.getX() - p.getX()) <= 50 && Math.abs(w.getZ() - p.getZ()) <= 50) return true;
        }
        return false;
    }
}
