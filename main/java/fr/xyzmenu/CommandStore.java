package fr.xyzmenu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Stocke les raccourcis dans config/xyzmenu.json */
public class CommandStore {
    public static class Entry {
        public String name;
        public String command;

        public Entry(String name, String command) {
            this.name = name;
            this.command = command;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Entry>>() {}.getType();

    public static final List<Entry> entries = new ArrayList<>();

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("xyzmenu.json");
    }

    public static void load() {
        entries.clear();
        Path f = file();
        if (Files.exists(f)) {
            try {
                List<Entry> read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), LIST_TYPE);
                if (read != null) {
                    for (Entry e : read) {
                        entries.add(new Entry(e.name == null ? "" : e.name, e.command == null ? "" : e.command));
                    }
                    return;
                }
            } catch (Exception ignored) {
                // fichier invalide : on repart sur les valeurs par défaut
            }
        }
        entries.add(new Entry("Spawn", "/spawn"));
        entries.add(new Entry("Shop", "/shop"));
        entries.add(new Entry("Home", "/home"));
        save();
    }

    public static void save() {
        try {
            Files.writeString(file(), GSON.toJson(entries, LIST_TYPE), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
