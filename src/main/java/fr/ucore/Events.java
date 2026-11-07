package fr.ucore;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class Events {
    private static long ticks = 0;
    private static int nextAnnounce = 0;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        Cmds.register(event.getDispatcher());
    }

    /** Ajoute le prefixe du grade devant le nom (chat, messages de mort...). */
    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        String gradeId = Store.get().grade.get(event.getEntity().getUUID().toString());
        if (gradeId == null) {
            return;
        }
        Cfg.Grade g = Cfg.get().grades.get(gradeId);
        if (g == null) {
            return;
        }
        ChatFormatting color = ChatFormatting.getByName(g.color);
        if (color == null) {
            color = ChatFormatting.WHITE;
        }
        event.setDisplayname(Component.literal(g.prefix + " ").withStyle(color).append(event.getDisplayname()));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        MinecraftServer server = p.getServer();
        if (server == null) {
            return;
        }
        Cfg.Config cfg = Cfg.get();
        Store.Data data = Store.get();
        String id = p.getUUID().toString();

        // "Nouveau joueur" = jamais vu par ce mod ET 0 temps de jeu (les anciens joueurs ne sont pas concernes).
        int playTime = p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        boolean first = playTime == 0 && !data.seen.contains(id);
        data.seen.add(id);
        Store.save();

        p.refreshDisplayName();
        String name = p.getGameProfile().getName();

        server.execute(() -> {
            if (first) {
                Store.Spawn fs = Store.get().firstSpawn;
                if (fs.set) {
                    Rewards.teleport(p, fs);
                }
                if (!cfg.firstJoin.isBlank()) {
                    server.getPlayerList().broadcastSystemMessage(
                            Component.literal(cfg.firstJoin.replace("%player%", name)).withStyle(ChatFormatting.LIGHT_PURPLE),
                            false);
                }
            }
            if (!cfg.welcome.isBlank()) {
                p.sendSystemMessage(Component.literal(cfg.welcome.replace("%player%", name))
                        .withStyle(ChatFormatting.AQUA));
            }
        });
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Cfg.Config cfg = Cfg.get();
        if (cfg.announcements.isEmpty()) {
            return;
        }
        ticks++;
        if (ticks >= cfg.announceMinutes * 1200L) {
            ticks = 0;
            String text = cfg.announcements.get(nextAnnounce % cfg.announcements.size());
            nextAnnounce++;
            MinecraftServer server = event.getServer();
            if (server != null) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("[Annonce] ").withStyle(ChatFormatting.GOLD)
                                .append(Component.literal(text).withStyle(ChatFormatting.YELLOW)),
                        false);
            }
        }
    }
}
