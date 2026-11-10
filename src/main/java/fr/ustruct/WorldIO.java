package fr.ustruct;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Sauvegarde de tout le monde en .zip, et installation automatique au demarrage. */
public class WorldIO {

    public static void export(MinecraftServer server, String name, UUID owner) {
        server.saveEverything(true, true, true);
        for (ServerLevel l : server.getAllLevels()) l.noSave = true;
        final Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        final Path out = Store.worlds().resolve(name + ".zip");
        final Path part = Store.worlds().resolve(name + ".zip.part");
        Thread t = new Thread(() -> {
            String result;
            try {
                List<Path> files;
                try (Stream<Path> s = Files.walk(root)) {
                    files = s.filter(Files::isRegularFile).collect(Collectors.toList());
                }
                try (ZipOutputStream z = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(part.toFile()), 1 << 16))) {
                    z.setLevel(6);
                    byte[] buf = new byte[1 << 16];
                    for (Path f : files) {
                        String rel = root.relativize(f.toAbsolutePath().normalize()).toString().replace('\\', '/');
                        if (rel.equals("session.lock")) continue;
                        z.putNextEntry(new ZipEntry(rel));
                        try (InputStream in = new FileInputStream(f.toFile())) {
                            int n;
                            while ((n = in.read(buf)) > 0) z.write(buf, 0, n);
                        } catch (IOException ex) {
                            // fichier verrouille : on l'ignore
                        }
                        z.closeEntry();
                    }
                }
                Files.move(part, out, StandardCopyOption.REPLACE_EXISTING);
                result = "Monde sauvegardé : " + out.toAbsolutePath() + " (" + (out.toFile().length() / (1024 * 1024)) + " Mo). "
                        + "Mets ce fichier dans <jeu>/ustruct/worlds/ d'un autre modpack avec le mod : le monde s'installera au lancement.";
            } catch (Throwable ex) {
                ex.printStackTrace();
                result = "ERREUR sauvegarde du monde : " + ex;
            }
            final String msg = result;
            server.execute(() -> {
                for (ServerLevel l : server.getAllLevels()) l.noSave = false;
                Jobs.tell(server, owner, msg, false);
                server.getPlayerList().broadcastSystemMessage(Component.literal("[ustruct] " + msg).withStyle(ChatFormatting.GRAY), false);
            });
        }, "ustruct-world-export");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ installation

    public static void installOnStartup() throws IOException {
        Path wd = Store.worlds();
        List<Path> zips;
        try (Stream<Path> s = Files.list(wd)) {
            zips = s.filter(p -> p.toString().endsWith(".zip")).collect(Collectors.toList());
        }
        if (zips.isEmpty()) return;
        Path game = FMLPaths.GAMEDIR.get();
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            String level = "world";
            Path props = game.resolve("server.properties");
            if (Files.exists(props)) {
                for (String line : Files.readAllLines(props, StandardCharsets.UTF_8)) {
                    if (line.startsWith("level-name=")) level = line.substring(11).trim();
                }
            }
            Path target = game.resolve(level);
            if (Files.exists(target.resolve("level.dat"))) return;
            Path newest = zips.stream().max(Comparator.comparingLong(p -> p.toFile().lastModified())).get();
            System.out.println("[ustruct] Installation du monde " + newest.getFileName() + " dans " + target);
            unzip(newest, target);
        } else {
            Path saves = game.resolve("saves");
            Files.createDirectories(saves);
            for (Path z : zips) {
                String n = z.getFileName().toString();
                n = n.substring(0, n.length() - 4);
                Path target = saves.resolve(n);
                if (Files.exists(target)) continue;
                System.out.println("[ustruct] Installation du monde " + n + " dans " + target);
                unzip(z, target);
            }
        }
    }

    private static void unzip(Path zip, Path target) throws IOException {
        Files.createDirectories(target);
        Path base = target.toAbsolutePath().normalize();
        try (ZipInputStream in = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip.toFile()), 1 << 16))) {
            ZipEntry e;
            byte[] buf = new byte[1 << 16];
            while ((e = in.getNextEntry()) != null) {
                Path p = base.resolve(e.getName()).normalize();
                if (!p.startsWith(base)) continue; // protection zip-slip
                if (e.isDirectory()) {
                    Files.createDirectories(p);
                    continue;
                }
                Files.createDirectories(p.getParent());
                try (OutputStream o = new BufferedOutputStream(new FileOutputStream(p.toFile()), 1 << 16)) {
                    int n;
                    while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                }
            }
        }
    }
}
