package fr.ucore;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Codes cadeaux a usage unique (recompenses payantes).
 * L'admin cree un code, le vend ; le joueur tape /redeem CODE en jeu.
 * Des que le code est utilise, il est supprime definitivement du serveur (et son fichier est vide puis efface).
 */
public class Gifts {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<UUID, Long> LAST_TRY = new ConcurrentHashMap<>();
    private static final Map<UUID, long[]> FAILS = new ConcurrentHashMap<>(); // [nombre d'echecs, debut de la fenetre]

    private static Path dir() {
        Path p = FMLPaths.CONFIGDIR.get().resolve("ucore_gifts");
        try {
            Files.createDirectories(p);
        } catch (Exception ignored) {
        }
        return p;
    }

    private static String normalize(String code) {
        return code.toUpperCase().replace("-", "").replace(" ", "").trim();
    }

    private static String pretty(String key) {
        return key.substring(0, 4) + "-" + key.substring(4, 8) + "-" + key.substring(8, 12);
    }

    private static String newKey() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private static void log(String line) {
        try {
            Files.writeString(FMLPaths.CONFIGDIR.get().resolve("ucore_gifts_log.txt"),
                    LocalDateTime.now() + "  " + line + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    private static int create(CommandSourceStack src, Cfg.Reward reward) {
        String key;
        synchronized (Gifts.class) {
            do {
                key = newKey();
            } while (Store.get().gifts.containsKey(key));
            Store.Gift g = new Store.Gift();
            g.reward = reward;
            g.created = LocalDateTime.now().toString();
            Store.get().gifts.put(key, g);
            Store.save();
        }
        String code = pretty(key);

        // Petit fichier "cadeau" a envoyer a l'acheteur (le vrai secret reste le code, valable une seule fois).
        String fileInfo = "";
        try {
            String text = "CADEAU\r\n\r\n"
                    + "Ton code : " + code + "\r\n\r\n"
                    + "Pour recevoir ta récompense : connecte-toi au serveur et tape en jeu :\r\n"
                    + "    /redeem " + code + "\r\n\r\n"
                    + "Attention : ce code ne fonctionne qu'une seule fois. Après utilisation, il est\r\n"
                    + "détruit définitivement et ne peut plus servir.\r\n";
            Files.writeString(dir().resolve("cadeau-" + key + ".txt"), text, StandardCharsets.UTF_8);
            fileInfo = " Fichier : config/ucore_gifts/cadeau-" + key + ".txt";
        } catch (Exception ignored) {
        }
        log("CREE " + pretty(key).substring(0, 4) + "-****-**** : " + Rewards.describe(reward));

        final String shown = code;
        final String extra = fileInfo;
        src.sendSuccess(() -> Component.literal("Code cadeau créé : ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(shown).withStyle(s -> s.withColor(ChatFormatting.GOLD).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, shown))))
                .append(Component.literal(" (clique pour copier)." + extra).withStyle(ChatFormatting.GRAY)), true);
        return 1;
    }

    private static void wipeFile(String key) {
        try {
            Path f = dir().resolve("cadeau-" + key + ".txt");
            if (Files.exists(f)) {
                Files.writeString(f, "", StandardCharsets.UTF_8); // on vide le contenu...
                Files.deleteIfExists(f);                          // ...puis on supprime le fichier
            }
        } catch (Exception ignored) {
        }
    }

    private static int redeem(ServerPlayer p, String raw) {
        long now = System.currentTimeMillis();
        Long last = LAST_TRY.get(p.getUUID());
        if (last != null && now - last < 3000L) {
            Rewards.msg(p, "Doucement, réessaie dans quelques secondes.", ChatFormatting.YELLOW);
            return 0;
        }
        LAST_TRY.put(p.getUUID(), now);

        long[] fails = FAILS.computeIfAbsent(p.getUUID(), k -> new long[]{0L, now});
        if (now - fails[1] > 600_000L) {
            fails[0] = 0L;
            fails[1] = now;
        }
        if (fails[0] >= 5L) {
            Rewards.msg(p, "Trop d'essais ratés. Réessaie dans 10 minutes.", ChatFormatting.RED);
            return 0;
        }

        String key = normalize(raw);
        Store.Gift gift;
        synchronized (Gifts.class) {
            gift = Store.get().gifts.remove(key); // retrait atomique : impossible de l'utiliser deux fois
            if (gift != null) {
                Store.save();
            }
        }
        if (gift == null) {
            if (fails[0] == 0L) {
                fails[1] = now;
            }
            fails[0]++;
            Rewards.msg(p, "Code invalide ou déjà utilisé.", ChatFormatting.RED);
            log("ECHEC " + p.getGameProfile().getName());
            return 0;
        }
        fails[0] = 0;

        if (!Rewards.give(p, gift.reward)) {
            synchronized (Gifts.class) {
                Store.get().gifts.put(key, gift); // récompense invalide : on rend le code a l'admin
                Store.save();
            }
            Rewards.msg(p, "La récompense de ce code est invalide : préviens un admin (ton code n'est pas consommé).",
                    ChatFormatting.RED);
            return 0;
        }
        wipeFile(key);
        Rewards.msg(p, "Cadeau reçu, merci ! Ce code est maintenant détruit.", ChatFormatting.GOLD);
        log("UTILISE par " + p.getGameProfile().getName() + " : " + Rewards.describe(gift.reward));
        return 1;
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("redeem")
                .then(Commands.argument("code", StringArgumentType.word())
                        .executes(ctx -> redeem(ctx.getSource().getPlayerOrException(),
                                StringArgumentType.getString(ctx, "code")))));

        d.register(Commands.literal("gift")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.literal("money")
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ctx -> create(ctx.getSource(), Cfg.reward("MONEY",
                                                String.valueOf(LongArgumentType.getLong(ctx, "amount")), 1, "")))))
                        .then(Commands.literal("item")
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                                .executes(ctx -> create(ctx.getSource(), Cfg.reward("ITEM",
                                                        StringArgumentType.getString(ctx, "id"),
                                                        IntegerArgumentType.getInteger(ctx, "count"), ""))))))
                        .then(Commands.literal("pokemon")
                                .then(Commands.argument("spec", StringArgumentType.greedyString())
                                        .executes(ctx -> create(ctx.getSource(), Cfg.reward("POKEMON",
                                                StringArgumentType.getString(ctx, "spec"), 1, "")))))
                        .then(Commands.literal("command")
                                .then(Commands.argument("cmd", StringArgumentType.greedyString())
                                        .executes(ctx -> create(ctx.getSource(), Cfg.reward("COMMAND",
                                                StringArgumentType.getString(ctx, "cmd"), 1, ""))))))
                .then(Commands.literal("list").executes(ctx -> {
                    Map<String, Store.Gift> gifts = Store.get().gifts;
                    int n = 0;
                    for (Map.Entry<String, Store.Gift> e : gifts.entrySet()) {
                        final String line = pretty(e.getKey()) + " -> " + Rewards.describe(e.getValue().reward);
                        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                        n++;
                    }
                    final int total = n;
                    ctx.getSource().sendSuccess(() -> Component.literal(total + " code(s) non utilisé(s)."), false);
                    return 1;
                }))
                .then(Commands.literal("delete")
                        .then(Commands.argument("code", StringArgumentType.word())
                                .executes(ctx -> {
                                    String key = normalize(StringArgumentType.getString(ctx, "code"));
                                    Store.Gift g;
                                    synchronized (Gifts.class) {
                                        g = Store.get().gifts.remove(key);
                                        if (g != null) {
                                            Store.save();
                                        }
                                    }
                                    if (g == null) {
                                        ctx.getSource().sendFailure(Component.literal("Code introuvable."));
                                        return 0;
                                    }
                                    wipeFile(key);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Code supprimé."), true);
                                    return 1;
                                }))));
    }
}
