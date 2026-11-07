package fr.ucore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Donnees des joueurs : config/ucore_data.json (spawns, monnaie, grades, serie /daily). */
public class Store {

    public static class Spawn {
        public String dim = "minecraft:overworld";
        public double x, y, z;
        public float yaw, pitch;
        public boolean set = false;
    }

    public static class Streak {
        public long lastDay = -1;
        public int streak = 0;
    }

    public static class Data {
        public Spawn firstSpawn = new Spawn();
        public Spawn spawn = new Spawn();
        public Map<String, Long> money = new HashMap<>();
        public Map<String, String> grade = new HashMap<>();
        public Map<String, Streak> daily = new HashMap<>();
        public Set<String> seen = new HashSet<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = null;

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("ucore_data.json");
    }

    public static synchronized Data get() {
        if (data == null) {
            load();
        }
        return data;
    }

    public static synchronized void load() {
        Data read = null;
        Path f = file();
        if (Files.exists(f)) {
            try {
                read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Data.class);
            } catch (Exception ignored) {
            }
        }
        if (read == null) {
            read = new Data();
        }
        if (read.firstSpawn == null) read.firstSpawn = new Spawn();
        if (read.spawn == null) read.spawn = new Spawn();
        if (read.money == null) read.money = new HashMap<>();
        if (read.grade == null) read.grade = new HashMap<>();
        if (read.daily == null) read.daily = new HashMap<>();
        if (read.seen == null) read.seen = new HashSet<>();
        data = read;
    }

    public static synchronized void save() {
        try {
            Files.writeString(file(), GSON.toJson(get(), Data.class), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static synchronized long balance(UUID id) {
        return get().money.getOrDefault(id.toString(), 0L);
    }

    public static synchronized void setBalance(UUID id, long amount) {
        get().money.put(id.toString(), Math.max(0L, amount));
        save();
    }

    public static synchronized void addBalance(UUID id, long delta) {
        setBalance(id, balance(id) + delta);
    }
}
