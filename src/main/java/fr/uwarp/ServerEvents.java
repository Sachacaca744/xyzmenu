package fr.uwarp;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

public class ServerEvents {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        // /warp : pour tout le monde
        d.register(Commands.literal("warp").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            Cfg.Data c = Cfg.get();
            Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                    new Net.OpenWarpMsg(c.shop.ready(), c.world.ready(), c.spawn.ready()));
            return 1;
        }));

        // /admin : OP uniquement. /admin = interface, /admin setspawn et /admin setworld = raccourcis
        d.register(Commands.literal("admin")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    Actions.openAdmin(ctx.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("setspawn").executes(ctx -> {
                    Actions.setHere(ctx.getSource().getPlayerOrException(), "spawn");
                    return 1;
                }))
                .then(Commands.literal("setworld").executes(ctx -> {
                    Actions.setHere(ctx.getSource().getPlayerOrException(), "world");
                    return 1;
                })));
    }
}
