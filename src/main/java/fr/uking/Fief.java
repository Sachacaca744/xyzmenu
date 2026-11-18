package fr.uking;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Un fief : joueur (owner != null) ou seigneur PNJ. */
public class Fief {
    public static final String[] BUILDINGS = {"house", "farm", "market", "barracks", "forge", "tower", "wall", "castle"};
    public static final int[] BUILD_COST = {30, 40, 60, 80, 80, 70, 150, 400};
    public static final int[] BUILD_LEVEL = {0, 0, 0, 0, 0, 0, 1, 1};

    public int id;
    public String name = "";
    public String lord = "";
    public UUID owner;
    public int color;
    public BlockPos pos = BlockPos.ZERO;
    public int level;
    public int gold;
    public boolean conquered;
    public long lastIncome;
    public final int[] counts = new int[BUILDINGS.length];
    public final Set<UUID> army = new LinkedHashSet<>();
    public final Set<UUID> peasants = new LinkedHashSet<>();
    public final List<BlockPos> catapults = new ArrayList<>();

    public boolean npc() { return owner == null; }

    public String key() { return owner != null ? owner.toString() : "npc" + id; }

    public int radius() { return 44 + 16 * level; }

    public int armyCap() { return Math.max(6, 8 + 6 * level + 4 * counts[3] + 2 * counts[5]); }

    public int income() {
        return 2 + peasants.size() + counts[0] * 2 + counts[1] * 4 + counts[2] * 8 + level * 3;
    }

    public String levelKey() { return level == 0 ? "hamlet" : level == 1 ? "village" : "castle"; }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putString("name", name);
        t.putString("lord", lord);
        if (owner != null) t.putUUID("owner", owner);
        t.putInt("color", color);
        t.put("pos", NbtUtils.writeBlockPos(pos));
        t.putInt("level", level);
        t.putInt("gold", gold);
        t.putBoolean("conq", conquered);
        t.putLong("last", lastIncome);
        t.putIntArray("counts", counts);
        ListTag a = new ListTag();
        for (UUID u : army) a.add(StringTag.valueOf(u.toString()));
        t.put("army", a);
        ListTag p = new ListTag();
        for (UUID u : peasants) p.add(StringTag.valueOf(u.toString()));
        t.put("peas", p);
        return t;
    }

    public static Fief load(CompoundTag t) {
        Fief f = new Fief();
        f.id = t.getInt("id");
        f.name = t.getString("name");
        f.lord = t.getString("lord");
        if (t.hasUUID("owner")) f.owner = t.getUUID("owner");
        f.color = t.getInt("color");
        f.pos = NbtUtils.readBlockPos(t.getCompound("pos"));
        f.level = t.getInt("level");
        f.gold = t.getInt("gold");
        f.conquered = t.getBoolean("conq");
        f.lastIncome = t.getLong("last");
        int[] c = t.getIntArray("counts");
        for (int i = 0; i < Math.min(c.length, f.counts.length); i++) f.counts[i] = c[i];
        for (Tag s : t.getList("army", Tag.TAG_STRING)) f.army.add(UUID.fromString(s.getAsString()));
        for (Tag s : t.getList("peas", Tag.TAG_STRING)) f.peasants.add(UUID.fromString(s.getAsString()));
        return f;
    }
}
