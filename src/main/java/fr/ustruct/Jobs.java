package fr.ustruct;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.UUID;

/** File de travaux decoupes sur plusieurs ticks (aucun gel du serveur). */
public class Jobs {
    public abstract static class Job {
        public UUID owner;
        public String label = "Travail";
        public Runnable onDone;

        /** Travaille jusqu'a la limite de temps. Renvoie true quand c'est fini. */
        public abstract boolean step(MinecraftServer server, long deadlineNanos) throws Exception;

        public abstract double progress();

        public void abort() {
        }
    }

    private static final ArrayDeque<Job> QUEUE = new ArrayDeque<>();
    private static Job current;
    private static long lastMsg = 0;

    public static void add(Job j) {
        QUEUE.add(j);
    }

    public static boolean busy() {
        return current != null || !QUEUE.isEmpty();
    }

    public static void tell(MinecraftServer s, UUID who, String msg, boolean actionbar) {
        if (who == null) return;
        ServerPlayer p = s.getPlayerList().getPlayer(who);
        if (p != null) p.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.AQUA), actionbar);
    }

    public static void tick(MinecraftServer server) {
        if (current == null) {
            current = QUEUE.poll();
            if (current == null) return;
        }
        long now = System.nanoTime();
        try {
            boolean done = current.step(server, now + 30_000_000L);
            if (done) {
                Job j = current;
                current = null;
                if (j.onDone != null) j.onDone.run();
                return;
            }
        } catch (Throwable t) {
            t.printStackTrace();
            tell(server, current.owner, current.label + " : ERREUR : " + t.getMessage(), false);
            current.abort();
            current = null;
            return;
        }
        long ms = System.currentTimeMillis();
        if (ms - lastMsg > 1500) {
            lastMsg = ms;
            tell(server, current.owner, current.label + " : " + (int) (current.progress() * 100) + " %", true);
        }
    }

    public static void stop() {
        if (current != null) current.abort();
        current = null;
        QUEUE.clear();
    }
}
