package fr.utaxi;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/** /taxi add|remove|list|give|reload (OP). */
public class Cmds {
    private static List<String> names() {
        List<String> l = new ArrayList<>();
        for (TaxiCfg.Dest d : TaxiCfg.get().destinations) l.add(d.name);
        return l;
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("taxi")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("add")
                        .then(Commands.argument("nom", StringArgumentType.string()).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            String name = StringArgumentType.getString(ctx, "nom").trim();
                            if (name.isEmpty() || name.length() > 40) {
                                ctx.getSource().sendFailure(Component.literal("Nom invalide (1 à 40 caractères)."));
                                return 0;
                            }
                            TaxiCfg.Dest d = TaxiCfg.find(name);
                            boolean update = d != null;
                            if (d == null) {
                                d = new TaxiCfg.Dest();
                                d.name = name;
                                TaxiCfg.get().destinations.add(d);
                            }
                            d.dim = p.level().dimension().location().toString();
                            d.x = p.getX();
                            d.y = p.getY();
                            d.z = p.getZ();
                            d.yaw = p.getYRot();
                            d.pitch = p.getXRot();
                            TaxiCfg.save();
                            final String msg = (update ? "Destination mise à jour : " : "Destination ajoutée : ") + name;
                            ctx.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GREEN), true);
                            return 1;
                        })))
                .then(Commands.literal("remove")
                        .then(Commands.argument("nom", StringArgumentType.string())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(names().stream().map(n -> "\"" + n + "\""), b))
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "nom");
                                    TaxiCfg.Dest d = TaxiCfg.find(name);
                                    if (d == null) {
                                        ctx.getSource().sendFailure(Component.literal("Destination inconnue : " + name));
                                        return 0;
                                    }
                                    TaxiCfg.get().destinations.remove(d);
                                    TaxiCfg.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Destination supprimée : " + name)
                                            .withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                })))
                .then(Commands.literal("list").executes(ctx -> {
                    CommandSourceStack s = ctx.getSource();
                    List<TaxiCfg.Dest> l = TaxiCfg.get().destinations;
                    if (l.isEmpty()) {
                        s.sendSuccess(() -> Component.literal("Aucune destination. Utilise /taxi add \"Nom\" là où tu te trouves."), false);
                    }
                    for (TaxiCfg.Dest d : l) {
                        final String line = d.name + " -> " + d.dim + " " + (int) d.x + " " + (int) d.y + " " + (int) d.z;
                        s.sendSuccess(() -> Component.literal(line), false);
                    }
                    return l.size();
                }))
                .then(Commands.literal("reload").executes(ctx -> {
                    TaxiCfg.load();
                    ctx.getSource().sendSuccess(() -> Component.literal("Config rechargée : "
                            + TaxiCfg.get().destinations.size() + " destination(s)."), true);
                    return 1;
                }))
                .then(Commands.literal("give").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    ItemStack s = new ItemStack(Reg.BOOTH_ITEM.get());
                    if (!p.getInventory().add(s) && !s.isEmpty()) p.drop(s, false);
                    return 1;
                })));
    }
}
