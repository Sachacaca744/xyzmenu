package fr.ugta;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Arrays;

public class Cmds {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        var d = e.getDispatcher();
        d.register(Commands.literal("gta").requires(s -> s.hasPermission(2))
                .then(Commands.literal("stars")
                        .then(Commands.argument("n", IntegerArgumentType.integer(0, 5))
                                .executes(c -> stars(c.getSource(), c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "n")))
                                .then(Commands.argument("joueur", EntityArgument.player())
                                        .executes(c -> stars(c.getSource(), EntityArgument.getPlayer(c, "joueur"), IntegerArgumentType.getInteger(c, "n"))))))
                .then(Commands.literal("vehicle")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        Arrays.stream(Kind.values()).map(k -> k.id), b))
                                .executes(c -> vehicle(c.getSource(), StringArgumentType.getString(c, "type")))))
                .then(Commands.literal("cops")
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, 20))
                                .executes(c -> cops(c.getSource(), IntegerArgumentType.getInteger(c, "n"), false))
                                .then(Commands.literal("army").executes(c -> cops(c.getSource(), IntegerArgumentType.getInteger(c, "n"), true)))))
                .then(Commands.literal("peds")
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, 30))
                                .executes(c -> peds(c.getSource(), IntegerArgumentType.getInteger(c, "n"))))));
    }

    private static int stars(CommandSourceStack s, ServerPlayer p, int n) {
        Wanted.set(p, n);
        s.sendSuccess(() -> Component.literal(p.getName().getString() + " : " + n + " étoile(s)"), true);
        return n;
    }

    private static int vehicle(CommandSourceStack s, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Kind k = Arrays.stream(Kind.values()).filter(x -> x.id.equals(id)).findFirst().orElse(null);
        if (k == null) {
            s.sendFailure(Component.literal("Types : " + String.join(", ", Arrays.stream(Kind.values()).map(x -> x.id).toList())));
            return 0;
        }
        ServerPlayer p = s.getPlayerOrException();
        ServerLevel lv = p.serverLevel();
        Vehicle v = Reg.create(k, lv);
        var f = p.getLookAngle();
        v.moveTo(p.getX() + f.x * 5, p.getY() + (k.cat == 0 ? 0.5 : 1.0), p.getZ() + f.z * 5, p.getYRot(), 0);
        lv.addFreshEntity(v);
        return 1;
    }

    private static int cops(CommandSourceStack s, int n, boolean army) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = s.getPlayerOrException();
        for (int i = 0; i < n; i++) {
            BlockPos b = p.blockPosition().offset(p.getRandom().nextInt(9) - 4, 0, p.getRandom().nextInt(9) - 4);
            Spawner.spawnCop(p.serverLevel(), b, army);
        }
        return n;
    }

    private static int peds(CommandSourceStack s, int n) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = s.getPlayerOrException();
        ServerLevel lv = p.serverLevel();
        for (int i = 0; i < n; i++) {
            Ped ped = Reg.PED.get().create(lv);
            ped.moveTo(p.getX() + p.getRandom().nextInt(9) - 4, p.getY(), p.getZ() + p.getRandom().nextInt(9) - 4, 0, 0);
            lv.addFreshEntity(ped);
        }
        return n;
    }
}
