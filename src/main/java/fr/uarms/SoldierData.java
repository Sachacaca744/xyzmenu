package fr.uarms;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Etat "les soldats ont le droit d'apparaitre" (sauvegarde dans le monde). */
public class SoldierData extends SavedData {
    public boolean enabled = true;
    public static volatile boolean ENABLED = true;

    public static SoldierData get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(SoldierData::load, SoldierData::new, "uarms_soldiers");
    }

    public static SoldierData load(CompoundTag t) {
        SoldierData d = new SoldierData();
        d.enabled = !t.contains("enabled") || t.getBoolean("enabled");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        t.putBoolean("enabled", enabled);
        return t;
    }

    public void set(boolean v) {
        enabled = v;
        ENABLED = v;
        setDirty();
    }
}
