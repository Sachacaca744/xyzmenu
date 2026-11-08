package fr.uboards;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class Cmds {
    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("uboards")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("give")
                        .then(sized("board", false))
                        .then(sized("screen", true)))
                .then(Commands.literal("remote").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    give(p, new ItemStack(Reg.REMOTE.get()), "Télécommande");
                    return 1;
                })));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> sized(String name, boolean screen) {
        return Commands.literal(name)
                .then(Commands.argument("largeur", IntegerArgumentType.integer(1, BoardBE.MAX_SIZE))
                        .then(Commands.argument("hauteur", IntegerArgumentType.integer(1, BoardBE.MAX_SIZE))
                                .executes(ctx -> {
                                    int w = IntegerArgumentType.getInteger(ctx, "largeur");
                                    int h = IntegerArgumentType.getInteger(ctx, "hauteur");
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    give(p, Reg.make(screen, w, h), (screen ? "Écran blanc " : "Tableau ") + w + "x" + h);
                                    return 1;
                                })));
    }

    private static void give(ServerPlayer p, ItemStack stack, String label) {
        if (!p.getInventory().add(stack) && !stack.isEmpty()) {
            p.drop(stack, false);
        }
        p.sendSystemMessage(Component.literal("Tu as reçu : " + label).withStyle(ChatFormatting.GREEN));
    }
}
