package fr.udynamax;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Memorise la taille d'origine des Pokemon dynamaxes (securite en cas de crash). */
public class DynSaved extends SavedData {
    public final Map<UUID, Float> old = new HashMap<>();

    public static DynSaved get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(DynSaved::load, DynSaved::new, "udynamax_state");
    }

    public static DynSaved load(CompoundTag tag) {
        DynSaved d = new DynSaved();
        ListTag l = tag.getList("L", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            d.old.put(t.getUUID("u"), t.getFloat("s"));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag l = new ListTag();
        for (Map.Entry<UUID, Float> e : old.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("u", e.getKey());
            t.putFloat("s", e.getValue());
            l.add(t);
        }
        tag.put("L", l);
        return tag;
    }
}
