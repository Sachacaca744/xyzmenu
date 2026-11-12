package fr.ugta;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Indice de recherche (0 a 5 etoiles) de chaque joueur. */
public class Wanted {
    public static class St {
        public int stars;
        public long lastCrime, attackedCop;
        public int unseen;
        public boolean seen;
        public final List<Long> kills = new ArrayList<>();
        public final List<Long> witnessed = new ArrayList<>();
        int sentStars = -1;
        boolean sentSeen;
        int sentAge;
    }

    private static final Map<UUID, St> MAP = new HashMap<>();

    public static St get(Player p) {
        return MAP.computeIfAbsent(p.getUUID(), k -> new St());
    }

    public static int stars(Player p) {
        St s = MAP.get(p.getUUID());
        return s == null ? 0 : s.stars;
    }

    public static void set(ServerPlayer p, int stars) {
        St s = get(p);
        s.stars = Mth_clamp(stars);
        s.unseen = 0;
        s.lastCrime = p.level().getGameTime();
        if (stars == 0) {
            s.kills.clear();
            s.witnessed.clear();
        }
        send(p, s);
    }

    private static int Mth_clamp(int v) {
        return Math.max(0, Math.min(5, v));
    }

    public static void raise(ServerPlayer p, int stars, String why) {
        St s = get(p);
        s.lastCrime = p.level().getGameTime();
        s.unseen = 0;
        if (stars > s.stars) {
            s.stars = Mth_clamp(stars);
            p.sendSystemMessage(Component.literal("§c★ Recherché (" + s.stars + "/5) §7- " + why));
            send(p, s);
        }
    }

    public static void send(ServerPlayer p, St s) {
        Net.CH.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), new Net.StarsMsg(s.stars, s.seen));
        s.sentStars = s.stars;
        s.sentSeen = s.seen;
        s.sentAge = 0;
    }

    // ---------------------------------------------------------------- armes

    public static boolean isWeapon(ItemStack st) {
        if (st.isEmpty()) return false;
        var item = st.getItem();
        if (item instanceof SwordItem || item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem) return true;
        ResourceLocation k = ForgeRegistries.ITEMS.getKey(item);
        if (k == null) return false;
        String ns = k.getNamespace(), path = k.getPath();
        if (ns.equals("uarms")) return !path.equals("bullet") && !path.equals("shell") && !path.equals("rubber_ball");
        if (ns.equals("upolice")) return !path.equals("handcuffs");
        return ns.equals("ugta") && path.endsWith("_prop");
    }

    // ---------------------------------------------------------------- regles de tir

    public static int crowd(Player t) {
        return t.level().getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(5.0),
                e -> e != t && e.isAlive() && (e instanceof Ped || e instanceof Villager || e instanceof Player)).size();
    }

    public static boolean authorized(Player t) {
        St s = MAP.get(t.getUUID());
        if (s == null || s.stars == 0) return false;
        long now = t.level().getGameTime();
        s.witnessed.removeIf(k -> now - k > 1200);
        return s.witnessed.size() >= 2 || s.stars >= 3 || now - s.attackedCop < 1200;
    }

    /** Un policier peut tirer : delit grave devant les forces de l'ordre et pas de foule. L'armee ignore la foule. */
    public static boolean canShoot(Cop c, Player t) {
        if (!authorized(t)) return false;
        if (c.isArmy()) return true;
        return crowd(t) < 3;
    }

    public static boolean canShootAir(Player t) {
        return authorized(t) && crowd(t) < 3;
    }

    // ---------------------------------------------------------------- evenements

    private static List<Cop> copsWatching(Player p, double r) {
        return p.level().getEntitiesOfClass(Cop.class, p.getBoundingBox().inflate(r), c -> c.isAlive() && c.hasLineOfSight(p));
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim instanceof ServerPlayer sp) {
            MAP.remove(sp.getUUID());
            Net.CH.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp), new Net.StarsMsg(0, false));
        }
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || victim == p) return;
        if (victim instanceof Cop) {
            raise(p, 5, "Policier tué !");
            get(p).attackedCop = p.level().getGameTime();
            return;
        }
        if (!(victim instanceof Ped || victim instanceof Villager || victim instanceof Player || victim instanceof WanderingTrader)) return;
        St s = get(p);
        long now = p.level().getGameTime();
        s.kills.removeIf(k -> now - k > 3600);
        s.kills.add(now);
        boolean cops = !copsWatching(p, 48).isEmpty();
        boolean peds = !p.level().getEntitiesOfClass(Ped.class, p.getBoundingBox().inflate(24),
                x -> x != victim && x.isAlive() && x.hasLineOfSight(p)).isEmpty();
        if (cops) s.witnessed.add(now);
        if (cops || peds || s.stars > 0) {
            raise(p, Math.min(5, 1 + s.kills.size() / 2), cops ? "Meurtre vu par la police" : "Un témoin a alerté la police");
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        LivingEntity v = e.getEntity();
        if (v instanceof Cop) {
            get(p).attackedCop = p.level().getGameTime();
            raise(p, 2, "Agression d'un policier");
        } else if ((v instanceof Ped || v instanceof Villager) && !copsWatching(p, 40).isEmpty()) {
            raise(p, 1, "Agression");
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp)
            Net.CH.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp), new Net.StarsMsg(0, false));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        MAP.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 10 != 0) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) update(p);
    }

    private static void update(ServerPlayer p) {
        St s = get(p);
        long now = p.level().getGameTime();
        boolean seen = false, copNear = false;
        if (s.stars > 0) {
            AABB box = p.getBoundingBox().inflate(100);
            for (Cop c : p.level().getEntitiesOfClass(Cop.class, box, Cop::isAlive)) {
                copNear = true;
                if (c.distanceTo(p) < 60 && c.hasLineOfSight(p)) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                for (Vehicle v : p.level().getEntitiesOfClass(Vehicle.class, box, x -> x.kind == Kind.POLICE_HELI)) {
                    copNear = true;
                    double dx = v.getX() - p.getX(), dz = v.getZ() - p.getZ();
                    if (dx * dx + dz * dz < 45 * 45) seen = true;
                }
            }
            if (seen) s.unseen = 0;
            else s.unseen += 10;
            int limit = copNear ? 700 : 300;
            if (now - s.lastCrime > 200 && s.unseen > limit) {
                s.stars--;
                s.unseen = 0;
                s.sentStars = -1;
                if (s.stars <= 0) {
                    s.stars = 0;
                    s.kills.clear();
                    s.witnessed.clear();
                    p.sendSystemMessage(Component.literal("§aVous avez sémé la police."));
                }
            }
        }
        s.seen = seen;
        s.sentAge += 10;
        if (s.sentStars != s.stars || s.sentSeen != s.seen || s.sentAge >= 60) send(p, s);
    }

    /** Arrestation : on perd ses etoiles et ses armes tombent au sol. */
    public static void bust(ServerPlayer p, Cop c) {
        St s = get(p);
        if (s.stars == 0) return;
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            ItemStack st = p.getItemInHand(hand);
            if (isWeapon(st)) {
                p.drop(st.copy(), true);
                p.setItemInHand(hand, ItemStack.EMPTY);
            }
        }
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 4));
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
        p.stopRiding();
        set(p, 0);
        p.sendSystemMessage(Component.literal("§9ÉARRETÉ §f- vous avez été arrêté par la police. Vos armes sont au sol.".replace("ÉARRETÉ", "ARRÊTÉ")));
    }
}
