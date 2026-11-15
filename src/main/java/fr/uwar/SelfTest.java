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
        Entity c1;
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
                    for (int i = 0; i < 2; i++) {
                        Kind k = Kind.values()[i];
                        Aircraft ac = new Aircraft(Reg.AIRCRAFTS.get(k).get(), sl, k);
                        ac.moveTo(i == 0 ? -12 : 14, -60, 15, 180, 0);
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
        for (int cx = -8; cx <= 12; cx++) for (int cz = -8; cz <= 12; cz++) lv.setChunkForced(cx, cz, true);
        lv.getChunkSource().getChunk(0, 0, net.minecraft.world.level.chunk.ChunkStatus.FULL, true);
        running = true;
        t = 0;
        pass = fail = 0;
        runs.clear();
        String[][] sc = {{"heli_fly", "HELI"}, {"jet_fly", "JET"}, {"cannon", "HELI"}, {"rocket", "HELI"}, {"missile", "HELI"}, {"bomb", "JET"},
                {"empty", "HELI"}, {"supply", "HELI"}, {"fuel_out", "HELI"}, {"destroy", "HELI"}, {"crash", "JET"}, {"persist", "JET"},
                {"pickup", "HELI"}, {"place", "JET"}, {"jet_weapons", "JET"}};
        int i = 0;
        for (String[] s : sc) {
            Run r = mk(s[0], Kind.valueOf(s[1]), -90 + (i % 8) * 24, (i / 8) * 60 - 30, s[0]);
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
                    r.a.setInput(r.a.thrust, r.a.inBoost, r.a.inFire, r.a.inU > 0, r.a.aimYaw, r.a.aimPitch);
                step(r, t - r.t0);
            } catch (Throwable ex) {
                ex.printStackTrace();
                check("exception " + r.name, false, ex.toString());
                r.done = true;
            }
        }
        if (t > 1500 && !all) {
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
                    r.c1 = cow(r.start.x, r.start.y, r.start.z + 30);
                    a.nextWeapon(1);
                    a.aimPitch = -1.5f;
                    a.inFire = true;
                    r.i0 = a.ammo(W.ROCKET);
                }
                if (lt == 8) a.inFire = false;
                if (lt == 70) {
                    check("roquette selectionnee", a.selected() == W.ROCKET, "arme=" + a.selected());
                    check("roquettes consommees", r.i0 - a.ammo(W.ROCKET) >= 1, r.i0 + " -> " + a.ammo(W.ROCKET));
                    LivingEntity le = (LivingEntity) r.c1;
                    check("roquette detruit la cible", !le.isAlive(), "pv=" + le.getHealth());
                    boolean left = false;
                    for (Entity en : lv.getAllEntities()) if (en instanceof Shell) left = true;
                    check("plus de projectile apres explosion", !left, "");
                    fin(r);
                }
            }
            case "missile" -> {
                if (lt == 1) {
                    r.c1 = cow(r.start.x + 8, r.start.y, r.start.z + 40);
                    a.nextWeapon(2);
                    a.aimPitch = 0;
                    r.i0 = a.ammo(W.MISSILE);
                }
                if (lt == 20) {
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
                    ItemStack s = new ItemStack(Reg.AIRCRAFT_ITEMS.get(r.kind).get());
                    r.fp.setItemInHand(InteractionHand.MAIN_HAND, s);
                    BlockPos ground = BlockPos.containing(r.start).below();
                    int before = lv.getEntitiesOfClass(Aircraft.class, new net.minecraft.world.phys.AABB(r.start, r.start).inflate(10)).size();
                    var res = s.getItem().useOn(new UseOnContext(lv, r.fp, InteractionHand.MAIN_HAND, s,
                            new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false)));
                    var list = lv.getEntitiesOfClass(Aircraft.class, new net.minecraft.world.phys.AABB(r.start, r.start).inflate(10));
                    check("l'objet pose un appareil", list.size() == before + 1, "res=" + res + " n=" + list.size());
                    Aircraft n = null;
                    for (Aircraft x : list) if (x != a) n = x;
                    if (n != null) {
                        check("appareil pose : 40% carburant + 150 obus", Math.abs(n.fuel() - n.kind.fuelCap * 0.4f) < 1 && n.ammo(W.CANNON) == 150, "fuel=" + n.fuel() + " mun=" + n.ammo(W.CANNON));
                        n.discard();
                    }
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
