package fr.uwarp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Config stockee dans config/uwarp.json */
public class Cfg {

    public static class Dest {
        /** COMMAND (execute une commande) ou TELEPORT (va a une position) */
        public String mode = "COMMAND";
        public String command = "";
        public String dim = "minecraft:overworld";
        public double x, y, z;
        public float yaw, pitch;
        public boolean set = false;

        public boolean ready() {
            return "COMMAND".equals(mode) ? !command.isBlank() : set;
        }
    }

    public static class Data {
        public Dest shop = new Dest();
        public Dest world = new Dest();
        public Dest spawn = new Dest();
        /** URL du webhook (Discord, Slack...) qui recoit les idees. */
        public String webhook = "";

        public Data() {
            shop.command = "shop";
            world.mode = "TELEPORT";
            spawn.mode = "TELEPORT";
        }
    }

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson COMPACT = new Gson();
    private static Data data = null;

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("uwarp.json");
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
                read = PRETTY.fromJson(Files.readString(f, StandardCharsets.UTF_8), Data.class);
            } catch (Exception ignored) {
            }
        }
        data = clean(read);
        save();
    }

    public static synchronized void save() {
        try {
            Files.writeString(file(), PRETTY.toJson(get(), Data.class), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static synchronized String toJson() {
        return COMPACT.toJson(get(), Data.class);
    }

    public static String toJson(Data d) {
        return COMPACT.toJson(d, Data.class);
    }

    public static synchronized boolean setFromJson(String json) {
        try {
            Data read = COMPACT.fromJson(json, Data.class);
            if (read == null) {
                return false;
            }
            data = clean(read);
            save();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Lecture cote client. */
    public static Data parse(String json) {
        try {
            return clean(COMPACT.fromJson(json, Data.class));
        } catch (Exception e) {
            return new Data();
        }
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static double finite(double v) {
        return Double.isFinite(v) ? v : 0.0;
    }

    private static Dest cleanDest(Dest in, String defMode) {
        Dest o = new Dest();
        if (in == null) {
            o.mode = defMode;
            return o;
        }
        o.mode = in.mode == null ? defMode : ("TELEPORT".equals(in.mode) ? "TELEPORT" : "COMMAND");
        String cmd = clip(in.command, 200).trim();
        o.command = cmd.startsWith("/") ? cmd.substring(1).trim() : cmd;
        o.dim = clip(in.dim, 100).trim();
        if (ResourceLocation.tryParse(o.dim) == null) {
            o.dim = "minecraft:overworld";
        }
        o.x = finite(in.x);
        o.y = finite(in.y);
        o.z = finite(in.z);
        o.yaw = Float.isFinite(in.yaw) ? in.yaw : 0f;
        o.pitch = Float.isFinite(in.pitch) ? in.pitch : 0f;
        o.set = in.set;
        return o;
    }

    private static Data clean(Data in) {
        Data o = new Data();
        if (in == null) {
            return o;
        }
        o.shop = cleanDest(in.shop, "COMMAND");
        if (in.shop == null) {
            o.shop.command = "shop";
        }
        o.world = cleanDest(in.world, "TELEPORT");
        o.spawn = cleanDest(in.spawn, "TELEPORT");
        String w = clip(in.webhook, 400).trim();
        o.webhook = w.startsWith("https://") ? w : "";
        return o;
    }
}
