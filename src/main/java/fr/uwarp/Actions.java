package fr.uwarp;

import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Actions {
    private static final Map<UUID, Long> LAST_IDEA = new ConcurrentHashMap<>();

    public static boolean isOp(ServerPlayer p) {
        return p.createCommandSourceStack().hasPermission(2);
    }

    private static void msg(ServerPlayer p, String text, ChatFormatting color) {
        p.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    private static Cfg.Dest dest(String key) {
        Cfg.Data c = Cfg.get();
        switch (key) {
            case "shop":
                return c.shop;
            case "world":
                return c.world;
            case "spawn":
                return c.spawn;
            default:
                return null;
        }
    }

    private static String niceName(String key) {
        switch (key) {
            case "shop":
                return "Boutique";
            case "world":
                return "World";
            default:
                return "Spawn";
        }
    }

    public static void openAdmin(ServerPlayer p) {
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Net.OpenAdminMsg(Cfg.toJson()));
    }

    /** Memorise la position actuelle de l'admin comme destination. */
    public static void setHere(ServerPlayer p, String key) {
        Cfg.Dest d = dest(key);
        if (d == null) {
            return;
        }
        d.mode = "TELEPORT";
        d.dim = p.level().dimension().location().toString();
        d.x = p.getX();
        d.y = p.getY();
        d.z = p.getZ();
        d.yaw = p.getYRot();
        d.pitch = p.getXRot();
        d.set = true;
        Cfg.save();
        msg(p, niceName(key) + " défini ici : " + Math.round(d.x) + " " + Math.round(d.y) + " "
                + Math.round(d.z) + " (" + d.dim + ")", ChatFormatting.GREEN);
    }

    /** Un joueur clique sur une destination du menu. */
    public static void go(ServerPlayer p, String key) {
        Cfg.Dest d = dest(key);
        if (d == null || !d.ready()) {
            msg(p, "Cette destination n'est pas encore configurée.", ChatFormatting.RED);
            return;
        }
        MinecraftServer server = p.getServer();
        if (server == null) {
            return;
        }
        if ("COMMAND".equals(d.mode)) {
            try {
                server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), d.command);
            } catch (Exception e) {
                msg(p, "La commande de cette destination a échoué.", ChatFormatting.RED);
            }
            return;
        }
        ResourceLocation dimId = ResourceLocation.tryParse(d.dim);
        ServerLevel level = dimId == null ? null
                : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
        if (level == null) {
            msg(p, "Dimension introuvable : " + d.dim, ChatFormatting.RED);
            return;
        }
        p.teleportTo(level, d.x, d.y, d.z, d.yaw, d.pitch);
        msg(p, "Téléportation : " + niceName(key), ChatFormatting.GREEN);
    }

    /** Une idee ecrite par un joueur : journal local + envoi au webhook. */
    public static void idea(ServerPlayer p, String raw) {
        String text = raw == null ? "" : raw.replaceAll("[\\r\\n]+", " ").trim();
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > 300) {
            text = text.substring(0, 300);
        }
        long now = System.currentTimeMillis();
        Long last = LAST_IDEA.get(p.getUUID());
        if (last != null && now - last < 60_000L) {
            msg(p, "Attends une minute avant d'envoyer une autre idée.", ChatFormatting.YELLOW);
            return;
        }
        LAST_IDEA.put(p.getUUID(), now);

        final String playerName = p.getGameProfile().getName();
        final String ideaText = text;
        final String line = "Idée de " + playerName + " : " + ideaText;

        try {
            Files.writeString(FMLPaths.CONFIGDIR.get().resolve("uwarp_ideas.txt"),
                    LocalDateTime.now() + "  " + line + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }

        final String url = Cfg.get().webhook;
        if (url.isEmpty()) {
            msg(p, "Idée enregistrée, merci ! (l'admin la lira dans ses fichiers)", ChatFormatting.GREEN);
            return;
        }

        final MinecraftServer server = p.getServer();
        final UUID uuid = p.getUUID();
        Thread t = new Thread(() -> {
            boolean ok = postWebhook(url, line);
            if (server != null) {
                server.execute(() -> {
                    ServerPlayer target = server.getPlayerList().getPlayer(uuid);
                    if (target != null) {
                        if (ok) {
                            msg(target, "Idée envoyée à l'équipe, merci !", ChatFormatting.GREEN);
                        } else {
                            msg(target, "Idée enregistrée (l'envoi externe a échoué).", ChatFormatting.YELLOW);
                        }
                    }
                });
            }
        }, "uwarp-webhook");
        t.setDaemon(true);
        t.start();
    }

    private static boolean postWebhook(String url, String message) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("content", message);
            body.addProperty("text", message);
            body.addProperty("username", "Idées du serveur");
            JsonObject mentions = new JsonObject();
            mentions.add("parse", new com.google.gson.JsonArray());
            body.add("allowed_mentions", mentions);

            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setRequestMethod("POST");
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            con.setRequestProperty("User-Agent", "uwarp-mod");
            try (OutputStream os = con.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = con.getResponseCode();
            con.disconnect();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        }
    }
}
