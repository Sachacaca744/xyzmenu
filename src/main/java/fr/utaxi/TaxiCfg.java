package fr.utaxi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Destinations du taxi : config/utaxi.json (la meme pour toutes les cabines). */
public class TaxiCfg {
    public static class Dest {
        public String name = "";
        public String dim = "minecraft:overworld";
        public double x, y, z;
        public float yaw, pitch;
    }

    public List<Dest> destinations = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static TaxiCfg cur = new TaxiCfg();

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("utaxi.json");
    }

    public static synchronized TaxiCfg get() {
        return cur;
    }

    public static synchronized void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                TaxiCfg c = GSON.fromJson(Files.readString(f), TaxiCfg.class);
                cur = c == null ? new TaxiCfg() : c;
                if (cur.destinations == null) cur.destinations = new ArrayList<>();
            } else {
                cur = new TaxiCfg();
                save();
            }
        } catch (Exception e) {
            cur = new TaxiCfg();
        }
    }

    public static synchronized void save() {
        try {
            Files.writeString(file(), GSON.toJson(cur));
        } catch (Exception ignored) {
        }
    }

    public static synchronized Dest find(String name) {
        for (Dest d : cur.destinations) {
            if (d.name.equalsIgnoreCase(name)) return d;
        }
        return null;
    }
}
