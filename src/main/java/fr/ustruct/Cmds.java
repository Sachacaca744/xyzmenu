package fr.ustruct;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Cmds {
    private static void say(CommandContext<CommandSourceStack> c, String s) {
        c.getSource().sendSuccess(() -> Component.literal(s), false);
    }

    private static void err(CommandContext<CommandSourceStack> c, String s) {
        c.getSource().sendFailure(Component.literal(s).withStyle(ChatFormatting.RED));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> nameArg() {
        return Commands.argument("nom", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(listNames(), b));
    }

    private static List<String> listNames() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(Store.structures())) {
            s.forEach(p -> {
                String f = p.getFileName().toString();
                if (f.endsWith(".ustruct") && !f.startsWith("__") && !f.startsWith(".")) out.add(f.substring(0, f.length() - 8));
            });
        } catch (IOException ignored) {
        }
        java.util.Collections.sort(out);
        return out;
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ustruct").requires(s -> s.hasPermission(2));

        root.then(Commands.literal("wand").executes(c -> give(c, Reg.WAND.get())));
        root.then(Commands.literal("tablet").executes(c -> give(c, Reg.TABLET.get())));

        root.then(Commands.literal("pos1").executes(c -> {
            Sel.set(c.getSource().getPlayerOrException(), true, c.getSource().getPlayerOrException().blockPosition());
            return 1;
        }).then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> {
            Sel.set(c.getSource().getPlayerOrException(), true, BlockPosArgument.getLoadedBlockPos(c, "pos"));
            return 1;
        })));
        root.then(Commands.literal("pos2").executes(c -> {
            Sel.set(c.getSource().getPlayerOrException(), false, c.getSource().getPlayerOrException().blockPosition());
            return 1;
        }).then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> {
            Sel.set(c.getSource().getPlayerOrException(), false, BlockPosArgument.getLoadedBlockPos(c, "pos"));
            return 1;
        })));

        root.then(Commands.literal("save").then(nameArg()
                .executes(c -> save(c, false))
                .then(Commands.literal("entities").executes(c -> save(c, true)))
                .then(Commands.literal("noentities").executes(c -> save(c, false)))));

        root.then(Commands.literal("paste").then(nameArg()
                .executes(c -> paste(c, c.getSource().getPlayerOrException().blockPosition(), 0, 0, false))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(c -> paste(c, BlockPosArgument.getLoadedBlockPos(c, "pos"), 0, 0, false))
                        .then(Commands.argument("rotation", IntegerArgumentType.integer(0, 270))
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"0", "90", "180", "270"}, b))
                                .executes(c -> paste(c, BlockPosArgument.getLoadedBlockPos(c, "pos"), IntegerArgumentType.getInteger(c, "rotation"), 0, false))
                                .then(Commands.argument("miroir", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"aucun", "x", "z"}, b))
                                        .executes(c -> paste(c, BlockPosArgument.getLoadedBlockPos(c, "pos"), IntegerArgumentType.getInteger(c, "rotation"), mir(c), false))
                                        .then(Commands.argument("air", BoolArgumentType.bool())
                                                .executes(c -> paste(c, BlockPosArgument.getLoadedBlockPos(c, "pos"), IntegerArgumentType.getInteger(c, "rotation"), mir(c), BoolArgumentType.getBool(c, "air")))))))));

        root.then(Commands.literal("undo").executes(Cmds::undo));
        root.then(Commands.literal("list").executes(c -> {
            List<String> n = listNames();
            say(c, n.isEmpty() ? "Aucune structure sauvegardée." : n.size() + " structure(s) : " + String.join(", ", n));
            say(c, "Dossier : " + Store.structures().toAbsolutePath());
            return n.size();
        }));
        root.then(Commands.literal("info").then(nameArg().executes(c -> {
            String n = StringArgumentType.getString(c, "nom");
            if (!Files.exists(Store.file(n))) {
                err(c, "Structure introuvable.");
                return 0;
            }
            try {
                for (String s : PasteJob.info(n)) say(c, " - " + s);
            } catch (IOException ex) {
                err(c, "Lecture impossible : " + ex.getMessage());
            }
            return 1;
        })));
        root.then(Commands.literal("delete").then(nameArg().executes(c -> {
            String n = StringArgumentType.getString(c, "nom");
            try {
                if (Store.validName(n) && Files.deleteIfExists(Store.file(n))) {
                    say(c, "Supprimée.");
                    return 1;
                }
            } catch (IOException ignored) {
            }
            err(c, "Structure introuvable.");
            return 0;
        })));

        root.then(Commands.literal("world")
                .then(Commands.literal("export").then(Commands.argument("nom", StringArgumentType.word()).executes(c -> {
                    String n = StringArgumentType.getString(c, "nom");
                    if (!Store.validName(n)) {
                        err(c, "Nom invalide (lettres, chiffres, - et _).");
                        return 0;
                    }
                    WorldIO.export(c.getSource().getServer(), n, c.getSource().getEntity() instanceof ServerPlayer p ? p.getUUID() : null);
                    say(c, "Sauvegarde du monde lancée...");
                    return 1;
                })))
                .then(Commands.literal("list").executes(c -> {
                    try (Stream<Path> s = Files.list(Store.worlds())) {
                        List<String> l = new ArrayList<>();
                        s.forEach(p -> {
                            if (p.toString().endsWith(".zip")) l.add(p.getFileName() + " (" + (p.toFile().length() / (1024 * 1024)) + " Mo)");
                        });
                        say(c, l.isEmpty() ? "Aucun monde sauvegardé." : String.join(", ", l));
                    } catch (IOException ignored) {
                    }
                    say(c, "Dossier : " + Store.worlds().toAbsolutePath());
                    return 1;
                })));
        d.register(root);
    }

    private static int mir(CommandContext<CommandSourceStack> c) {
        String m = StringArgumentType.getString(c, "miroir").toLowerCase();
        return m.equals("x") ? 1 : m.equals("z") ? 2 : 0;
    }

    private static int give(CommandContext<CommandSourceStack> c, Item item) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ItemStack st = new ItemStack(item);
        if (!sp.getInventory().add(st)) sp.drop(st, false);
        return 1;
    }

    private static int save(CommandContext<CommandSourceStack> c, boolean ent) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "nom");
        if (!Store.validName(name) || name.startsWith("__")) {
            err(c, "Nom invalide (lettres, chiffres, - et _ ; 48 max).");
            return 0;
        }
        Sel s = Sel.of(sp);
        if (!s.complete()) {
            err(c, "Sélectionne d'abord 2 coins (baguette ou /ustruct pos1 / pos2).");
            return 0;
        }
        if (!s.dim.equals(sp.level().dimension().location().toString())) {
            err(c, "La sélection est dans une autre dimension.");
            return 0;
        }
        int[] z = s.size();
        if (z[0] > Store.MAX || z[1] > Store.MAX || z[2] > Store.MAX) {
            err(c, "Zone trop grande : " + z[0] + "x" + z[1] + "x" + z[2] + " (maximum " + Store.MAX + " par côté).");
            return 0;
        }
        SaveJob j = new SaveJob((ServerLevel) sp.level(), s.min(), z[0], z[1], z[2], name, ent);
        j.owner = sp.getUUID();
        var server = c.getSource().getServer();
        j.onDone = () -> Jobs.tell(server, sp.getUUID(), "Structure '" + name + "' sauvegardée (" + z[0] + "x" + z[1] + "x" + z[2] + ").", false);
        Jobs.add(j);
        say(c, "Sauvegarde de " + z[0] + "x" + z[1] + "x" + z[2] + " blocs lancée" + (Jobs.busy() ? " (en file d'attente possible)" : "") + "...");
        return 1;
    }

    private static int paste(CommandContext<CommandSourceStack> c, BlockPos pos, int rot, int mir, boolean air) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "nom");
        if (!Store.validName(name) || !Files.exists(Store.file(name))) {
            err(c, "Structure introuvable. /ustruct list");
            return 0;
        }
        if (rot % 90 != 0) {
            err(c, "Rotation : 0, 90, 180 ou 270.");
            return 0;
        }
        ServerLevel level = (ServerLevel) sp.level();
        try {
            int[] sz = PasteJob.size(name);
            int[] r = PasteJob.rotated(sz[0], sz[1], sz[2], rot);
            // sauvegarde de l'ancien terrain pour /ustruct undo
            String undo = "__undo_" + sp.getUUID().toString().substring(0, 8);
            SaveJob backup = new SaveJob(level, pos, r[0], r[1], r[2], undo, true);
            backup.owner = sp.getUUID();
            backup.label = "Copie de sécurité (undo)";
            PasteJob job = new PasteJob(level, name, pos, rot, mir, air);
            job.owner = sp.getUUID();
            var server = c.getSource().getServer();
            job.onDone = () -> Jobs.tell(server, sp.getUUID(), "Structure '" + name + "' placée. (/ustruct undo pour annuler)", false);
            Jobs.add(backup);
            Jobs.add(job);
        } catch (IOException ex) {
            err(c, "Lecture impossible : " + ex.getMessage());
            return 0;
        }
        say(c, "Placement lancé...");
        return 1;
    }

    private static int undo(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String undo = "__undo_" + sp.getUUID().toString().substring(0, 8);
        if (!Files.exists(Store.file(undo))) {
            err(c, "Rien à annuler.");
            return 0;
        }
        try {
            Object[] o = PasteJob.origin(undo);
            BlockPos pos = (BlockPos) o[1];
            ServerLevel level = null;
            for (ServerLevel l : c.getSource().getServer().getAllLevels()) {
                if (l.dimension().location().toString().equals(o[0])) level = l;
            }
            if (level == null) {
                err(c, "Dimension introuvable.");
                return 0;
            }
            PasteJob job = new PasteJob(level, undo, pos, 0, 0, true);
            job.owner = sp.getUUID();
            job.label = "Annulation";
            var server = c.getSource().getServer();
            job.onDone = () -> Jobs.tell(server, sp.getUUID(), "Annulé.", false);
            Jobs.add(job);
        } catch (IOException ex) {
            err(c, "Lecture impossible : " + ex.getMessage());
            return 0;
        }
        return 1;
    }
}
