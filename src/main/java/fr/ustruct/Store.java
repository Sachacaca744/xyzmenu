package fr.ustruct;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** Dossiers de donnees : <jeu>/ustruct/structures et <jeu>/ustruct/worlds */
public class Store {
    public static final int MAX = 700;
    private static final Pattern OK = Pattern.compile("[A-Za-z0-9_\\-]{1,48}");

    public static Path root() {
        return FMLPaths.GAMEDIR.get().resolve("ustruct");
    }

    public static Path structures() {
        Path p = root().resolve("structures");
        try {
            Files.createDirectories(p);
        } catch (IOException ignored) {
        }
        return p;
    }

    public static Path worlds() {
        Path p = root().resolve("worlds");
        try {
            Files.createDirectories(p);
        } catch (IOException ignored) {
        }
        return p;
    }

    public static boolean validName(String n) {
        return OK.matcher(n).matches();
    }

    public static Path file(String name) {
        return structures().resolve(name + ".ustruct");
    }
}
