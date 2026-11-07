package fr.ucore;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Cmds {

    private static void ok(CommandSourceStack s, String text) {
        s.sendSuccess(() -> Component.literal(text), true);
    }

    private static void fail(CommandSourceStack s, String text) {
        s.sendFailure(Component.literal(text));
    }

    private static void refreshAll(MinecraftServer server) {
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.refreshDisplayName();
            }
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {

        // ---------- /spawn (tout le monde) ----------
        d.register(Commands.literal("spawn").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            if (Rewards.teleport(p, Store.get().spawn)) {
                Rewards.msg(p, "Téléportation au spawn.", ChatFormatting.GREEN);
            } else {
                Rewards.msg(p, "Le spawn n'est pas encore défini par l'admin.", ChatFormatting.RED);
            }
            return 1;
        }));

        // ---------- /daily ----------
        d.register(Commands.literal("daily").executes(ctx -> {
            Daily.claim(ctx.getSource().getPlayerOrException());
            return 1;
        }));

        // ---------- monnaie ----------
        for (String name : new String[]{"balance", "bal"}) {
            d.register(Commands.literal(name).executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                Rewards.msg(p, "Tu as " + Store.balance(p.getUUID()) + " " + Cfg.get().currency + ".",
                        ChatFormatting.GOLD);
                return 1;
            }));
        }

        d.register(Commands.literal("pay")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(ctx -> {
                                    ServerPlayer from = ctx.getSource().getPlayerOrException();
                                    ServerPlayer to = EntityArgument.getPlayer(ctx, "player");
                                    long amount = LongArgumentType.getLong(ctx, "amount");
                                    String cur = Cfg.get().currency;
                                    if (from.getUUID().equals(to.getUUID())) {
                                        Rewards.msg(from, "Tu ne peux pas te payer toi-même.", ChatFormatting.RED);
                                        return 0;
                                    }
                                    if (Store.balance(from.getUUID()) < amount) {
                                        Rewards.msg(from, "Tu n'as pas assez de " + cur + ".", ChatFormatting.RED);
                                        return 0;
                                    }
                                    Store.addBalance(from.getUUID(), -amount);
                                    Store.addBalance(to.getUUID(), amount);
                                    Rewards.msg(from, "Tu as envoyé " + amount + " " + cur + " à "
                                            + to.getGameProfile().getName() + ".", ChatFormatting.GREEN);
                                    Rewards.msg(to, from.getGameProfile().getName() + " t'a envoyé " + amount
                                            + " " + cur + ".", ChatFormatting.GREEN);
                                    return 1;
                                }))));

        // ---------- /grade (OP) ----------
        d.register(Commands.literal("grade")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("color", StringArgumentType.word())
                                        .then(Commands.argument("prefix", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String id = StringArgumentType.getString(ctx, "id").toLowerCase();
                                                    String color = StringArgumentType.getString(ctx, "color").toLowerCase();
                                                    String prefix = StringArgumentType.getString(ctx, "prefix");
                                                    ChatFormatting cf = ChatFormatting.getByName(color);
                                                    if (cf == null || !cf.isColor()) {
                                                        fail(ctx.getSource(), "Couleur inconnue. Exemples : gold, red, green, aqua, light_purple, blue, yellow, white");
                                                        return 0;
                                                    }
                                                    Cfg.Grade g = new Cfg.Grade();
                                                    g.prefix = prefix.length() > 24 ? prefix.substring(0, 24) : prefix;
                                                    g.color = color;
                                                    Cfg.get().grades.put(id, g);
                                                    Cfg.save();
                                                    refreshAll(ctx.getSource().getServer());
                                                    ok(ctx.getSource(), "Grade '" + id + "' créé : " + g.prefix);
                                                    return 1;
                                                })))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id").toLowerCase();
                                    if (Cfg.get().grades.remove(id) == null) {
                                        fail(ctx.getSource(), "Grade inconnu : " + id);
                                        return 0;
                                    }
                                    Store.get().grade.values().removeIf(v -> v.equals(id));
                                    Cfg.save();
                                    Store.save();
                                    refreshAll(ctx.getSource().getServer());
                                    ok(ctx.getSource(), "Grade '" + id + "' supprimé.");
                                    return 1;
                                })))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
                                            String id = StringArgumentType.getString(ctx, "id").toLowerCase();
                                            if (!Cfg.get().grades.containsKey(id)) {
                                                fail(ctx.getSource(), "Grade inconnu : " + id + " (voir /grade list)");
                                                return 0;
                                            }
                                            Store.get().grade.put(t.getUUID().toString(), id);
                                            Store.save();
                                            t.refreshDisplayName();
                                            ok(ctx.getSource(), t.getGameProfile().getName() + " a maintenant le grade " + id + ".");
                                            return 1;
                                        }))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
                                    Store.get().grade.remove(t.getUUID().toString());
                                    Store.save();
                                    t.refreshDisplayName();
                                    ok(ctx.getSource(), "Grade retiré à " + t.getGameProfile().getName() + ".");
                                    return 1;
                                })))
                .then(Commands.literal("list").executes(ctx -> {
                    for (Map.Entry<String, Cfg.Grade> e : Cfg.get().grades.entrySet()) {
                        ok(ctx.getSource(), e.getKey() + " -> " + e.getValue().prefix + " (" + e.getValue().color + ")");
                    }
                    return 1;
                })));

        // ---------- /ucore (OP) ----------
        d.register(Commands.literal("ucore")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("setspawn")
                        .executes(ctx -> setSpawn(ctx.getSource(), null, false))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> setSpawn(ctx.getSource(), Vec3Argument.getVec3(ctx, "pos"), false))))
                .then(Commands.literal("setfirstspawn")
                        .executes(ctx -> setSpawn(ctx.getSource(), null, true))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> setSpawn(ctx.getSource(), Vec3Argument.getVec3(ctx, "pos"), true))))
                .then(Commands.literal("money")
                        .then(moneyCmd("give"))
                        .then(moneyCmd("take"))
                        .then(moneyCmd("set")))
                .then(Commands.literal("announce")
                        .then(Commands.literal("add")
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            Cfg.get().announcements.add(StringArgumentType.getString(ctx, "text"));
                                            Cfg.save();
                                            ok(ctx.getSource(), "Annonce ajoutée.");
                                            return 1;
                                        })))
                        .then(Commands.literal("list").executes(ctx -> {
                            List<String> list = Cfg.get().announcements;
                            for (int i = 0; i < list.size(); i++) {
                                ok(ctx.getSource(), (i + 1) + ". " + list.get(i));
                            }
                            ok(ctx.getSource(), "Intervalle : " + Cfg.get().announceMinutes + " min");
                            return 1;
                        }))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("n", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            int n = IntegerArgumentType.getInteger(ctx, "n");
                                            List<String> list = Cfg.get().announcements;
                                            if (n > list.size()) {
                                                fail(ctx.getSource(), "Numéro invalide.");
                                                return 0;
                                            }
                                            list.remove(n - 1);
                                            Cfg.save();
                                            ok(ctx.getSource(), "Annonce supprimée.");
                                            return 1;
                                        })))
                        .then(Commands.literal("interval")
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 1440))
                                        .executes(ctx -> {
                                            Cfg.get().announceMinutes = IntegerArgumentType.getInteger(ctx, "minutes");
                                            Cfg.save();
                                            ok(ctx.getSource(), "Une annonce toutes les " + Cfg.get().announceMinutes + " minutes.");
                                            return 1;
                                        }))))
                .then(Commands.literal("daily")
                        .then(Commands.literal("list").executes(ctx -> {
                            List<Cfg.Day> days = Cfg.get().daily;
                            for (int i = 0; i < days.size(); i++) {
                                List<String> parts = new ArrayList<>();
                                for (Cfg.Reward r : days.get(i).rewards) {
                                    parts.add(Rewards.describe(r));
                                }
                                ok(ctx.getSource(), "Jour " + (i + 1) + " : " + (parts.isEmpty() ? "(vide)" : String.join(" + ", parts)));
                            }
                            return 1;
                        }))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("day", IntegerArgumentType.integer(1, 7))
                                        .executes(ctx -> {
                                            int day = IntegerArgumentType.getInteger(ctx, "day");
                                            Cfg.get().daily.get(day - 1).rewards.clear();
                                            Cfg.save();
                                            ok(ctx.getSource(), "Jour " + day + " vidé.");
                                            return 1;
                                        })))
                        .then(Commands.literal("add")
                                .then(Commands.argument("day", IntegerArgumentType.integer(1, 7))
                                        .then(Commands.literal("money")
                                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                        .executes(ctx -> addReward(ctx, Cfg.reward("MONEY",
                                                                String.valueOf(LongArgumentType.getLong(ctx, "amount")), 1, "")))))
                                        .then(Commands.literal("item")
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                                                .executes(ctx -> addReward(ctx, Cfg.reward("ITEM",
                                                                        StringArgumentType.getString(ctx, "id"),
                                                                        IntegerArgumentType.getInteger(ctx, "count"), ""))))))
                                        .then(Commands.literal("pokemon")
                                                .then(Commands.argument("spec", StringArgumentType.greedyString())
                                                        .executes(ctx -> addReward(ctx, Cfg.reward("POKEMON",
                                                                StringArgumentType.getString(ctx, "spec"), 1, "")))))
                                        .then(Commands.literal("command")
                                                .then(Commands.argument("cmd", StringArgumentType.greedyString())
                                                        .executes(ctx -> addReward(ctx, Cfg.reward("COMMAND",
                                                                StringArgumentType.getString(ctx, "cmd"), 1, ""))))))))
                .then(Commands.literal("reload").executes(ctx -> {
                    Cfg.load();
                    ok(ctx.getSource(), "Configuration rechargée.");
                    return 1;
                })));
    }

    private static int addReward(CommandContext<CommandSourceStack> ctx, Cfg.Reward r) {
        int day = IntegerArgumentType.getInteger(ctx, "day");
        Cfg.get().daily.get(day - 1).rewards.add(r);
        Cfg.save();
        ok(ctx.getSource(), "Jour " + day + " : ajout de " + Rewards.describe(r));
        return 1;
    }

    private static int setSpawn(CommandSourceStack src, Vec3 pos, boolean first) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        Vec3 v = pos != null ? pos : src.getPosition();
        Store.Spawn s = new Store.Spawn();
        s.dim = src.getLevel().dimension().location().toString();
        s.x = v.x;
        s.y = v.y;
        s.z = v.z;
        s.yaw = p.getYRot();
        s.pitch = p.getXRot();
        s.set = true;
        if (first) {
            Store.get().firstSpawn = s;
        } else {
            Store.get().spawn = s;
        }
        Store.save();
        ok(src, (first ? "Spawn des nouveaux joueurs" : "/spawn") + " défini : "
                + Math.round(v.x) + " " + Math.round(v.y) + " " + Math.round(v.z) + " (" + s.dim + ")");
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> moneyCmd(String mode) {
        return Commands.literal(mode)
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                .executes(ctx -> {
                                    ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
                                    long amount = LongArgumentType.getLong(ctx, "amount");
                                    switch (mode) {
                                        case "give":
                                            Store.addBalance(t.getUUID(), amount);
                                            break;
                                        case "take":
                                            Store.addBalance(t.getUUID(), -amount);
                                            break;
                                        default:
                                            Store.setBalance(t.getUUID(), amount);
                                            break;
                                    }
                                    ok(ctx.getSource(), t.getGameProfile().getName() + " a maintenant "
                                            + Store.balance(t.getUUID()) + " " + Cfg.get().currency + ".");
                                    return 1;
                                })));
    }
}
