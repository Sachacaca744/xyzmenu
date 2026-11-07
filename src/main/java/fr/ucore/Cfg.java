package fr.ucore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.ChatFormatting;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reglages : config/ucore.json (modifiable a la main ou avec /ucore). */
public class Cfg {

    public static class Reward {
        /** ITEM, POKEMON, COMMAND ou MONEY */
        public String type = "ITEM";
        public String label = "";
        /** ITEM : id (+ NBT) / POKEMON : espece + options / COMMAND : commande / MONEY : montant */
        public String value = "";
        public int count = 1;
    }

    public static class Grade {
        public String prefix = "[VIP]";
        public String color = "gold";
    }

    public static class Day {
        public List<Reward> rewards = new ArrayList<>();
    }

    public static class Config {
        public String timezone = "Europe/Paris";
        public String currency = "pièces";
        public String welcome = "Bienvenue %player% sur le serveur !";
        public String firstJoin = "%player% vient de rejoindre le serveur pour la première fois, bienvenue !";
        public int announceMinutes = 10;
        public List<String> announcements = new ArrayList<>();
        public Map<String, Grade> grades = new LinkedHashMap<>();
        public List<Day> daily = new ArrayList<>();

        public Config() {
            announcements.add("Bienvenue sur le serveur !");
            announcements.add("N'oublie pas ta récompense quotidienne : tape /daily");
            announcements.add("Un souci ? Demande de l'aide au staff.");

            grades.put("vip", grade("[VIP]", "gold"));
            grades.put("modo", grade("[Modo]", "green"));
            grades.put("admin", grade("[Admin]", "red"));

            daily.add(day(reward("MONEY", "50", 1, "")));
            daily.add(day(reward("ITEM", "cobblemon:poke_ball", 5, "")));
            daily.add(day(reward("MONEY", "100", 1, "")));
            daily.add(day(reward("ITEM", "cobblemon:great_ball", 5, "")));
            daily.add(day(reward("MONEY", "150", 1, "")));
            daily.add(day(reward("ITEM", "cobblemon:revive", 3, "")));
            daily.add(day(reward("MONEY", "500", 1, ""),
                    reward("ITEM", "cobblemon:ultra_ball", 10, ""),
                    reward("ITEM", "cobblemon:max_revive", 3, "")));
        }
    }

    public static Reward reward(String type, String value, int count, String label) {
        Reward r = new Reward();
        r.type = type;
        r.value = value;
        r.count = count;
        r.label = label;
        return r;
    }

    private static Grade grade(String prefix, String color) {
        Grade g = new Grade();
        g.prefix = prefix;
        g.color = color;
        return g;
    }

    private static Day day(Reward... rewards) {
        Day d = new Day();
        for (Reward r : rewards) {
            d.rewards.add(r);
        }
        return d;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config cfg = null;

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("ucore.json");
    }

    public static synchronized Config get() {
        if (cfg == null) {
            load();
        }
        return cfg;
    }

    public static synchronized void load() {
        Config read = null;
        Path f = file();
        if (Files.exists(f)) {
            try {
                read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Config.class);
            } catch (Exception ignored) {
            }
        }
        cfg = clean(read);
        save();
    }

    public static synchronized void save() {
        try {
            Files.writeString(file(), GSON.toJson(get(), Config.class), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static Config clean(Config in) {
        if (in == null) {
            return new Config();
        }
        in.timezone = clip(in.timezone, 60).isBlank() ? "Europe/Paris" : in.timezone.trim();
        in.currency = clip(in.currency, 20).isBlank() ? "pièces" : in.currency;
        in.welcome = clip(in.welcome, 200);
        in.firstJoin = clip(in.firstJoin, 200);
        in.announceMinutes = Math.max(1, Math.min(in.announceMinutes, 1440));

        List<String> ann = new ArrayList<>();
        if (in.announcements != null) {
            for (String a : in.announcements) {
                if (a != null && !a.isBlank() && ann.size() < 50) {
                    ann.add(clip(a, 200));
                }
            }
        }
        in.announcements = ann;

        Map<String, Grade> grades = new LinkedHashMap<>();
        if (in.grades != null) {
            for (Map.Entry<String, Grade> e : in.grades.entrySet()) {
                Grade g = e.getValue();
                if (e.getKey() == null || e.getKey().isBlank() || g == null) {
                    continue;
                }
                Grade n = new Grade();
                n.prefix = clip(g.prefix, 24);
                ChatFormatting cf = g.color == null ? null : ChatFormatting.getByName(g.color);
                n.color = (cf != null && cf.isColor()) ? g.color.toLowerCase() : "white";
                grades.put(e.getKey().toLowerCase(), n);
            }
        }
        in.grades = grades;

        List<Day> days = new ArrayList<>();
        if (in.daily != null) {
            for (Day d : in.daily) {
                Day nd = new Day();
                if (d != null && d.rewards != null) {
                    for (Reward r : d.rewards) {
                        if (r == null || nd.rewards.size() >= 30) {
                            continue;
                        }
                        Reward nr = new Reward();
                        String t = r.type == null ? "ITEM" : r.type.trim().toUpperCase();
                        nr.type = (t.equals("POKEMON") || t.equals("COMMAND") || t.equals("MONEY")) ? t : "ITEM";
                        nr.label = clip(r.label, 48);
                        nr.value = clip(r.value, 400);
                        nr.count = Math.max(1, Math.min(r.count, 64));
                        nd.rewards.add(nr);
                    }
                }
                days.add(nd);
            }
        }
        while (days.size() < 7) {
            days.add(new Day());
        }
        while (days.size() > 7) {
            days.remove(days.size() - 1);
        }
        in.daily = days;
        return in;
    }
}
