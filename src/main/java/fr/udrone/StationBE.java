package fr.udrone;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class StationBE extends BlockEntity {
    /** drone -> ordinal du Kind */
    public final Map<UUID, Integer> drones = new LinkedHashMap<>();

    public StationBE(BlockPos p, BlockState s) {
        super(Reg.STATION_BE.get(), p, s);
    }

    public boolean link(UUID id, int kind) {
        if (drones.containsKey(id) || drones.size() >= 6) return false;
        drones.put(id, kind);
        setChanged();
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        ListTag l = new ListTag();
        for (var e : drones.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("U", e.getKey());
            c.putInt("K", e.getValue());
            l.add(c);
        }
        t.put("Drones", l);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        drones.clear();
        ListTag l = t.getList("Drones", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            drones.put(c.getUUID("U"), c.getInt("K"));
        }
    }
}
