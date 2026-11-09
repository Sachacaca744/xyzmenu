package fr.udynamax;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class Events {
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.getServer() != null) Dyn.tick(e.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) Dyn.restoreParty(sp);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) Dyn.revertPlayer(sp);
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent e) {
        Dyn.revertAll(e.getServer());
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("dynamax")
                .then(Commands.literal("go").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    String err = Dyn.tryStart(sp);
                    if (err != null) sp.sendSystemMessage(Component.literal(err));
                    return 1;
                }))
                .then(Commands.literal("give").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("band").executes(c -> give(c.getSource(), Reg.BAND.get())))
                        .then(Commands.literal("star").executes(c -> give(c.getSource(), Reg.RED_STAR.get())))
                        .then(Commands.literal("ward").executes(c -> give(c.getSource(), Reg.WARD_ITEM.get()))))
                .then(Commands.literal("wards").requires(s -> s.hasPermission(2)).executes(c -> {
                    var lvl = c.getSource().getLevel();
                    var all = WardData.get(lvl).all();
                    c.getSource().sendSuccess(() -> Component.literal(all.size() + " bloc(s) anti-Dynamax dans cette dimension :"), false);
                    for (long l : all) {
                        BlockPos p = BlockPos.of(l);
                        c.getSource().sendSuccess(() -> Component.literal(" - " + p.getX() + " " + p.getY() + " " + p.getZ()), false);
                    }
                    return all.size();
                }))
                .then(Commands.literal("reset").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("joueur", EntityArgument.player()).executes(c -> {
                            ServerPlayer t = EntityArgument.getPlayer(c, "joueur");
                            Dyn.revertPlayer(t);
                            int n = Dyn.restoreParty(t);
                            c.getSource().sendSuccess(() -> Component.literal("Taille normale rétablie (" + n + " Pokémon corrigé(s))."), true);
                            return 1;
                        }))));
    }

    private static int give(CommandSourceStack src, Item item) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        ItemStack st = new ItemStack(item);
        if (!sp.getInventory().add(st)) sp.drop(st, false);
        return 1;
    }
}
