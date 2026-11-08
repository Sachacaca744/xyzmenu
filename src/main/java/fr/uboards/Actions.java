package fr.uboards;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.regex.Pattern;

public class Actions {
    private static final int MAX_DOWNLOAD = 12 * 1024 * 1024;
    private static final int MAX_SIDE = 1024;
    private static final int CHUNK = 30000;
    private static final Pattern HASH = Pattern.compile("^[0-9a-f]{40}$");
    private static final Pattern FILE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 _.\\-]{0,100}$");

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private static void msg(ServerPlayer p, String s, ChatFormatting c) {
        p.sendSystemMessage(Component.literal(s).withStyle(c));
    }

    /** Applique le texte / l'image saisi par un OP (depuis le bloc ou la telecommande). */
    public static void edit(ServerPlayer sp, boolean remote, BlockPos clickPos, String text) {
        if (!sp.hasPermissions(2)) {
            msg(sp, "Réservé aux OP.", ChatFormatting.RED);
            return;
        }
        ServerLevel level;
        BlockPos pos;
        if (remote) {
            ItemStack remoteStack = ItemStack.EMPTY;
            for (InteractionHand h : InteractionHand.values()) {
                ItemStack s = sp.getItemInHand(h);
                if (s.getItem() instanceof RemoteItem && RemoteItem.isLinked(s)) {
                    remoteStack = s;
                    break;
                }
            }
            if (remoteStack.isEmpty()) {
                msg(sp, "Tiens ta télécommande reliée en main.", ChatFormatting.RED);
                return;
            }
            CompoundTag t = remoteStack.getTag();
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(t.getString("Dim")));
            level = sp.server.getLevel(key);
            pos = new BlockPos(t.getInt("X"), t.getInt("Y"), t.getInt("Z"));
        } else {
            level = sp.serverLevel();
            pos = clickPos;
            if (sp.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) {
                msg(sp, "Trop loin.", ChatFormatting.RED);
                return;
            }
        }
        if (level == null || !level.isLoaded(pos)) {
            msg(sp, "Le tableau est dans une zone non chargée. Approche-toi de lui.", ChatFormatting.RED);
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BoardBE b)) {
            msg(sp, "Plus de tableau à cet endroit (il a été cassé ?).", ChatFormatting.RED);
            return;
        }
        String t = text == null ? "" : text.trim();
        if (b.isScreen()) {
            b.setText(t.replace("\\n", "\n"));
            msg(sp, t.isEmpty() ? "Écran effacé." : "Texte affiché.", ChatFormatting.GREEN);
        } else {
            setImage(sp, level, pos, t);
        }
    }

    private static void setImage(ServerPlayer sp, ServerLevel level, BlockPos pos, String src) {
        if (src.isEmpty()) {
            if (level.getBlockEntity(pos) instanceof BoardBE b) {
                b.setImg("");
            }
            msg(sp, "Tableau vidé.", ChatFormatting.GREEN);
            return;
        }
        MinecraftServer server = sp.server;
        msg(sp, "Chargement de l'image...", ChatFormatting.GRAY);
        Thread th = new Thread(() -> {
            try {
                byte[] png = loadPng(src);
                String hash = sha1(png);
                Path dir = imagesDir(server);
                Files.createDirectories(dir);
                Files.write(dir.resolve(hash + ".png"), png);
                server.execute(() -> {
                    if (level.getBlockEntity(pos) instanceof BoardBE b && !b.isScreen()) {
                        b.setImg(hash);
                        msg(sp, "Image affichée.", ChatFormatting.GREEN);
                    }
                });
            } catch (Throwable e) {
                String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                server.execute(() -> msg(sp, "Impossible de charger l'image : " + why, ChatFormatting.RED));
            }
        }, "uboards-image");
        th.setDaemon(true);
        th.start();
    }

    private static Path imagesDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("uboards");
    }

    private static byte[] loadPng(String src) throws Exception {
        byte[] raw;
        String low = src.toLowerCase();
        if (low.startsWith("http://") || low.startsWith("https://")) {
            HttpURLConnection c = (HttpURLConnection) new URL(src).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (uboards)");
            if (c.getResponseCode() / 100 != 2) {
                throw new RuntimeException("le site a répondu " + c.getResponseCode());
            }
            try (InputStream in = c.getInputStream()) {
                raw = readLimited(in);
            }
        } else {
            if (!FILE.matcher(src).matches() || src.contains("..")) {
                throw new RuntimeException("nom de fichier invalide (lettres, chiffres, espace, _ . - seulement)");
            }
            Path dir = FMLPaths.GAMEDIR.get().resolve("config").resolve("uboards");
            Path f = dir.resolve(src);
            if (!Files.isRegularFile(f)) {
                f = null;
                for (String ext : new String[]{".png", ".jpg", ".jpeg", ".gif"}) {
                    Path g = dir.resolve(src + ext);
                    if (Files.isRegularFile(g)) {
                        f = g;
                        break;
                    }
                }
            }
            if (f == null) {
                throw new RuntimeException("fichier introuvable dans config/uboards/ du serveur");
            }
            if (Files.size(f) > MAX_DOWNLOAD) {
                throw new RuntimeException("fichier trop gros (12 Mo max)");
            }
            raw = Files.readAllBytes(f);
        }
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(raw));
        if (img == null) {
            throw new RuntimeException("format non reconnu (PNG, JPG ou GIF)");
        }
        int w = img.getWidth(), h = img.getHeight();
        double sc = Math.min(1.0, (double) MAX_SIDE / Math.max(w, h));
        int nw = Math.max(1, (int) Math.round(w * sc)), nh = Math.max(1, (int) Math.round(h * sc));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, nw, nh, null);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(out, "png", bos);
        return bos.toByteArray();
    }

    private static byte[] readLimited(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
            if (bos.size() > MAX_DOWNLOAD) {
                throw new RuntimeException("image trop grosse (12 Mo max)");
            }
        }
        return bos.toByteArray();
    }

    private static String sha1(byte[] b) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte x : MessageDigest.getInstance("SHA-1").digest(b)) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }

    /** Envoie une image enregistree au joueur, en morceaux. */
    public static void sendImage(ServerPlayer sp, String hash) {
        if (hash == null || !HASH.matcher(hash).matches()) {
            return;
        }
        Path f = imagesDir(sp.server).resolve(hash + ".png");
        Thread th = new Thread(() -> {
            try {
                if (!Files.isRegularFile(f) || Files.size(f) > MAX_DOWNLOAD) {
                    return;
                }
                byte[] all = Files.readAllBytes(f);
                int total = (all.length + CHUNK - 1) / CHUNK;
                for (int i = 0; i < total; i++) {
                    int from = i * CHUNK, to = Math.min(all.length, from + CHUNK);
                    byte[] part = java.util.Arrays.copyOfRange(all, from, to);
                    Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new Net.ImgChunkMsg(hash, i, total, part));
                }
            } catch (Throwable ignored) {
            }
        }, "uboards-send");
        th.setDaemon(true);
        th.start();
    }
}
