package fr.ucore;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.time.LocalDate;
import java.time.ZoneId;

public class Daily {

    private static ZoneId zone() {
        try {
            return ZoneId.of(Cfg.get().timezone);
        } catch (Exception e) {
            return ZoneId.of("Europe/Paris");
        }
    }

    public static void claim(ServerPlayer p) {
        Cfg.Config c = Cfg.get();
        Store.Data d = Store.get();
        long today = LocalDate.now(zone()).toEpochDay();

        Store.Streak s = d.daily.computeIfAbsent(p.getUUID().toString(), k -> new Store.Streak());
        if (s.lastDay == today) {
            Rewards.msg(p, "Tu as déjà récupéré ta récompense aujourd'hui (série : "
                    + s.streak + " jour(s)). Reviens demain !", ChatFormatting.YELLOW);
            return;
        }

        s.streak = (s.lastDay == today - 1) ? s.streak + 1 : 1;
        s.lastDay = today;
        Store.save();

        int dayIdx = (s.streak - 1) % 7;
        boolean big = dayIdx == 6;
        Rewards.msg(p, (big ? "★ GROS CADEAU ★ " : "") + "Récompense quotidienne - jour "
                + (dayIdx + 1) + "/7  (série : " + s.streak + " jour(s))", ChatFormatting.GOLD);

        Cfg.Day day = c.daily.get(dayIdx);
        if (day.rewards.isEmpty()) {
            Rewards.msg(p, "Pas de récompense configurée pour ce jour.", ChatFormatting.GRAY);
        }
        for (Cfg.Reward r : day.rewards) {
            if (!Rewards.give(p, r)) {
                Rewards.msg(p, "Une récompense est invalide (" + r.value + ") : préviens un admin.",
                        ChatFormatting.RED);
            }
        }

        MutableComponent line = Component.literal("Série : ");
        for (int i = 0; i < 7; i++) {
            ChatFormatting color = i < dayIdx ? ChatFormatting.GREEN
                    : (i == dayIdx ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY);
            String mark = i <= dayIdx ? "✔" : "○";
            line.append(Component.literal("J" + (i + 1) + mark + " ").withStyle(color));
        }
        p.sendSystemMessage(line.withStyle(ChatFormatting.GRAY));
    }
}
