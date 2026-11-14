package fr.udrone;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Pilot {
    /** joueur -> drone qu'il pilote (serveur). */
    public static final Map<UUID, Drone> CTRL = new ConcurrentHashMap<>();

    /** Passe au drone suivant appartenant au joueur (liaison valide). Le precedent reste en vol stationnaire / autopilote. */
    public static void next(ServerPlayer sp, Drone cur) {
        ServerLevel sl = sp.serverLevel();
        List<Drone> list = new ArrayList<>();
        for (Entity e : sl.getAllEntities()) {
            if (e instanceof Drone d && d.state() != Drone.DEAD && d.ownerIs(sp)
                    && (d == cur || !d.piloted()) && d.state() != Drone.LOST) list.add(d);
        }
        list.sort(Comparator.comparingInt(Entity::getId));
        if (list.size() < 2) {
            sp.displayClientMessage(Component.literal("Aucun autre drone disponible."), true);
            return;
        }
        int i = list.indexOf(cur);
        for (int k = 1; k < list.size(); k++) {
            Drone n = list.get((i + k) % list.size());
            if (n.battery() <= 0 && n.state() != Drone.LANDED) continue;
            cur.release();
            n.station = null;
            if (n.takeControl(sp)) {
                sp.displayClientMessage(Component.literal("Drone : " + n.kind.title), true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void hurt(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && e.getAmount() > 0) {
            Drone d = CTRL.get(sp.getUUID());
            if (d != null) {
                d.release();
                sp.displayClientMessage(Component.literal("Vous etes attaque : liaison video coupee !"), true);
            }
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        Player p = e.getEntity();
        Drone d = CTRL.remove(p.getUUID());
        if (d != null && !d.isRemoved()) d.release();
    }
}
