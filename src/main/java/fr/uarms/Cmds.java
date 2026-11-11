package fr.uarms;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class Cmds {
    static boolean forcing = false;

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("soldiers").requires(s -> s.hasPermission(2))
                .then(Commands.literal("stop").executes(c -> {
                    var server = c.getSource().getServer();
                    SoldierData.get(server).set(false);
                    int n = SoldierSpawner.sweep(server);
                    c.getSource().sendSuccess(() -> Component.literal(n + " soldat(s) supprimé(s). Ils n'apparaîtront plus (/soldiers start pour réactiver)."), true);
                    return n;
                }))
                .then(Commands.literal("start").executes(c -> {
                    SoldierData.get(c.getSource().getServer()).set(true);
                    c.getSource().sendSuccess(() -> Component.literal("Les soldats peuvent de nouveau apparaître."), true);
                    return 1;
                }))
                .then(Commands.literal("status").executes(c -> {
                    int n = SoldierSpawner.count(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.literal("Apparition : " + (SoldierData.ENABLED ? "ACTIVÉE" : "DÉSACTIVÉE") + " — " + n + " soldat(s) en vie."), false);
                    return n;
                }))
                .then(Commands.literal("spawn")
                        .executes(c -> spawn(c.getSource(), 5 + c.getSource().getLevel().random.nextInt(46)))
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, 50)).executes(c -> spawn(c.getSource(), IntegerArgumentType.getInteger(c, "n"))))));
    }

    private static int spawn(CommandSourceStack src, int n) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!SoldierData.ENABLED) {
            src.sendFailure(Component.literal("Les soldats sont désactivés. /soldiers start pour réactiver."));
            return 0;
        }
        ServerPlayer p = src.getPlayerOrException();
        int ok = SoldierSpawner.spawnGroup((ServerLevel) p.level(), p.blockPosition().offset(12, 0, 0), n);
        src.sendSuccess(() -> Component.literal(ok + " soldat(s) apparu(s) près de toi."), true);
        return ok;
    }
}
