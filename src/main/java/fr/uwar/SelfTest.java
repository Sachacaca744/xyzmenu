package fr.uwar;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
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

/** Tests automatiques cote serveur (actifs uniquement avec -Duwar.selftest). Non inclus dans le jar livre. */
public class SelfTest {
    static int pass, fail, t;
    static boolean running, auto;
    static ServerLevel lv;
    static MinecraftServer server;
    static final List<Run> runs = new ArrayList<>();

    static class Run {
        String name, mode;
        Kind kind;
        Aircraft a;
        ServerPlayer fp;
        Vec3 start;
        int t0, ph, mark;
        boolean done;
        Entity c1, c2, c3, c4;
        double d0, d1;
        float f0;
        int i0;
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(SelfTest.class);
        auto = "auto".equals(System.getProperty("uwar.selftest"));
        System.out.println("SELFTEST: actif (auto=" + auto + ")");
        if (System.getProperty("uwar.clienttest") != null) {
            try {
                Class.forName("fr.uwar.client.ClientTest").getMethod("init").invoke(null);
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
        e.getDispatcher().register(Commands.literal("uwar_selftest").requires(s -> s.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.greedyString()).executes(c -> cmd(c.getSource(), StringArgumentType.getString(c, "name")))));
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent e) {
        server = e.getServer();
        if (auto) start(server.overworld());
    }

    static int cmd(CommandSourceStack src, String n) {
        String[] a = n.split(" ");
        try {
            ServerLevel sl = src.getLevel();
            switch (a[0]) {
                case "models" -> {
                    ServerPlayer sp = src.getPlayerOrException();
                    for (int i = 0; i < 3; i++) {
                        Kind k = Kind.values()[i];
                        Aircraft ac = new Aircraft(Reg.AIRCRAFTS.get(k).get(), sl, k);
                        ac.moveTo(i == 0 ? -12 : i == 1 ? 14 : 34, -60, 15, 180, 0);
                        ac.applyStack(Aircraft.makeStack(k, true));
                        ac.yRotO = 180;
                        sl.addFreshEntity(ac);
                    }
                }
                case "ride" -> {
                    ServerPlayer sp = src.getPlayerOrException();
                    Kind k = Kind.valueOf(a[1].toUpperCase());
                    Aircraft best = null;
                    for (Entity en : sl.getAllEntities())
                        if (en instanceof Aircraft ac && ac.kind == k && (best == null || ac.distanceTo(sp) < best.distanceTo(sp))) best = ac;
                    if (best != null) sp.startRiding(best, true);
                }
                case "fill" -> {
                    if (src.getPlayerOrException().getVehicle() instanceof Aircraft ac)
                        for (W w : ac.loaded()) ac.setAmmo(w, ac.cap(w));
                }
                case "nuke" -> {
                    double sc = a.length > 1 ? Double.parseDouble(a[1]) : 0.05;
                    Nuke.detonate(sl, new Vec3(0, -60, 0), null, sc);
                }
                case "unride" -> src.getPlayerOrException().stopRiding();
                default -> src.sendFailure(Component.literal("?"));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return 1;
    }

    // ------------------------------------------------------------ scenarios
    static Run mk(String name, Kind k, double x, double z, String mode) {
        Run r = new Run();
        r.name = name;
        r.kind = k;
        r.mode = mode;
        lv.getChunkSource().getChunk((int) x >> 4, (int) z >> 4, net.minecraft.world.level.chunk.ChunkStatus.FULL, true);
        BlockPos bp = lv.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos((int) x, 0, (int) z));
        r.start = new Vec3(x + 0.5, bp.getY(), z + 0.5);
        r.fp = FakePlayerFactory.get(lv, new GameProfile(UUID.randomUUID(), "[T" + name + "]"));
        r.fp.setPos(r.start.x, r.start.y, r.start.z - 3);
        Aircraft a = new Aircraft(Reg.AIRCRAFTS.get(k).get(), lv, k);
        a.moveTo(r.start.x, r.start.y, r.start.z, 0, 0);
        a.yRotO = 0;
        a.applyStack(Aircraft.makeStack(k, true));
        a.testPilot = r.fp;
        a.aimYaw = 0;
        lv.addFreshEntity(a);
        r.a = a;
        r.t0 = t;
        return r;
    }

    static void board(Run r) {
        r.fp.startRiding(r.a, true);
    }

    /** Isole les tests de verrouillage : supprime les vaches des autres scenarios. */
    static void clearCows() {
        for (Entity e : new ArrayList<>(lv.getEntitiesOfClass(Cow.class, new net.minecraft.world.phys.AABB(-400, -100, -400, 400, 300, 400)))) e.discard();
    }

    static Cow cow(double x, double y, double z) {
        Cow c = EntityType.COW.create(lv);
        c.moveTo(x, y, z, 0, 0);
        c.setNoAi(true);
        c.setPersistenceRequired();
        lv.addFreshEntity(c);
        return c;
    }

    static void start(ServerLevel sl) {
        if (running) return;
        lv = sl;
        for (int cx = -16; cx <= 12; cx++) for (int cz = -8; cz <= 12; cz++) lv.setChunkForced(cx, cz, true);
        lv.getChunkSource().getChunk(0, 0, net.minecraft.world.level.chunk.ChunkStatus.FULL, true);
        running = true;
        t = 0;
        pass = fail = 0;
        runs.clear();
        String[][] sc = {{"cruise", "JET"}, {"ballistic", "JET"}, {"heli_fly", "HELI"}, {"jet_fly", "JET"}, {"cannon", "HELI"}, {"rocket", "HELI"}, {"missile", "HELI"}, {"bomb", "JET"},
                {"empty", "HELI"}, {"supply", "HELI"}, {"fuel_out", "HELI"}, {"destroy", "HELI"}, {"crash", "JET"}, {"persist", "JET"},
                {"pickup", "HELI"}, {"place", "JET"}, {"jet_weapons", "JET"}, {"mini_fly", "MINI"}, {"mini_arms", "MINI"}, {"flak_far", "HELI"}, {"loadout", "HELI"}, {"ir", "HELI"}, {"flak", "HELI"}, {"buster", "HELI"}, {"bay", "JET"}, {"nuke", "JET"}};
        java.util.Map<String, Integer> delay = java.util.Map.of("missile", 100, "ir", 300, "flak", 500, "buster", 700, "flak_far", 1300, "nuke", 1600);
        int i = 0;
        for (String[] s : sc) {
            boolean far = s[0].equals("missile") || s[0].equals("flak_far") || s[0].equals("nuke");
            Run r = far ? mk(s[0], Kind.valueOf(s[1]), s[0].equals("nuke") ? -100 : s[0].equals("missile") ? -250 : -170, s[0].equals("nuke") ? 100 : -100, s[0]) : mk(s[0], Kind.valueOf(s[1]), -90 + (i % 8) * 24, (i / 8) * 60 - 30, s[0]);
            r.t0 = t + delay.getOrDefault(s[0], 0);
            runs.add(r);
            i++;
        }
        for (Run r : runs) {
            if (r.mode.equals("pickup") || r.mode.equals("place") || r.mode.equals("persist") || r.mode.equals("supply")) {
                r.a.testPilot = null;
                if (r.mode.equals("supply")) r.fp.setPos(r.start.x, r.start.y, r.start.z - 3);
                continue;
            }
            board(r);
            check("embarquement " + r.name, r.a.getControllingPassenger() == r.fp, "passagers=" + r.a.getPassengers().size());
        }
        System.out.println("SELFTEST: demarrage, " + runs.size() + " scenarios");
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
                if (r.a != null && r.a.testPilot != null && !r.a.isRemoved())
                    r.a.setInput(r.a.thrust, r.a.inBoost, r.a.inFire, r.a.inU > 0, r.a.aimYaw, r.a.aimPitch, 0f);
                if (t - r.t0 >= 1) step(r, t - r.t0);
            } catch (Throwable ex) {
                ex.printStackTrace();
                check("exception " + r.name, false, ex.toString());
                r.done = true;
            }
        }
        if (t > 3800 && !all) {
            for (Run r : runs) if (!r.done) check("timeout " + r.name, false, "ph=" + r.ph);
            all = true;
        }
        if (all) {
            running = false;
            System.out.println("SELFTEST RESULT: " + pass + " PASS, " + fail + " FAIL");
            if (auto && server != null) server.halt(false);
        }
    }

    static String st(Aircraft a) {
        return String.format("pos=(%.1f,%.1f,%.1f) yaw=%.0f pitch=%.0f spd=%.2f thr=%.2f fuel=%.0f hp=%.0f", a.getX(), a.getY(), a.getZ(), a.getYRot(), a.getXRot(), a.speedF, a.thrust, a.fuel(), a.hp());
    }

    static void fin(Run r) {
        r.done = true;
    }

    static void step(Run r, int lt) {
        Aircraft a = r.a;
        switch (r.mode) {
            case "heli_fly" -> {
                switch (r.ph) {
                    case 0 -> {
                        if (lt == 1) {
                            r.d0 = a.getY();
                            r.f0 = a.fuel();
                            a.inU = 1;
                        }
                        if (lt == 80) {
                            check("heli monte", a.getY() > r.d0 + 5, st(a));
                            a.inU = 0;
                            r.ph = 1;
                            r.mark = lt;
                        }
                    }
                    case 1 -> {
                        if (lt == r.mark + 20) {
                            r.d1 = a.getY();
                            a.inF = 1;
                        }
                        if (lt == r.mark + 30) check("heli stationnaire", Math.abs(a.getY() - r.d1) < 1.0, st(a) + " y0=" + r.d1);
                        if (lt == r.mark + 80) {
                            check("heli avance (+z)", a.getZ() > r.start.z + 12, st(a));
                            check("heli consomme du carburant", a.fuel() < r.f0 - 1, "fuel " + r.f0 + " -> " + a.fuel());
                            check("heli moteur synchro", a.engineOn(), "engine=" + a.engineOn());
                            a.inF = 0;
                            a.inS = 1;
                            r.d0 = a.getX();
                            r.ph = 2;
                            r.mark = lt;
                        }
                    }
                    case 2 -> {
                        if (lt == r.mark + 40) {
                            check("heli lateral (+x = gauche)", a.getX() > r.d0 + 3, st(a));
                            a.inS = 0;
                            a.aimYaw = 90;
                            r.ph = 3;
                            r.mark = lt;
                        }
                    }
                    case 3 -> {
                        if (lt == r.mark + 40) {
                            check("heli tourne (cap 90)", Math.abs(a.getYRot() - 90) < 6, st(a));
                            a.inU = -1;
                            r.ph = 4;
                            r.mark = lt;
                        }
                    }
                    case 4 -> {
                        if (a.grounded() && a.getDeltaMovement().y > -0.01) {
                            check("heli se pose sans dommage", a.hp() >= a.kind.maxHp - 0.1f, st(a));
                            fin(r);
                        } else if (lt > r.mark + 400) {
                            check("heli se pose", false, st(a));
                            fin(r);
                        }
                    }
                }
            }
            case "jet_fly" -> {
                switch (r.ph) {
                    case 0 -> {
                        if (lt == 1) {
                            a.inF = 1;
                            a.aimPitch = -20;
                            r.d0 = a.getY();
                        }
                        if (lt == 40) check("jet roule au sol", a.speedF > 0.5 && a.getY() < r.d0 + 2, st(a));
                        if (a.getY() > r.d0 + 6) {
                            check("jet decolle", true, st(a));
                            r.ph = 1;
                            r.mark = lt;
                            r.f0 = a.fuel();
                        } else if (lt > 400) {
                            check("jet decolle", false, st(a));
                            fin(r);
                        }
                    }
                    case 1 -> {
                        a.aimYaw += 4;
                        a.aimPitch = -4;
                        if (lt == r.mark + 80) {
                            check("jet vitesse de croisiere", a.speedF > 0.9, st(a));
                            check("jet carburant consomme", a.fuel() < r.f0, "fuel " + r.f0 + " -> " + a.fuel());
                            check("jet reste haut", a.getY() > r.d0 + 6, st(a));
                            r.d1 = a.speedF;
                            a.inBoost = true;
                            r.ph = 2;
                            r.mark = lt;
                        }
                    }
                    case 2 -> {
                        a.aimYaw += 4;
                        a.aimPitch = -2;
                        a.inBoost = true;
                        if (lt == r.mark + 40) {
                            check("postcombustion", a.speedF > r.d1 + 0.2 && a.boosting(), st(a) + " avant=" + r.d1 + " boost=" + a.boosting());
                            a.inBoost = false;
                            a.inF = -1;
                            a.inBrake = true;
                            r.d1 = a.speedF;
                            r.ph = 3;
                            r.mark = lt;
                        }
                    }
                    case 3 -> {
                        a.aimYaw += 4;
                        a.aimPitch = 0;
                        a.inF = -1;
                        if (lt == r.mark + 60) {
                            check("jet ralentit (gaz coupes)", a.speedF < r.d1 - 0.3, st(a) + " avant=" + r.d1);
                            fin(r);
                        }
                    }
                }
            }
            case "cannon" -> {
                switch (r.ph) {
                    case 0 -> {
                        if (lt == 1) {
                            r.c1 = cow(r.start.x, r.start.y, r.start.z + 30);
                            r.i0 = a.ammo(W.CANNON);
                            a.aimPitch = 0;
                            a.inFire = true;
                        }
                        if (lt == 40) {
                            a.inFire = false;
                            int used = r.i0 - a.ammo(W.CANNON);
                            check("canon consomme des munitions", used >= 10 && used <= 25, "utilise=" + used);
                            LivingEntity le = (LivingEntity) r.c1;
                            check("canon touche la cible", !le.isAlive() || le.getHealth() < le.getMaxHealth(), "pv=" + le.getHealth());
                            r.i0 = a.ammo(W.CANNON);
                            r.ph = 1;
                            r.mark = lt;
                        }
                    }
                    case 1 -> {
                        if (lt == r.mark + 20) {
                            check("canon cesse de tirer", a.ammo(W.CANNON) == r.i0, "mun=" + a.ammo(W.CANNON));
                            fin(r);
                        }
                    }
                }
            }
            case "rocket" -> {
                if (lt == 1) {
                    r.c1 = cow(r.start.x + 1.7, r.start.y, r.start.z + 30);
                    a.nextWeapon(1);
                    a.aimPitch = -1.0f;
                    a.inFire = true;
                    r.i0 = a.ammo(W.ROCKET);
                }
                if (lt == 8) a.inFire = false;
                if (lt == 70) {
                    check("roquette selectionnee", a.selected() == W.ROCKET, "arme=" + a.selected());
                    check("roquettes consommees", r.i0 - a.ammo(W.ROCKET) >= 1, r.i0 + " -> " + a.ammo(W.ROCKET));
                    LivingEntity le = (LivingEntity) r.c1;
                    check("roquette detruit la cible", !le.isAlive(), "pv=" + le.getHealth());
                    check("roquette standard : ne casse pas les blocs", !lv.getBlockState(BlockPos.containing(r.start.x + 1.7, r.start.y - 1, r.start.z + 30)).isAir(), "");
                    boolean left = false;
                    for (Entity en : lv.getAllEntities()) if (en instanceof Shell sh && sh.weapon() == W.ROCKET) left = true;
                    check("plus de projectile apres explosion", !left, "");
                    fin(r);
                }
            }
            case "missile" -> {
                if (lt == 1) {
                    clearCows();
                    r.c1 = cow(r.start.x + 8, r.start.y, r.start.z + 40);
                    a.nextWeapon(2);
                    a.aimPitch = 0;
                    r.i0 = a.ammo(W.MISSILE);
                }
                if (lt == 20) {
                    for (Entity en : lv.getEntities(a, a.getBoundingBox().inflate(150))) System.out.println("DBG missile near id=" + en.getId() + " " + en.getType() + " at " + en.position() + " me=" + a.position());
                    check("missile verrouille la cible", a.lockId() == r.c1.getId(), "lock=" + a.lockId() + " cible=" + r.c1.getId());
                    a.inFire = true;
                }
                if (lt == 24) a.inFire = false;
                if (lt == 110) {
                    check("missile consomme 1", a.ammo(W.MISSILE) == r.i0 - 1, r.i0 + " -> " + a.ammo(W.MISSILE));
                    check("missile guide detruit la cible hors axe", !((LivingEntity) r.c1).isAlive(), "pv=" + ((LivingEntity) r.c1).getHealth());
                    fin(r);
                }
            }
            case "bomb" -> {
                if (lt == 1) {
                    r.c1 = cow(r.start.x + 3, r.start.y, r.start.z);
                    a.nextWeapon(3);
                    r.i0 = a.ammo(W.BOMB);
                    a.inFire = true;
                }
                if (lt == 3) a.inFire = false;
                if (lt == 50) {
                    check("bombe selectionnee", a.selected() == W.BOMB, "arme=" + a.selected());
                    check("bombe consommee", a.ammo(W.BOMB) == r.i0 - 1, r.i0 + " -> " + a.ammo(W.BOMB));
                    check("bombe detruit la cible", !((LivingEntity) r.c1).isAlive(), "pv=" + ((LivingEntity) r.c1).getHealth());
                    check("bombe standard : ne casse pas les blocs", !lv.getBlockState(BlockPos.containing(r.start.x + 3, r.start.y - 1, r.start.z)).isAir(), "");
                    check("la bombe n'abime pas son propre appareil", a.hp() >= a.kind.maxHp - 0.1f, "hp=" + a.hp());
                    fin(r);
                }
            }
            case "empty" -> {
                if (lt == 1) {
                    r.c1 = cow(r.start.x, r.start.y, r.start.z + 20);
                    a.setAmmo(W.CANNON, 0);
                    a.inFire = true;
                }
                if (lt == 40) {
                    check("sans munitions : pas de tir", a.ammo(W.CANNON) == 0 && a.shots == 0 && ((LivingEntity) r.c1).getHealth() == ((LivingEntity) r.c1).getMaxHealth(), "tirs=" + a.shots);
                    fin(r);
                }
            }
            case "supply" -> {
                if (lt == 1) {
                    a.setAmmo(W.CANNON, 0);
                    a.setAmmo(W.ROCKET, 0);
                    a.setFuel(100);
                    a.setHp(50);
                    r.fp.getInventory().add(new ItemStack(Reg.AMMO.get(W.CANNON).get(), 2));
                    r.fp.getInventory().add(new ItemStack(Reg.FUEL_CAN.get(), 1));
                    r.fp.getInventory().add(new ItemStack(Reg.REPAIR_KIT.get(), 1));
                    a.reloadFrom(r.fp);
                    check("recharge canon depuis l'inventaire", a.ammo(W.CANNON) == 150, "mun=" + a.ammo(W.CANNON));
                    check("recharge carburant", Math.abs(a.fuel() - 400) < 1, "fuel=" + a.fuel());
                    check("reparation", Math.abs(a.hp() - 95) < 1, "hp=" + a.hp());
                    int left = 0;
                    for (int i = 0; i < r.fp.getInventory().getContainerSize(); i++) left += r.fp.getInventory().getItem(i).getCount();
                    check("objets consommes", left == 0, "restant=" + left);
                    ItemStack pod = new ItemStack(Reg.AMMO.get(W.ROCKET).get(), 3);
                    a.supply(r.fp, pod);
                    check("nacelle roquettes +6", a.ammo(W.ROCKET) == 6 && pod.getCount() == 2, "roq=" + a.ammo(W.ROCKET));
                    a.setFuel(a.kind.fuelCap);
                    ItemStack can = new ItemStack(Reg.FUEL_CAN.get(), 2);
                    a.supply(r.fp, can);
                    check("reservoir plein : bidon non consomme", can.getCount() == 2, "n=" + can.getCount());
                    ItemStack bomb = new ItemStack(Reg.AMMO.get(W.BOMB).get(), 2);
                    a.supply(r.fp, bomb);
                    check("hélico ne peut pas porter de bombes", bomb.getCount() == 2 && a.ammo(W.BOMB) == 0, "n=" + bomb.getCount());
                    fin(r);
                }
            }
            case "fuel_out" -> {
                if (lt == 1) {
                    a.moveTo(a.getX(), a.getY() + 20, a.getZ());
                    a.setFuel(3);
                    a.inU = 1;
                }
                if (lt == 60) {
                    check("panne seche : carburant 0 et moteur coupe", a.fuel() <= 0 && !a.engineOn(), st(a) + " engine=" + a.engineOn());
                    check("panne seche : l'helico descend", a.getDeltaMovement().y < -0.05 || a.grounded(), st(a));
                }
                if (lt > 60 && a.grounded()) {
                    check("atterrissage force sans destruction", !a.isRemoved() && a.hp() > 0, st(a));
                    fin(r);
                } else if (lt > 500) {
                    check("atterrissage force", false, st(a));
                    fin(r);
                }
            }
            case "destroy" -> {
                if (lt == 3) {
                    a.hurt(lv.damageSources().generic(), 5000f);
                    check("appareil detruit", a.isRemoved(), "");
                    check("pilote ejecte", r.fp.getVehicle() == null, "");
                    fin(r);
                }
            }
            case "crash" -> {
                if (lt == 1) {
                    a.moveTo(a.getX(), a.getY() + 40, a.getZ(), 0, 60);
                    a.setXRot(60);
                    a.speedF = 1.3f;
                    a.thrust = 0.8f;
                    a.aimPitch = 60;
                    a.inF = 0;
                }
                if (a.isRemoved() || a.hp() < a.kind.maxHp - 5) {
                    check("collision au sol endommage l'avion", true, "hp=" + (a.isRemoved() ? "detruit" : "" + a.hp()));
                    fin(r);
                } else if (lt > 300) {
                    check("collision au sol endommage l'avion", false, st(a));
                    fin(r);
                }
            }
            case "persist" -> {
                if (lt == 1) {
                    a.setFuel(777);
                    a.setHp(66);
                    a.setAmmo(W.CANNON, 321);
                    a.setAmmo(W.MISSILE, 3);
                    CompoundTag tag = new CompoundTag();
                    a.saveWithoutId(tag);
                    Aircraft b = new Aircraft(Reg.AIRCRAFTS.get(r.kind).get(), lv, r.kind);
                    b.load(tag);
                    check("sauvegarde NBT : carburant/PV/munitions", Math.abs(b.fuel() - 777) < 0.5 && Math.abs(b.hp() - 66) < 0.5 && b.ammo(W.CANNON) == 321 && b.ammo(W.MISSILE) == 3,
                            "fuel=" + b.fuel() + " hp=" + b.hp() + " mun=" + b.ammo(W.CANNON) + "/" + b.ammo(W.MISSILE));
                    ItemStack s = a.toStack();
                    Aircraft c = new Aircraft(Reg.AIRCRAFTS.get(r.kind).get(), lv, r.kind);
                    c.applyStack(s);
                    check("objet -> appareil conserve l'etat", Math.abs(c.fuel() - 777) < 0.5 && c.ammo(W.CANNON) == 321 && Math.abs(c.hp() - 66) < 0.5, "fuel=" + c.fuel());
                    fin(r);
                }
            }
            case "pickup" -> {
                if (lt == 1) {
                    a.setFuel(555);
                    r.fp.setShiftKeyDown(true);
                    var res = a.interact(r.fp, InteractionHand.MAIN_HAND);
                    r.fp.setShiftKeyDown(false);
                    check("maj + clic droit ramasse l'appareil", a.isRemoved() && res.consumesAction(), "res=" + res);
                    boolean found = false;
                    for (Entity en : lv.getEntities((Entity) null, a.getBoundingBox().inflate(3), x -> x instanceof ItemEntity)) {
                        ItemStack s = ((ItemEntity) en).getItem();
                        if (s.getItem() == Reg.AIRCRAFT_ITEMS.get(r.kind).get() && s.getTagElement("ac") != null && Math.abs(s.getTagElement("ac").getFloat("Fuel") - 555) < 1) found = true;
                    }
                    check("l'objet ramasse garde le carburant", found, "");
                    fin(r);
                }
            }
            case "place" -> {
                if (lt == 1) {
                    a.discard();
                    ItemStack s = new ItemStack(Reg.AIRCRAFT_ITEMS.get(r.kind).get());
                    r.fp.setItemInHand(InteractionHand.MAIN_HAND, s);
                    BlockPos ground = BlockPos.containing(r.start).below();
                    int before = lv.getEntitiesOfClass(Aircraft.class, new net.minecraft.world.phys.AABB(r.start, r.start).inflate(10)).size();
                    var res = s.getItem().useOn(new UseOnContext(lv, r.fp, InteractionHand.MAIN_HAND, s,
                            new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false)));
                    var list = lv.getEntitiesOfClass(Aircraft.class, new net.minecraft.world.phys.AABB(r.start, r.start).inflate(10));
                    check("l'objet pose un appareil", list.size() == 1, "res=" + res + " n=" + list.size());
                    Aircraft n = null;
                    for (Aircraft x : list) n = x;
                    if (n != null) {
                        check("appareil pose : 40% carburant + 150 obus", Math.abs(n.fuel() - n.kind.fuelCap * 0.4f) < 1 && n.ammo(W.CANNON) == 150, "fuel=" + n.fuel() + " mun=" + n.ammo(W.CANNON));
                        n.discard();
                    }
                    fin(r);
                }
            }
            case "mini_fly" -> {
                switch (r.ph) {
                    case 0 -> {
                        if (lt == 1) {
                            a.inF = 1;
                            a.aimPitch = -20;
                            r.d0 = a.getY();
                        }
                        if (a.getY() > r.d0 + 5) {
                            check("mini chasseur decolle", true, st(a));
                            r.ph = 1;
                            r.mark = lt;
                        } else if (lt > 300) {
                            check("mini chasseur decolle", false, st(a));
                            fin(r);
                        }
                    }
                    case 1 -> {
                        a.aimPitch = -3;
                        if (lt == r.mark + 40) {
                            r.d1 = a.getYRot();
                            a.aimYaw = net.minecraft.util.Mth.wrapDegrees(a.getYRot() + 90);
                            r.f0 = a.getYRot();
                            r.ph = 2;
                            r.mark = lt;
                        }
                    }
                    case 2 -> {
                        a.aimPitch = -3;
                        if (lt == r.mark + 17) {
                            float turned = Math.abs(net.minecraft.util.Mth.wrapDegrees(a.getYRot() - r.f0));
                            check("mini chasseur : virage de 90 degres en 17 ticks (agile)", turned >= 85, "tourne=" + turned + " " + st(a));
                            a.aimYaw = a.getYRot();
                            check("mini chasseur : taille reduite", a.kind.sc < 0.7f && a.getBbWidth() < 2.0f, "bb=" + a.getBbWidth());
                            fin(r);
                        }
                    }
                }
            }
            case "mini_arms" -> {
                if (lt == 1) {
                    r.c1 = cow(r.start.x, r.start.y, r.start.z + 30);
                    a.aimPitch = 0;
                    a.inFire = true;
                    r.i0 = a.ammo(W.CANNON);
                    check("mini : config par defaut roquettes + missile", a.has(W.ROCKET) && a.has(W.MISSILE) && a.slotsUsed() == 2, "lo=" + a.loadout());
                }
                if (lt == 30) {
                    a.inFire = false;
                    check("mini : canon touche la cible", r.i0 - a.ammo(W.CANNON) >= 8 && (!r.c1.isAlive() || ((LivingEntity) r.c1).getHealth() < ((LivingEntity) r.c1).getMaxHealth()), "mun=" + a.ammo(W.CANNON));
                    String e1 = a.setLoadout(r.fp, W.CANNON.bit() | W.IR.bit() | W.FLAK.bit() | W.ROCKET.bit());
                    check("mini : 2 emplacements seulement", e1 != null, "err=" + e1);
                    String e2 = a.setLoadout(r.fp, W.CANNON.bit() | W.CRUISE.bit());
                    check("mini : pas de missile de croisiere", e2 != null, "err=" + e2);
                    String e3 = a.setLoadout(r.fp, W.CANNON.bit() | W.IR.bit() | W.FLAK.bit());
                    check("mini : IR + fragmentation acceptes", e3 == null && a.has(W.IR) && a.has(W.FLAK), "err=" + e3);
                    fin(r);
                }
            }
            case "flak_far" -> {
                if (lt == 1) {
                    a.setLoadout(r.fp, W.CANNON.bit() | W.FLAK.bit());
                    a.setAmmo(W.FLAK, 4);
                    clearCows();
                    r.c1 = cow(r.start.x, r.start.y, r.start.z + 196);
                    a.nextWeapon(1);
                    a.aimPitch = 0;
                    a.inFire = true;
                    r.i0 = a.ammo(W.FLAK);
                    check("fragmentation : portee d'explosion 200 m", Shell.FLAK_RANGE == 200, "" + Shell.FLAK_RANGE);
                }
                if (lt == 3) a.inFire = false;
                if (lt == 50) {
                    boolean still = false;
                    for (Entity en : lv.getAllEntities()) if (en instanceof Shell sh && sh.weapon() == W.FLAK) still = true;
                    check("fragmentation : ne explose PAS a 50 blocs (toujours en vol)", still && ((LivingEntity) r.c1).isAlive(), "");
                }
                if (lt == 200) {
                    check("fragmentation : explose a ~200 blocs et detruit la cible", !((LivingEntity) r.c1).isAlive() && a.ammo(W.FLAK) == r.i0 - 1, "pv=" + ((LivingEntity) r.c1).getHealth());
                    fin(r);
                }
            }
            case "loadout" -> {
                if (lt == 1) {
                    a.setAmmo(W.ROCKET, 38);
                    int mask = W.CANNON.bit() | W.IR.bit() | W.FLAK.bit() | W.BUSTER.bit();
                    String err = a.setLoadout(r.fp, mask);
                    check("config armement acceptee au sol", err == null && a.has(W.IR) && a.has(W.FLAK) && a.has(W.BUSTER) && !a.has(W.ROCKET) && !a.has(W.MISSILE), "err=" + err + " lo=" + a.loadout());
                    int items = 0;
                    for (int i = 0; i < r.fp.getInventory().getContainerSize(); i++) {
                        ItemStack st = r.fp.getInventory().getItem(i);
                        if (st.getItem() == Reg.AMMO.get(W.ROCKET).get()) items += st.getCount();
                    }
                    check("arme retiree : munitions rendues en objets", items == 6 && a.ammo(W.ROCKET) == 0, "objets=" + items);
                    String e2 = a.setLoadout(r.fp, mask | W.MISSILE.bit());
                    check("trop d'armes refuse", e2 != null && !a.has(W.MISSILE), "err=" + e2);
                    String e3 = a.setLoadout(r.fp, W.CANNON.bit() | W.CRUISE.bit());
                    check("arme impossible sur l'helico refusee", e3 != null && !a.has(W.CRUISE), "err=" + e3);
                    a.nextWeapon(1);
                    W w1 = a.selected();
                    a.nextWeapon(1);
                    W w2 = a.selected();
                    a.nextWeapon(1);
                    W w3 = a.selected();
                    a.nextWeapon(1);
                    check("cycle limite aux armes installees", w1 == W.IR && w2 == W.FLAK && w3 == W.BUSTER && a.selected() == W.CANNON, w1 + " " + w2 + " " + w3 + " " + a.selected());
                    ItemStack pod = new ItemStack(Reg.AMMO.get(W.ROCKET).get(), 2);
                    a.supply(r.fp, pod);
                    check("munition d'une arme non installee refusee", pod.getCount() == 2 && a.ammo(W.ROCKET) == 0, "n=" + pod.getCount());
                    ItemStack s2 = a.toStack();
                    Aircraft c = new Aircraft(Reg.AIRCRAFTS.get(r.kind).get(), lv, r.kind);
                    c.applyStack(s2);
                    check("la config suit l'objet (ramassage)", c.loadout() == a.loadout(), c.loadout() + " vs " + a.loadout());
                    a.moveTo(a.getX(), a.getY() + 10, a.getZ());
                    a.setDeltaMovement(0, 0, 0);
                    String e4 = a.setLoadout(r.fp, W.CANNON.bit());
                    check("config refusee en vol", e4 != null && a.has(W.IR), "err=" + e4);
                    fin(r);
                }
            }
            case "ir", "flak", "buster" -> {
                W w = r.mode.equals("ir") ? W.IR : r.mode.equals("flak") ? W.FLAK : W.BUSTER;
                if (lt == 1) {
                    String err = a.setLoadout(r.fp, W.CANNON.bit() | w.bit());
                    a.setAmmo(w, 4);
                    clearCows();
                    double dz = w == W.FLAK ? 45 : w == W.BUSTER ? 30 : 60;
                    r.c1 = cow(r.start.x + (w == W.IR ? 4 : 0), r.start.y, r.start.z + dz);
                    r.d0 = r.c1.getX();
                    r.d1 = r.c1.getZ();
                    a.nextWeapon(1);
                    r.i0 = a.ammo(w);
                    check(w.name() + " installe et selectionne", err == null && a.selected() == w, "err=" + err + " sel=" + a.selected());
                }
                if (lt == 20) {
                    check(w.name() + " verrouille la cible", a.lockId() == r.c1.getId(), "lock=" + a.lockId());
                    a.inFire = true;
                }
                if (lt == 22) a.inFire = false;
                if (lt == 130) {
                    check(w.name() + " consomme 1", a.ammo(w) == r.i0 - 1, r.i0 + " -> " + a.ammo(w));
                    check(w.name() + " detruit la cible", !((LivingEntity) r.c1).isAlive(), "pv=" + ((LivingEntity) r.c1).getHealth());
                    if (w == W.BUSTER) {
                        boolean air = lv.getBlockState(BlockPos.containing(r.d0, r.start.y - 1, r.d1)).isAir();
                        check("anti-bunker : arrache les blocs a l'impact", air, "bloc=" + lv.getBlockState(BlockPos.containing(r.d0, r.start.y - 1, r.d1)));
                    }
                    fin(r);
                }
            }
            case "cruise", "ballistic" -> {
                W w = r.mode.equals("cruise") ? W.CRUISE : W.BALLISTIC;
                if (lt == 1) {
                    String err = a.setLoadout(r.fp, W.CANNON.bit() | w.bit());
                    a.setAmmo(w, 2);
                    clearCows();
                    r.c1 = cow(r.start.x, r.start.y, r.start.z + 170);
                    a.nextWeapon(1);
                    a.aimPitch = 0;
                    r.i0 = a.ammo(w);
                    check(w.name() + " installe et selectionne", err == null && a.selected() == w, "err=" + err);
                    Vec3 pt = a.aimPoint(lv, a.position().add(0, a.kind.h * 0.55, 0), a.aimDir());
                    check(w.name() + " : point vise au sol a 170 blocs", Math.abs(pt.z - (r.start.z + 170)) < 3 && Math.abs(pt.y - r.start.y) < 2, "pt=" + pt);
                    a.inFire = true;
                }
                if (lt == 3) a.inFire = false;
                if (lt == 12) {
                    boolean found = false;
                    for (Entity en : lv.getAllEntities()) if (en instanceof Shell sh && sh.weapon() == w) found = true;
                    check(w.name() + " en vol", found, "");
                }
                if (lt == 330) {
                    check(w.name() + " consomme 1", a.ammo(w) == r.i0 - 1, r.i0 + " -> " + a.ammo(w));
                    check(w.name() + " detruit la cible a 170 blocs", !((LivingEntity) r.c1).isAlive(), "pv=" + ((LivingEntity) r.c1).getHealth());
                    boolean air = lv.getBlockState(BlockPos.containing(r.start.x, r.start.y - 1, r.start.z + 170)).isAir();
                    check(w.name() + " : cratere (blocs detruits)", air, "bloc=" + lv.getBlockState(BlockPos.containing(r.start.x, r.start.y - 1, r.start.z + 170)));
                    fin(r);
                }
            }
            case "nuke" -> {
                double x0 = r.start.x, z0 = r.start.z;
                int y = (int) r.start.y;
                if (lt == 1) {
                    Nuke.scaleOverride = 0.02;
                    clearCows();
                    String err = a.setLoadout(r.fp, W.CANNON.bit() | W.NUKE.bit());
                    a.setAmmo(W.NUKE, 1);
                    a.nextWeapon(1);
                    check("NUKE installee et selectionnee (chasseur)", err == null && a.selected() == W.NUKE, "err=" + err);
                    check("NUKE refusee sur le mini chasseur", !Kind.MINI.canCarry(W.NUKE) && Kind.HELI.canCarry(W.NUKE), "");
                    r.c1 = cow(x0 + 5, y, z0);
                    r.c2 = cow(x0 + 60, y, z0);
                    r.c3 = cow(x0 + 150, y, z0);
                    r.c4 = cow(x0 + 260, y, z0);
                    var pl = net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState();
                    var lf = net.minecraft.world.level.block.Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
                    lv.setBlock(new BlockPos((int) x0 + 25, y, (int) z0), pl, 2);
                    lv.setBlock(new BlockPos((int) x0 + 70, y, (int) z0), pl, 2);
                    lv.setBlock(new BlockPos((int) x0 + 70, y + 3, (int) z0), lf, 2);
                    lv.setBlock(new BlockPos((int) x0 + 150, y, (int) z0), pl, 2);
                    lv.setBlock(new BlockPos((int) x0 + 150, y + 3, (int) z0), lf, 2);
                    lv.setBlock(new BlockPos((int) x0 + 230, y + 3, (int) z0), lf, 2);
                }
                if (lt == 2) a.inFire = true;
                if (lt == 4) a.inFire = false;
                if (lt == 40) {
                    check("NUKE : munition consommee", a.ammo(W.NUKE) == 0, "" + a.ammo(W.NUKE));
                    check("NUKE : explosion enregistree dans le monde", !Nuke.data(lv).sites.isEmpty(), "");
                    check("NUKE : vache dans le cratere morte", !r.c1.isAlive(), "");
                    check("NUKE : vache zone souffle morte", !r.c2.isAlive(), "");
                    check("NUKE : vache zone 5-10 km morte", !r.c3.isAlive(), "");
                    check("NUKE : vache hors zone vivante", r.c4.isAlive(), "");
                    check("NUKE : le pilote est mort (souffle)", !r.fp.isAlive(), "");
                    check("NUKE : l'avion est detruit", a.isRemoved() || a.hp() <= 0, "hp=" + a.hp());
                }
                if (lt == 300) {
                    r.c1 = EntityType.VILLAGER.create(lv);
                    r.c1.moveTo(x0 + 25, y, z0, 0, 0);
                    ((LivingEntity) r.c1).setHealth(20f);
                    lv.addFreshEntity(r.c1);
                    r.c2 = cow(x0 + 150, y, z0);
                    r.c3 = cow(x0 + 260, y, z0);
                }
                if (lt == 380) {
                    check("NUKE : poison permanent (zone 0,5-2 km)", ((LivingEntity) r.c1).hasEffect(net.minecraft.world.effect.MobEffects.POISON), "");
                    check("NUKE : nouveaux animaux morts en zone 5-10 km", !r.c2.isAlive(), "");
                    check("NUKE : animaux hors zone survivent", r.c3.isAlive(), "");
                }
                if (lt == 1000) {
                    check("NUKE : traitement des blocs termine", !Nuke.busy(), "");
                    check("NUKE : cratere", lv.getBlockState(new BlockPos((int) x0 + 3, y - 3, (int) z0)).isAir(), "bloc=" + lv.getBlockState(new BlockPos((int) x0 + 3, y - 3, (int) z0)));
                    var carb = lv.getBlockState(new BlockPos((int) x0 + 25, y - 1, (int) z0)).getBlock();
                    check("NUKE : sol carbonise (zone 0,5-2 km)", carb == net.minecraft.world.level.block.Blocks.BLACKSTONE || carb == net.minecraft.world.level.block.Blocks.SMOOTH_BASALT || carb == net.minecraft.world.level.block.Blocks.COAL_BLOCK, "bloc=" + carb);
                    check("NUKE : planches carbonisees detruites", lv.getBlockState(new BlockPos((int) x0 + 25, y, (int) z0)).isAir(), "");
                    check("NUKE : souffle detruit les constructions (zone 2-5 km)", lv.getBlockState(new BlockPos((int) x0 + 70, y, (int) z0)).isAir(), "");
                    check("NUKE : souffle detruit les feuilles", lv.getBlockState(new BlockPos((int) x0 + 70, y + 3, (int) z0)).isAir(), "");
                    check("NUKE : le sol n'est pas carbonise en zone souffle", lv.getBlockState(new BlockPos((int) x0 + 70, y - 1, (int) z0)).getBlock() == net.minecraft.world.level.block.Blocks.GRASS_BLOCK,
                            "bloc=" + lv.getBlockState(new BlockPos((int) x0 + 70, y - 1, (int) z0)));
                    check("NUKE : zone 5-10 km : constructions intactes", lv.getBlockState(new BlockPos((int) x0 + 150, y, (int) z0)).is(net.minecraft.world.level.block.Blocks.OAK_PLANKS), "");
                    check("NUKE : zone 5-10 km : arbres sans feuilles", lv.getBlockState(new BlockPos((int) x0 + 150, y + 3, (int) z0)).isAir(), "");
                    check("NUKE : hors zone : feuilles intactes", lv.getBlockState(new BlockPos((int) x0 + 230, y + 3, (int) z0)).getBlock() instanceof net.minecraft.world.level.block.LeavesBlock, "");
                    Nuke.scaleOverride = -1;
                    fin(r);
                }
            }
            case "bay" -> {
                if (lt == 1) {
                    String err = a.setLoadout(r.fp, W.CANNON.bit() | W.BAY.bit());
                    a.setAmmo(W.BAY, 6);
                    r.c1 = cow(r.start.x + 3, r.start.y, r.start.z);
                    a.nextWeapon(1);
                    r.i0 = a.ammo(W.BAY);
                    check("soute lourde installee", err == null && a.selected() == W.BAY, "err=" + err);
                    a.inFire = true;
                }
                if (lt == 14) a.inFire = false;
                if (lt == 70) {
                    check("soute : plusieurs bombes larguees en maintenant le tir", r.i0 - a.ammo(W.BAY) >= 2, r.i0 + " -> " + a.ammo(W.BAY));
                    check("soute : detruit la cible", !((LivingEntity) r.c1).isAlive(), "");
                    check("soute : detruit les blocs", lv.getBlockState(BlockPos.containing(r.start.x + 3, r.start.y - 1, r.start.z)).isAir(), "");
                    fin(r);
                }
            }
            case "jet_weapons" -> {
                if (lt == 1) {
                    r.c1 = cow(r.start.x, r.start.y, r.start.z + 30);
                    a.aimPitch = 0;
                    a.inFire = true;
                    r.i0 = a.ammo(W.CANNON);
                }
                if (lt == 30) {
                    a.inFire = false;
                    check("canon de l'avion", r.i0 - a.ammo(W.CANNON) >= 8 && ((LivingEntity) r.c1).getHealth() < ((LivingEntity) r.c1).getMaxHealth() || !r.c1.isAlive(), "mun=" + a.ammo(W.CANNON));
                    int before = a.ammo(W.ROCKET);
                    a.nextWeapon(1);
                    check("arme suivante : roquettes", a.selected() == W.ROCKET, "");
                    a.nextWeapon(1);
                    a.nextWeapon(1);
                    check("arme suivante : bombe puis retour canon", a.selected() == W.BOMB, "");
                    a.nextWeapon(1);
                    check("cycle complet d'armes", a.selected() == W.CANNON, "");
                    fin(r);
                }
            }
            default -> fin(r);
        }
    }
}
