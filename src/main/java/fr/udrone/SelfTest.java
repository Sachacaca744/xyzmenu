package fr.udrone;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Tests automatiques cote serveur (actifs uniquement avec -Dudrone.selftest). Non inclus dans le jar livre. */
public class SelfTest {
    static int pass, fail;
    static boolean running, auto;
    static int t;
    static ServerLevel lv;
    static MinecraftServer server;
    static final List<Run> runs = new ArrayList<>();

    static class Run {
        String name;
        Kind kind;
        Drone d;
        ServerPlayer fp;
        Vec3 start;
        double alt0;
        boolean done;
        int wi = -1, wstart;
        int[] ammo0;
        int ph;
        int t0;
        Vec3 pos0;
        String mode;
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(SelfTest.class);
        auto = "auto".equals(System.getProperty("udrone.selftest"));
        System.out.println("SELFTEST: actif (auto=" + auto + ")");
        if (System.getProperty("udrone.clienttest") != null) {
            try {
                Class.forName("fr.udrone.client.ClientTest").getMethod("init").invoke(null);
            } catch (Throwable t) {
                System.out.println("SELFTEST: client test impossible " + t);
            }
        }
    }

    static void check(String what, boolean ok, String detail) {
        if (ok) pass++;
        else fail++;
        System.out.println("SELFTEST " + (ok ? "PASS " : "FAIL ") + what + " | " + detail);
    }

    @SubscribeEvent
    public static void cmds(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("udrone_selftest").requires(s -> s.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.greedyString()).executes(c -> {
                    String n = StringArgumentType.getString(c, "name");
                    return cmd(c.getSource(), n);
                })));
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent e) {
        server = e.getServer();
        if (auto) startPhysics(server.overworld());
    }

    static int cmd(CommandSourceStack src, String n) {
        ServerLevel sl = src.getLevel();
        String[] a = n.split(" ");
        try {
            switch (a[0]) {
                case "physics" -> startPhysics(sl);
                case "models" -> models(src);
                case "fly" -> fly(sl);
                case "pilot" -> {
                    Kind k = Kind.valueOf(a[1].toUpperCase());
                    ServerPlayer sp = src.getPlayerOrException();
                    Drone best = null;
                    for (Entity en : sl.getAllEntities())
                        if (en instanceof Drone d && d.kind == k && (best == null || d.distanceTo(sp) < best.distanceTo(sp))) best = d;
                    if (best != null) {
                        best.station = null;
                        best.takeControl(sp);
                    }
                }
                case "station" -> station(src);
                default -> src.sendFailure(Component.literal("?"));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            src.sendFailure(Component.literal("erreur " + ex));
        }
        return 1;
    }

    // ------------------------------------------------------------ outils pour captures client
    static void models(CommandSourceStack src) {
        ServerLevel sl = src.getLevel();
        ServerPlayer sp = src.getPlayer();
        int[] xs = {-14, -8, -3, 1, 5, 8};
        Kind[] ks = {Kind.REAPER, Kind.HEAVY, Kind.SURVEIL, Kind.QUAD, Kind.FAST, Kind.MINI};
        for (int i = 0; i < ks.length; i++) {
            Drone d = Drone.spawn(sl, ks[i], new Vec3(xs[i] + 0.5, -60, 8.5), sp, null);
            d.setYRot(180);
            d.yRotO = 180;
            sl.addFreshEntity(d);
        }
    }

    static void fly(ServerLevel sl) {
        for (Entity en : sl.getAllEntities()) {
            if (en instanceof Drone d) {
                d.setState(d.kind.wing ? Drone.LOITER : Drone.FLYING);
                d.loiterY = d.getY() + 3;
                d.spd = d.kind.vmax * 0.5;
                d.setPos(d.getX(), d.getY() + 3, d.getZ());
            }
        }
    }

    static void station(CommandSourceStack src) {
        ServerLevel sl = src.getLevel();
        ServerPlayer sp = src.getPlayer();
        BlockPos p = new BlockPos(10, -60, 0);
        sl.setBlock(p, Reg.STATION.get().defaultBlockState(), 3);
        sl.setBlock(p.east(2), Reg.PAD.get().defaultBlockState(), 3);
        if (sl.getBlockEntity(p) instanceof StationBE be) {
            for (Entity en : sl.getAllEntities()) if (en instanceof Drone d) be.link(d.getUUID(), d.kind.ordinal());
            StationBlock.sendData(sp, p, be, true);
        }
    }

    // ------------------------------------------------------------ scenario physique
    static Run mk(String name, Kind k, double x, double z, String mode) {
        Run r = new Run();
        r.name = name;
        r.kind = k;
        r.mode = mode;
        BlockPos bp = lv.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos((int) x, 0, (int) z));
        r.start = new Vec3(x + 0.5, bp.getY(), z + 0.5);
        r.fp = FakePlayerFactory.get(lv, new GameProfile(UUID.randomUUID(), "[T" + name + "]"));
        r.fp.setPos(r.start.x, r.start.y, r.start.z - 3);
        return r;
    }

    static void spawn(Run r, CompoundTag tag) {
        r.d = Drone.spawn(lv, r.kind, r.start, r.fp, tag);
        r.d.testPilot = r.fp;
        r.d.testSrc = r.fp.position();
        r.d.setYRot(0);
        r.d.yRotO = 0;
        lv.addFreshEntity(r.d);
        r.alt0 = r.d.getY();
        r.pos0 = r.d.position();
        r.t0 = t;
    }

    static void startPhysics(ServerLevel sl) {
        if (running) return;
        lv = sl;
        for (int cx = -5; cx <= 7; cx++) for (int cz = -2; cz <= 9; cz++) lv.setChunkForced(cx, cz, true);
        running = true;
        t = 0;
        pass = fail = 0;
        runs.clear();
        // force le chargement de la zone de test
        lv.getChunkSource().getChunk(0, 0, net.minecraft.world.level.chunk.ChunkStatus.FULL, true);
        Kind[] ks = Kind.values();
        for (int i = 0; i < ks.length; i++) {
            Run r = mk("fly_" + ks[i].name(), ks[i], -60 + i * 22, 0, "fly");
            runs.add(r);
        }
        runs.add(mk("signal", Kind.QUAD, 20, 40, "signal"));
        runs.add(mk("lowbatt", Kind.QUAD, 40, 40, "lowbatt"));
        runs.add(mk("destroy", Kind.MINI, 60, 40, "destroy"));
        runs.add(mk("pad", Kind.SURVEIL, 80, 40, "pad"));
        runs.add(mk("persist", Kind.HEAVY, 100, 40, "persist"));
        // chargement des chunks voisins
        for (Run r : runs) lv.getChunkSource().getChunk((int) r.start.x >> 4, (int) r.start.z >> 4, net.minecraft.world.level.chunk.ChunkStatus.FULL, true);
        for (Run r : runs) {
            if (r.mode.equals("lowbatt")) {
                CompoundTag tg = new CompoundTag();
                tg.putInt("Batt", (int) (r.kind.battery * 0.125));
                tg.putFloat("Hp", r.kind.hp);
                tg.putInt("Ammo", r.kind.packedAmmo());
                spawn(r, tg);
            } else if (r.mode.equals("pad")) {
                CompoundTag tg = new CompoundTag();
                tg.putInt("Batt", 100);
                tg.putFloat("Hp", 1f);
                tg.putInt("Ammo", 0);
                lv.setBlock(BlockPos.containing(r.start), Reg.PAD.get().defaultBlockState(), 3);
                spawn(r, tg);
                r.d.testPilot = null;
            } else spawn(r, null);
            boolean ok = r.d.takeControl(r.fp);
            if (r.mode.equals("pad")) {
                r.d.release();
            } else check("takeControl " + r.name, ok && r.d.piloted(), "state=" + r.d.state());
        }
        System.out.println("SELFTEST: demarrage physique, " + runs.size() + " scenarios");
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !running) return;
        t++;
        boolean all = true;
        for (Run r : runs) {
            if (r.done) continue;
            all = false;
            try {
                step(r);
            } catch (Throwable ex) {
                ex.printStackTrace();
                check("exception " + r.name, false, ex.toString());
                r.done = true;
            }
        }
        if (t > 3000 && !all) {
            for (Run r : runs) if (!r.done) check("timeout " + r.name, false, "ph=" + r.ph + " state=" + (r.d == null ? -1 : r.d.state()));
            all = true;
        }
        if (all) {
            running = false;
            System.out.println("SELFTEST RESULT: " + pass + " PASS, " + fail + " FAIL");
            if (auto && server != null) server.halt(false);
        }
    }

    static String pos(Drone d) {
        return String.format("(%.1f,%.1f,%.1f) st=%d batt=%d spd=%.2f", d.getX(), d.getY(), d.getZ(), d.state(), d.battery(), d.spd);
    }

    static void step(Run r) {
        Drone d = r.d;
        int lt = t - r.t0;
        switch (r.mode) {
            case "fly" -> flyStep(r, d, lt);
            case "signal" -> {
                if (lt < 40) d.setInput(0, 0, 1, 0, 0, false);
                if (lt == 40) {
                    check("signal: en vol", d.getY() > r.alt0 + 2, pos(d));
                    r.fp.setPos(r.start.x + 400, r.start.y, r.start.z);
                    r.fp.setPos(r.start.x + 400, r.start.y, r.start.z);
                    d.testSrc = r.fp.position();
                }
                if (lt == 43) {
                    check("signal: perte de liaison", d.state() == Drone.LOST && !d.piloted(), pos(d));
                    r.fp.setPos(r.start.x, r.start.y, r.start.z);
                    d.testSrc = r.fp.position();
                }
                if (lt == 47) check("signal: liaison retablie -> RTB", d.state() == Drone.RTB || d.state() == Drone.LANDED, pos(d));
                if (lt > 47 && d.state() == Drone.LANDED) {
                    check("signal: atterrit", true, pos(d));
                    r.done = true;
                }
                if (lt > 700) {
                    check("signal: atterrit", false, pos(d));
                    r.done = true;
                }
            }
            case "lowbatt" -> {
                if (lt < 40) d.setInput(0, 0, 1, 0, 0, false);
                if (lt == 40) check("batterie faible -> retour force", d.forcedRtb && d.state() == Drone.RTB, pos(d));
                if (lt == 41) {
                    d.release();
                    d.takeControl(r.fp);
                    check("batterie faible: pas de reprise du vol libre", d.state() == Drone.RTB, pos(d));
                }
                if (lt > 41 && d.state() == Drone.LANDED) {
                    check("batterie faible: atterrit", true, pos(d));
                    r.done = true;
                }
                if (lt > 700) {
                    check("batterie faible: atterrit", false, pos(d));
                    r.done = true;
                }
            }
            case "destroy" -> {
                if (lt == 5) {
                    d.hurt(lv.damageSources().generic(), 9999f);
                }
                if (lt == 8) {
                    boolean drop = false;
                    for (Entity en : lv.getAllEntities()) if (en instanceof ItemEntity) drop = true;
                    check("destruction: drone retire", d.isRemoved(), pos(d));
                    check("destruction: objet abime lache", drop, "");
                    r.done = true;
                }
            }
            case "pad" -> {
                if (lt == 3) r.ph = d.battery();
                if (lt == 120) {
                    check("pad: recharge", d.battery() > r.ph + 500, "avant=" + r.ph + " apres=" + d.battery());
                    check("pad: reparation", d.hp() > 1.5f, "hp=" + d.hp());
                    r.done = true;
                }
            }
            case "persist" -> {
                if (lt == 3) {
                    d.setAmmo(1, 5);
                    CompoundTag tg = new CompoundTag();
                    d.saveWithoutId(tg);
                    Drone n = Reg.DRONES.get(Kind.HEAVY).get().create(lv);
                    n.load(tg);
                    check("sauvegarde NBT", n.battery() == d.battery() && n.ammo(1) == 5 && n.hp() == d.hp(), "batt " + n.battery() + " ammo " + n.ammo(1));
                    r.done = true;
                }
            }
            default -> r.done = true;
        }
    }

    static void flyStep(Run r, Drone d, int lt) {
        boolean wing = r.kind.wing;
        if (r.ph < 4) d.testSrc = d.position();
        else if (r.ph == 4 && lt - r.wstart == 29) d.testSrc = r.fp.position();
        if (r.d.isRemoved() && r.ph < 100) {
            check("fly " + r.name + ": drone retire (charge) apres tir", r.kind == Kind.FAST, "");
            r.done = true;
            return;
        }
        if (r.ph == 0) {
            if (lt < 90) {
                d.setInput(wing ? 1 : 0, 0, wing ? (lt > 70 ? 0.7f : 0) : 1, 0, 0, false);
            } else {
                r.ph = 1;
                check("fly " + r.name + ": decollage", d.getY() > r.alt0 + (wing ? 0.5 : 2.0) && d.state() == Drone.FLYING, pos(d));
            }
        } else if (r.ph == 1) {
            int u = lt - 90;
            d.setInput(1, wing ? -0.2f : 0, wing ? 0.4f : 0, 0, 0, false);
            if (u == 100) {
                double dd = Math.hypot(d.getX() - r.pos0.x, d.getZ() - r.pos0.z);
                check("fly " + r.name + ": deplacement horizontal", dd > (wing ? 15 : 4), String.format("dist=%.1f ", dd) + pos(d));
                check("fly " + r.name + ": batterie consommee", d.battery() < r.kind.battery, "batt=" + d.battery());
                r.ph = 2;
                r.wi = 0;
                r.wstart = lt;
                r.ammo0 = new int[3];
                for (int i = 0; i < r.kind.weapons.length; i++) r.ammo0[i] = d.ammo(i);
            }
        } else if (r.ph == 2) {
            if (r.kind.weapons.length == 0) {
                r.ph = 3;
                r.wstart = lt;
                return;
            }
            int u = lt - r.wstart;
            d.setInput(0.5f, 0, wing ? 0.3f : 0, 0, 40, true);
            d.setSel(r.wi);
            if (r.kind.weapons[r.wi] == W.CHARGE) {
                // la charge detruit le drone au premier tir
                if (d.isRemoved()) {
                    check("fly " + r.name + ": charge explosive consommee", true, "");
                    r.done = true;
                }
                return;
            }
            if (u == 70) {
                int a = d.ammo(r.wi);
                check("fly " + r.name + ": tir " + r.kind.weapons[r.wi].title, a < r.ammo0[r.wi], "munitions " + r.ammo0[r.wi] + "->" + a + " chaleur=" + d.heat() + " cd=" + d.cd + " piloted=" + d.piloted() + " fire=" + d.inFire + " hp=" + d.hp() + " " + pos(d));
                r.wi++;
                r.wstart = lt;
                if (r.wi >= r.kind.weapons.length) {
                    r.ph = 3;
                    d.setInput(0, 0, 0, 0, 0, false);
                }
            }
        } else if (r.ph == 3) {
            d.setInput(0, 0, 0, 0, 0, false);
            d.release();
            r.ph = 4;
            r.wstart = lt;
            check("fly " + r.name + ": liberation -> " + (wing ? "autopilote" : "vol stationnaire"),
                    d.state() == (wing ? Drone.LOITER : Drone.FLYING) && !d.piloted(), pos(d));
        } else if (r.ph == 4) {
            if (lt - r.wstart == 30) {
                d.toggleRtb(r.fp);
                check("fly " + r.name + ": retour base engage", d.state() == Drone.RTB, pos(d));
            }
            if (lt - r.wstart > 30 && d.state() == Drone.LANDED) {
                double dd = Math.hypot(d.getX() - r.fp.getX(), d.getZ() - r.fp.getZ());
                check("fly " + r.name + ": retour + atterrissage pres du pilote", dd < (wing ? 25 : 6), String.format("dist=%.1f ", dd) + pos(d));
                r.done = true;
            }
            if (lt - r.wstart > 1500) {
                check("fly " + r.name + ": retour + atterrissage", false, pos(d));
                r.done = true;
            }
        }
    }
}
