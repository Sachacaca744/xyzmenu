package fr.uking;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/** Donnees de tous les fiefs + guerre + economie + raids. Stockees dans le monde (Surface). */
public class Fiefs extends SavedData {
    public final List<Fief> all = new ArrayList<>();
    public int nextId = 1;
    public final Set<String> wars = new LinkedHashSet<>();
    public boolean npcDone;
    public final Map<String, Long> nextRaid = new HashMap<>();
    public final List<Raid> raids = new ArrayList<>();
    public long nextNpcWar;

    public static final String[] NAMES = {"Montclair", "Castel-Roc", "Hautepierre", "Valombre", "Ferrebois", "Bellegarde", "Roche-Noire", "Mont-Aigle",
            "Aubrac", "Clairval", "Sombrelande", "Pierrefort", "Valmont", "Brumevent", "Corbeau-Haut", "Fontclaire", "Tourbrune", "Grisemaille"};
    public static final String[] LORDS = {"Sire Aldéric", "Baron Gaspard", "Comte Thibaut", "Dame Mathilde", "Sire Enguerrand", "Duc Raoul",
            "Baronne Isaure", "Comte Hugues", "Sire Bertrand", "Dame Aliénor"};

    public static class Raid {
        public int npcId, targetId;
        public long start;
        public final Set<UUID> units = new HashSet<>();
        public boolean fresh = true;
    }

    // ---------------------------------------------------------------- acces
    public static Fiefs get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(Fiefs::load, Fiefs::new, "uking_fiefs");
    }

    public static Fiefs get(net.minecraft.world.level.Level l) {
        return get(((ServerLevel) l).getServer());
    }

    public Fief byId(int id) {
        for (Fief f : all) if (f.id == id) return f;
        return null;
    }

    public Fief byPos(BlockPos p) {
        for (Fief f : all) if (f.pos.equals(p)) return f;
        return null;
    }

    public Fief byKey(String k) {
        for (Fief f : all) if (f.key().equals(k)) return f;
        return null;
    }

    static double hd(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Fief dont le territoire contient pos (le plus proche), ou null. */
    public Fief at(BlockPos p) {
        Fief best = null;
        double bd = 1e9;
        for (Fief f : all) {
            double d = hd(f.pos, p);
            if (d <= f.radius() && d < bd) { bd = d; best = f; }
        }
        return best;
    }

    public Fief nearestOwned(UUID owner, BlockPos p, boolean inside) {
        Fief best = null;
        double bd = 1e9;
        for (Fief f : all) {
            if (!owner.equals(f.owner)) continue;
            double d = hd(f.pos, p);
            if (inside && d > f.radius()) continue;
            if (d < bd) { bd = d; best = f; }
        }
        return best;
    }

    public int ownedCount(UUID u, boolean foundedOnly) {
        int n = 0;
        for (Fief f : all) if (u.equals(f.owner) && !(foundedOnly && f.conquered)) n++;
        return n;
    }

    // ---------------------------------------------------------------- guerre
    static String pair(String a, String b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    public boolean war(String a, String b) {
        return !a.equals(b) && wars.contains(pair(a, b));
    }

    public void declareWar(String a, String b) {
        if (a.equals(b)) return;
        if (wars.add(pair(a, b))) setDirty();
    }

    public void makePeace(String a, String b) {
        if (wars.remove(pair(a, b))) setDirty();
        raids.removeIf(r -> {
            Fief n = byId(r.npcId), t = byId(r.targetId);
            return n == null || t == null || pair(n.key(), t.key()).equals(pair(a, b));
        });
    }

    // ---------------------------------------------------------------- creation
    public Fief create(String name, UUID owner, BlockPos pos, int level) {
        Fief f = new Fief();
        f.id = nextId++;
        f.name = name;
        f.owner = owner;
        f.pos = pos;
        f.level = level;
        all.add(f);
        setDirty();
        return f;
    }

    public String freshName(Random rnd) {
        for (int i = 0; i < 40; i++) {
            String n = NAMES[rnd.nextInt(NAMES.length)];
            boolean used = false;
            for (Fief f : all) if (f.name.contains(n)) used = true;
            if (!used) return n;
        }
        return NAMES[rnd.nextInt(NAMES.length)] + "-" + (nextId);
    }

    /** Fonde un fief pour le joueur a pos (la pierre vient d'etre posee). null si ok, sinon message d'erreur. */
    public String found(ServerPlayer p, BlockPos pos) {
        if (p.level() != p.getServer().overworld()) return "Un fief ne peut être fondé que dans la Surface.";
        if (ownedCount(p.getUUID(), true) >= Cfg.MAX_FIEFS.get()) return "Vous avez déjà fondé le maximum de fiefs (" + Cfg.MAX_FIEFS.get() + ").";
        for (Fief f : all) if (hd(f.pos, pos) < 90) return "Trop près du fief « " + f.name + " » (minimum 90 blocs).";
        Fief f = create("Fief de " + freshName(new Random(pos.asLong())), p.getUUID(), pos, 0);
        f.name = "Fief de " + p.getGameProfile().getName();
        if (ownedCount(p.getUUID(), true) > 1) f.name += " II";
        f.color = 0;
        f.gold = Cfg.cost(60);
        f.lastIncome = p.getServer().overworld().getGameTime();
        p.displayClientMessage(Component.literal("§6Fief fondé ! §fFaites un clic droit sur la pierre pour le gérer. Hameau : 60 or de départ."), false);
        if (!npcDone && Cfg.NPC_FIEFS.get() > 0) {
            npcDone = true;
            Npc.generateAround((ServerLevel) p.level(), this, pos, Cfg.NPC_FIEFS.get());
            p.displayClientMessage(Component.literal("§eDes seigneurs voisins se sont installés dans la région. Consultez la diplomatie dans la pierre de fief."), false);
        }
        setDirty();
        return null;
    }

    public void removeAt(BlockPos pos) {
        Fief f = byPos(pos);
        if (f == null) return;
        all.remove(f);
        wars.removeIf(w -> w.contains(f.key()));
        setDirty();
    }

    // ---------------------------------------------------------------- unites
    public static int count(Fief f) { return f.army.size(); }

    /** Cherche un emplacement libre autour de base. */
    public static BlockPos spawnSpot(ServerLevel lv, BlockPos base, int rmin, int rmax, Random rnd) {
        for (int i = 0; i < 40; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = rmin + rnd.nextDouble() * (rmax - rmin);
            int x = base.getX() + (int) Math.round(Math.cos(a) * r), z = base.getZ() + (int) Math.round(Math.sin(a) * r);
            for (int y = base.getY() + 4; y >= base.getY() - 6; y--) {
                BlockPos p = new BlockPos(x, y, z);
                if (lv.getBlockState(p.below()).blocksMotion() && lv.getBlockState(p).getCollisionShape(lv, p).isEmpty()
                        && lv.getBlockState(p.above()).getCollisionShape(lv, p.above()).isEmpty() && lv.getFluidState(p).isEmpty())
                    return p;
            }
        }
        return base.above();
    }

    public Soldier spawnUnit(ServerLevel lv, Fief f, UType t, BlockPos at, int order) {
        Soldier s = Reg.SOLDIER.get().create(lv);
        if (s == null) return null;
        s.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, lv.random.nextFloat() * 360f, 0);
        s.setup(t, f);
        s.setOrder(order);
        lv.addFreshEntity(s);
        if (t == UType.PEASANT) f.peasants.add(s.getUUID());
        else f.army.add(s.getUUID());
        setDirty();
        return s;
    }

    public Catapult spawnCatapult(ServerLevel lv, String key, BlockPos at, float yaw, int ammo) {
        Catapult c = Reg.CATAPULT.get().create(lv);
        if (c == null) return null;
        c.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
        c.setOwnerKey(key);
        c.setAmmo(ammo);
        lv.addFreshEntity(c);
        return c;
    }

    public static int cost(int type) {
        return type == 6 ? Cfg.cost(120) : Cfg.cost(UType.of(type).cost);
    }

    /** Recrute (type 0-5 = UType, 6 = catapulte). null si ok. */
    public String recruit(ServerPlayer p, Fief f, int type) {
        if (!p.getUUID().equals(f.owner)) return "Ce n'est pas votre fief.";
        ServerLevel lv = (ServerLevel) p.level();
        if (type < 0 || type > 6 || type == UType.LORD.ordinal()) return "Type inconnu.";
        int c = cost(type);
        if (f.gold < c) return "Pas assez d'or (" + c + " nécessaires).";
        if (type == UType.CROSSBOW.ordinal() && f.counts[4] == 0) return "Il faut une forge pour les arbalétriers.";
        if (type == UType.KNIGHT.ordinal() && f.counts[3] == 0) return "Il faut une caserne pour les chevaliers.";
        if (type == 6 && (f.counts[4] == 0 || f.level < 1)) return "La catapulte demande un village (niveau 2) et une forge.";
        if (type == UType.PEASANT.ordinal()) {
            if (f.peasants.size() >= 6 + f.counts[0] * 2) return "Pas assez de logements : construisez des maisons.";
        } else if (type != 6 && f.army.size() >= f.armyCap()) return "Armée au maximum (" + f.armyCap() + "). Construisez casernes/tours ou améliorez le fief.";
        if (type == 6 && f.catapults.size() >= 2 + f.level) return "Trop de catapultes pour ce fief.";
        f.gold -= c;
        BlockPos at = spawnSpot(lv, f.pos, 3, 7, lv.random);
        if (type == 6) {
            Catapult cat = spawnCatapult(lv, f.key(), at, lv.random.nextFloat() * 360f, 3);
            if (cat != null) f.catapults.add(cat.blockPosition());
        } else {
            spawnUnit(lv, f, UType.of(type), at, type == 0 ? 2 : 0);
        }
        lv.playSound(null, f.pos, SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 1f, 1f);
        setDirty();
        return null;
    }

    public String upgrade(ServerPlayer p, Fief f) {
        if (!p.getUUID().equals(f.owner)) return "Ce n'est pas votre fief.";
        if (f.level >= 2) return "Déjà au niveau maximum.";
        int c = Cfg.cost(f.level == 0 ? 200 : 600);
        if (f.level == 0 && f.counts[0] < 2) return "Il faut au moins 2 maisons pour devenir un village.";
        if (f.level == 1 && f.counts[7] == 0) return "Construisez un château (plan du bâtisseur) pour passer au niveau château.";
        if (f.gold < c) return "Pas assez d'or (" + c + " nécessaires).";
        f.gold -= c;
        f.level++;
        setDirty();
        p.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§6" + f.name + " devient un " + (f.level == 1 ? "village" : "château") + " !"), false);
        return null;
    }

    public int deposit(ServerPlayer p, Fief f) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(Items.GOLD_INGOT)) { n += s.getCount(); p.getInventory().setItem(i, ItemStack.EMPTY); }
            else if (s.is(Items.GOLD_BLOCK)) { n += 9 * s.getCount(); p.getInventory().setItem(i, ItemStack.EMPTY); }
        }
        f.gold += n;
        setDirty();
        return n;
    }

    public int withdraw(ServerPlayer p, Fief f, int n) {
        n = Math.min(n, f.gold);
        if (n <= 0) return 0;
        f.gold -= n;
        int left = n;
        while (left > 0) {
            int k = Math.min(64, left);
            ItemStack s = new ItemStack(Items.GOLD_INGOT, k);
            if (!p.getInventory().add(s)) p.drop(s, false);
            left -= k;
        }
        setDirty();
        return n;
    }

    public static int invGold(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(Items.GOLD_INGOT)) n += s.getCount();
            else if (s.is(Items.GOLD_BLOCK)) n += 9 * s.getCount();
        }
        return n;
    }

    // ---------------------------------------------------------------- diplomatie
    public String commandWar(ServerPlayer p, Fief target) {
        if (target.owner != null && target.owner.equals(p.getUUID())) return "C'est votre propre fief.";
        if (nearestOwned(p.getUUID(), target.pos, false) == null) return "Vous n'avez pas de fief.";
        String pk = p.getUUID().toString();
        if (war(pk, target.key())) return "Déjà en guerre.";
        declareWar(pk, target.key());
        p.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§c⚔ " + p.getGameProfile().getName() + " déclare la guerre à " + target.name + " !"), false);
        return null;
    }

    public String commandPeace(ServerPlayer p, Fief target) {
        String pk = p.getUUID().toString();
        if (!war(pk, target.key())) return "Vous n'êtes pas en guerre avec ce fief.";
        Fief mine = nearestOwned(p.getUUID(), target.pos, false);
        int tribute = Cfg.cost(40 + 40 * target.level);
        if (mine == null || mine.gold < tribute) return "Il faut " + tribute + " or (tribut) dans votre fief pour demander la paix.";
        mine.gold -= tribute;
        target.gold += tribute;
        makePeace(pk, target.key());
        setDirty();
        return null;
    }

    /** Fiefs ennemis encore defendus pres de la pierre (soldats vivants charges). */
    public int garrisonNear(ServerLevel lv, Fief f, double r) {
        int n = 0;
        AABB box = new AABB(f.pos).inflate(r, 60, r);
        for (Soldier s : lv.getEntitiesOfClass(Soldier.class, box)) if (s.getFiefId() == f.id && s.type().fighter() && s.isAlive()) n++;
        return n;
    }

    public String conquer(ServerPlayer p, Fief t) {
        ServerLevel lv = (ServerLevel) p.level();
        if (!t.npc() && t.owner.equals(p.getUUID())) return "C'est déjà votre fief.";
        if (!war(p.getUUID().toString(), t.key())) return "Déclarez d'abord la guerre à ce fief.";
        if (Math.sqrt(p.blockPosition().distSqr(t.pos)) > 48) return "Approchez-vous de la pierre de fief (48 blocs max).";
        int g = garrisonNear(lv, t, 60);
        if (g > 0) return "La garnison tient encore (" + g + " soldats) : éliminez-la d'abord.";
        String old = t.key();
        UUID oldOwner = t.owner;
        wars.removeIf(w -> w.contains(old));
        t.owner = p.getUUID();
        t.color = 0;
        t.conquered = true;
        t.gold += Cfg.cost(100 + 60 * t.level);
        t.lastIncome = lv.getGameTime();
        raids.removeIf(r -> r.npcId == t.id || r.targetId == t.id);
        setDirty();
        p.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§6🏰 " + p.getGameProfile().getName() + " conquiert " + t.name + " !"), false);
        if (oldOwner != null) {
            ServerPlayer op = p.getServer().getPlayerList().getPlayer(oldOwner);
            if (op != null) op.displayClientMessage(Component.literal("§cVotre fief " + t.name + " a été conquis !"), false);
        }
        return null;
    }

    // ---------------------------------------------------------------- raids
    public Raid startRaid(ServerLevel lv, Fief npc, Fief target) {
        Random rnd = lv.random;
        double a = rnd.nextDouble() * Math.PI * 2;
        BlockPos center = new BlockPos(target.pos.getX() + (int) (Math.cos(a) * 46), target.pos.getY(), target.pos.getZ() + (int) (Math.sin(a) * 46));
        Raid r = new Raid();
        r.npcId = npc.id;
        r.targetId = target.id;
        r.start = lv.getGameTime();
        int n = 3 + 2 * npc.level + Math.min(4, target.level * 2);
        UType[] mix = {UType.SPEARMAN, UType.ARCHER, UType.SPEARMAN, UType.CROSSBOW, UType.KNIGHT};
        for (int i = 0; i < n; i++) {
            BlockPos at = spawnSpot(lv, center, 0, 7, rnd);
            Soldier s = spawnUnit(lv, npc, mix[i % mix.length], at, Soldier.O_RALLY);
            if (s != null) { s.setRally(target.pos); r.units.add(s.getUUID()); }
        }
        if (npc.level >= 1) {
            Catapult c = spawnCatapult(lv, npc.key(), spawnSpot(lv, center, 0, 5, rnd), 0, 6);
            if (c != null) r.units.add(c.getUUID());
        }
        raids.add(r);
        String dir = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? (Math.cos(a) > 0 ? "l'est" : "l'ouest") : (Math.sin(a) > 0 ? "le sud" : "le nord");
        if (target.owner != null) {
            ServerPlayer op = lv.getServer().getPlayerList().getPlayer(target.owner);
            if (op != null) op.displayClientMessage(Component.literal("§c⚔ Les troupes de " + npc.name + " (" + n + " soldats) attaquent " + target.name + " par " + dir + " !"), false);
        }
        return r;
    }

    void tickRaids(ServerLevel lv) {
        long now = lv.getGameTime();
        for (Iterator<Raid> it = raids.iterator(); it.hasNext(); ) {
            Raid r = it.next();
            Fief npc = byId(r.npcId), tgt = byId(r.targetId);
            if (npc == null || tgt == null) { discardRaid(lv, r); it.remove(); continue; }
            int alive = 0;
            boolean near = false;
            for (UUID u : r.units) {
                Entity e = lv.getEntity(u);
                if (e == null || !e.isAlive()) continue;
                alive++;
                if (e instanceof Soldier && e.blockPosition().distSqr(tgt.pos) < 14 * 14) near = true;
            }
            ServerPlayer op = tgt.owner == null ? null : lv.getServer().getPlayerList().getPlayer(tgt.owner);
            if (alive == 0 && !r.fresh) {
                if (op != null) op.displayClientMessage(Component.literal("§a🛡 L'attaque de " + npc.name + " est repoussée !"), false);
                it.remove();
                continue;
            }
            r.fresh = false;
            if (near && garrisonNear(lv, tgt, 24) == 0) {
                int loot = Math.min(tgt.gold, 10 + tgt.gold / 4);
                tgt.gold -= loot;
                npc.gold += loot;
                if (op != null) op.displayClientMessage(Component.literal("§c💰 " + npc.name + " a pillé " + loot + " or à " + tgt.name + " !"), false);
                discardRaid(lv, r);
                it.remove();
                setDirty();
                continue;
            }
            if (now - r.start > 20 * 60 * 6) { discardRaid(lv, r); it.remove(); }
        }
    }

    void discardRaid(ServerLevel lv, Raid r) {
        for (UUID u : r.units) {
            Entity e = lv.getEntity(u);
            if (e != null) e.discard();
        }
    }

    // ---------------------------------------------------------------- tick
    public void tick(ServerLevel lv) {
        long now = lv.getGameTime();
        MinecraftServer srv = lv.getServer();
        for (Fief f : all) {
            if (f.owner != null && now - f.lastIncome >= Cfg.INCOME_SECONDS.get() * 20L) {
                f.lastIncome = now;
                int inc = f.income();
                f.gold += inc;
                setDirty();
                ServerPlayer op = srv.getPlayerList().getPlayer(f.owner);
                if (op != null && op.blockPosition().distSqr(f.pos) < 120 * 120)
                    op.displayClientMessage(Component.literal("§6+" + inc + " or §7(" + f.name + ")"), true);
            }
        }
        tickRaids(lv);
        if (Cfg.NPC_WAR.get() && now >= nextNpcWar) {
            nextNpcWar = now + 20L * 60 * 6;
            for (Fief n : all) {
                if (!n.npc()) continue;
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    Fief mine = nearestOwned(p.getUUID(), n.pos, false);
                    if (mine == null || hd(mine.pos, n.pos) > 700 || war(p.getUUID().toString(), n.key())) continue;
                    if (lv.random.nextInt(100) < 18) {
                        declareWar(p.getUUID().toString(), n.key());
                        srv.getPlayerList().broadcastSystemMessage(Component.literal("§c⚔ " + n.lord + " de " + n.name + " déclare la guerre à " + p.getGameProfile().getName() + " !"), false);
                    }
                }
            }
        }
        if (Cfg.RAIDS.get()) {
            for (String w : new ArrayList<>(wars)) {
                String[] k = w.split("\\|");
                Fief a = byKey(k[0]), b = byKey(k[1]);
                Fief npc = a != null && a.npc() ? a : b != null && b.npc() ? b : null;
                String pk = npc == null ? null : (npc == a ? k[1] : k[0]);
                if (npc == null || pk == null) continue;
                UUID pu;
                try { pu = UUID.fromString(pk); } catch (Exception ex) { continue; }
                ServerPlayer op = srv.getPlayerList().getPlayer(pu);
                if (op == null) continue;
                Fief target = nearestOwned(pu, npc.pos, false);
                if (target == null || !lv.isLoaded(target.pos)) continue;
                boolean active = false;
                for (Raid r : raids) if (r.npcId == npc.id) active = true;
                if (active) continue;
                long due = nextRaid.computeIfAbsent(w, x -> now + 20L * 60 * Cfg.RAID_MINUTES.get());
                if (now >= due) {
                    nextRaid.put(w, now + (long) (20L * 60 * Cfg.RAID_MINUTES.get() * (0.7 + lv.random.nextDouble() * 0.6)));
                    startRaid(lv, npc, target);
                }
            }
        }
    }

    // ---------------------------------------------------------------- GUI
    public Net.StateMsg state(ServerPlayer p, Fief f, String msg) {
        List<Net.Nb> nb = new ArrayList<>();
        List<Fief> others = new ArrayList<>(all);
        others.remove(f);
        others.sort(Comparator.comparingDouble(o -> hd(o.pos, f.pos)));
        String pk = p.getUUID().toString();
        for (Fief o : others) {
            if (nb.size() >= 6) break;
            boolean w = war(pk, o.key());
            boolean mine = p.getUUID().equals(o.owner);
            int cq = w && !mine && p.blockPosition().distSqr(o.pos) < 48 * 48 && garrisonNear((ServerLevel) p.level(), o, 60) == 0 ? 1 : 0;
            nb.add(new Net.Nb(o.id, o.name, o.level, o.color, o.pos.getX() - f.pos.getX(), o.pos.getZ() - f.pos.getZ(), w, mine, cq, o.npc()));
        }
        return new Net.StateMsg(f.id, f.name, f.level, f.gold, invGold(p), f.army.size(), f.armyCap(), f.peasants.size(), f.income(),
                p.getUUID().equals(f.owner), f.counts.clone(), nb, msg == null ? "" : msg, f.catapults.size(), war(pk, f.key()));
    }

    public void openGui(ServerPlayer p, Fief f) {
        Net.CH.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), state(p, f, ""));
    }

    // ---------------------------------------------------------------- sauvegarde
    @Override
    public CompoundTag save(CompoundTag t) {
        t.putInt("next", nextId);
        t.putBoolean("npcDone", npcDone);
        ListTag l = new ListTag();
        for (Fief f : all) l.add(f.save());
        t.put("fiefs", l);
        ListTag w = new ListTag();
        for (String s : wars) w.add(StringTag.valueOf(s));
        t.put("wars", w);
        return t;
    }

    public static Fiefs load(CompoundTag t) {
        Fiefs d = new Fiefs();
        d.nextId = Math.max(1, t.getInt("next"));
        d.npcDone = t.getBoolean("npcDone");
        for (Tag x : t.getList("fiefs", Tag.TAG_COMPOUND)) d.all.add(Fief.load((CompoundTag) x));
        for (Tag x : t.getList("wars", Tag.TAG_STRING)) d.wars.add(x.getAsString());
        return d;
    }

    // ---------------------------------------------------------------- evenements
    @Mod.EventBusSubscriber(modid = Uking.MODID)
    public static class Events {
        static int tick;

        @SubscribeEvent
        public static void serverTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            if (++tick % 20 != 0) return;
            ServerLevel lv = e.getServer().overworld();
            if (lv == null) return;
            try {
                get(e.getServer()).tick(lv);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        static String blocked(Player p, BlockPos pos) {
            if (!Cfg.PROTECT.get() || p == null || p.hasPermissions(2) && p.isCreative()) return null;
            if (!(p.level() instanceof ServerLevel lv)) return null;
            Fiefs fs = get(lv);
            for (Fief f : fs.all) {
                if (hd(f.pos, pos) > f.radius()) continue;
                if (p.getUUID().equals(f.owner)) return null;
                if (fs.war(p.getUUID().toString(), f.key())) return null;
                return f.name;
            }
            return null;
        }

        @SubscribeEvent
        public static void brk(BlockEvent.BreakEvent e) {
            String b = blocked(e.getPlayer(), e.getPos());
            if (b != null) {
                e.setCanceled(true);
                e.getPlayer().displayClientMessage(Component.literal("§cTerritoire de « " + b + " » : déclarez la guerre pour y casser des blocs."), true);
            }
        }

        @SubscribeEvent
        public static void place(BlockEvent.EntityPlaceEvent e) {
            if (!(e.getEntity() instanceof Player p)) return;
            if (e.getState().is(Reg.FIEF_STONE.get())) return;
            String b = blocked(p, e.getPos());
            if (b != null) {
                e.setCanceled(true);
                p.displayClientMessage(Component.literal("§cTerritoire de « " + b + " » : déclarez la guerre pour y construire."), true);
            }
        }

        @SubscribeEvent
        public static void cmds(RegisterCommandsEvent e) {
            var d = e.getDispatcher();
            d.register(Commands.literal("uking")
                    .then(Commands.literal("info").executes(c -> info(c.getSource())))
                    .then(Commands.literal("raid").requires(s -> s.hasPermission(2)).executes(c -> raid(c.getSource())))
                    .then(Commands.literal("npc").requires(s -> s.hasPermission(2)).executes(c -> npc(c.getSource())))
                    .then(Commands.literal("gold").requires(s -> s.hasPermission(2))
                            .then(Commands.argument("n", IntegerArgumentType.integer(1, 100000)).executes(c -> gold(c.getSource(), IntegerArgumentType.getInteger(c, "n"))))));
        }

        static int info(CommandSourceStack s) {
            Fiefs fs = get(s.getServer());
            if (fs.all.isEmpty()) { s.sendSuccess(() -> Component.literal("Aucun fief."), false); return 0; }
            for (Fief f : fs.all)
                s.sendSuccess(() -> Component.literal("#" + f.id + " " + f.name + " [" + f.levelKey() + "] " + (f.npc() ? "PNJ" : "joueur") + " pos " + f.pos.toShortString()
                        + " or " + f.gold + " armée " + f.army.size()), false);
            return fs.all.size();
        }

        static int gold(CommandSourceStack s, int n) {
            try {
                ServerPlayer p = s.getPlayerOrException();
                Fiefs fs = get(s.getServer());
                Fief f = fs.nearestOwned(p.getUUID(), p.blockPosition(), false);
                if (f == null) { s.sendFailure(Component.literal("Aucun fief.")); return 0; }
                f.gold += n;
                fs.setDirty();
                s.sendSuccess(() -> Component.literal("+" + n + " or pour " + f.name), false);
                return 1;
            } catch (Exception ex) { return 0; }
        }

        static int npc(CommandSourceStack s) {
            try {
                ServerPlayer p = s.getPlayerOrException();
                Npc.generateAround((ServerLevel) p.level(), get(s.getServer()), p.blockPosition(), 1);
                return 1;
            } catch (Exception ex) { return 0; }
        }

        static int raid(CommandSourceStack s) {
            try {
                ServerPlayer p = s.getPlayerOrException();
                Fiefs fs = get(s.getServer());
                Fief mine = fs.nearestOwned(p.getUUID(), p.blockPosition(), false);
                Fief npc = null;
                double bd = 1e18;
                for (Fief f : fs.all) if (f.npc() && hd(f.pos, p.blockPosition()) < bd) { bd = hd(f.pos, p.blockPosition()); npc = f; }
                if (mine == null || npc == null) { s.sendFailure(Component.literal("Il faut un fief joueur et un fief PNJ.")); return 0; }
                fs.declareWar(p.getUUID().toString(), npc.key());
                fs.startRaid((ServerLevel) p.level(), npc, mine);
                return 1;
            } catch (Exception ex) { return 0; }
        }
    }
}
