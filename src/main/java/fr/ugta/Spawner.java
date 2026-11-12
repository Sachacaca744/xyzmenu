package fr.ugta;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.UUID;

/** Apparition des policiers (selon les etoiles) et de la vie de la ville (passants, voitures). */
public class Spawner {
    // etendue de la carte GTA fournie
    public static final int MIN_X = -400, MAX_X = 720, MIN_Z = -400, MAX_Z = 400;

    public static boolean inCity(ServerPlayer p) {
        return p.getX() > MIN_X && p.getX() < MAX_X && p.getZ() > MIN_Z && p.getZ() < MAX_Z;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer srv = e.getServer();
        if (srv.getTickCount() % 20 != 0) return;
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (p.isSpectator() || p.level().dimension() != Level.OVERWORLD) continue;
            ServerLevel lv = p.serverLevel();
            Wanted.St s = Wanted.get(p);
            if (s.stars > 0) response(lv, p, s.stars);
            if (inCity(p)) ambient(lv, p);
        }
    }

    // ---------------------------------------------------------------- reaction de la police

    private static void response(ServerLevel lv, ServerPlayer p, int stars) {
        var box = p.getBoundingBox().inflate(100);
        int cops = lv.getEntitiesOfClass(Cop.class, box, Cop::isAlive).size();
        int desired = 2 + 2 * stars;
        if (cops < desired) {
            for (int i = 0; i < 2; i++) {
                BlockPos g = ground(lv, p, 55, 85, false);
                if (g != null) spawnCop(lv, g, false);
            }
        }
        if (stars >= 2) {
            int cars = lv.getEntitiesOfClass(Vehicle.class, box, v -> v.kind == Kind.POLICE).size();
            if (cars < stars) {
                BlockPos g = ground(lv, p, 60, 90, false);
                if (g != null) {
                    Vehicle car = Reg.create(Kind.POLICE, lv);
                    car.moveTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, lv.random.nextInt(360), 0);
                    car.temp = true;
                    lv.addFreshEntity(car);
                    for (int i = 0; i < 2; i++) {
                        Cop c = spawnCop(lv, g, false);
                        if (c != null) c.startRiding(car);
                    }
                }
            }
        }
        if (stars >= 3) {
            UUID id = p.getUUID();
            boolean has = !lv.getEntitiesOfClass(Vehicle.class, p.getBoundingBox().inflate(160),
                    v -> v.kind == Kind.POLICE_HELI && id.equals(v.targetId)).isEmpty();
            if (!has) {
                double a = lv.random.nextDouble() * Math.PI * 2;
                Vehicle h = Reg.create(Kind.POLICE_HELI, lv);
                h.moveTo(p.getX() + Math.cos(a) * 70, p.getY() + 25, p.getZ() + Math.sin(a) * 70, 0, 0);
                h.targetId = id;
                h.temp = true;
                lv.addFreshEntity(h);
            }
        }
        if (stars >= 5) {
            int army = lv.getEntitiesOfClass(Cop.class, box, c -> c.isAlive() && c.isArmy()).size();
            if (army < 6) {
                for (int i = 0; i < 3; i++) {
                    BlockPos g = ground(lv, p, 45, 70, false);
                    if (g != null) spawnCop(lv, g, true);
                }
            }
        }
    }

    public static Cop spawnCop(ServerLevel lv, BlockPos g, boolean army) {
        Cop c = Reg.COP.get().create(lv);
        if (c == null) return null;
        c.moveTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, lv.random.nextInt(360), 0);
        c.finalizeSpawn(lv, lv.getCurrentDifficultyAt(g), MobSpawnType.EVENT, null, null);
        if (army) c.setArmy(true);
        lv.addFreshEntity(c);
        return c;
    }

    // ---------------------------------------------------------------- vie de la ville

    private static void ambient(ServerLevel lv, ServerPlayer p) {
        var r = lv.random;
        int peds = lv.getEntitiesOfClass(Ped.class, p.getBoundingBox().inflate(50)).size();
        if (peds < 18 && r.nextFloat() < 0.8f) {
            for (int i = 0; i < 2; i++) {
                BlockPos g = ground(lv, p, 18, 46, true);
                if (g == null) continue;
                Ped ped = Reg.PED.get().create(lv);
                if (ped == null) continue;
                ped.moveTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, r.nextInt(360), 0);
                ped.finalizeSpawn(lv, lv.getCurrentDifficultyAt(g), MobSpawnType.NATURAL, null, null);
                lv.addFreshEntity(ped);
            }
        }
        int cars = lv.getEntitiesOfClass(Vehicle.class, p.getBoundingBox().inflate(70),
                v -> v.kind.cat == 0 && v.getPassengers().isEmpty()).size();
        if (cars < 5 && r.nextFloat() < 0.4f) {
            BlockPos g = ground(lv, p, 25, 60, false);
            if (g != null && lv.getBlockState(g.below()).is(Blocks.GRAY_CONCRETE)) {
                Kind[] ks = {Kind.SEDAN, Kind.SEDAN, Kind.SPORTS, Kind.TRUCK, Kind.POLICE};
                Vehicle v = Reg.create(ks[r.nextInt(ks.length)], lv);
                v.moveTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, r.nextInt(4) * 90f, 0);
                v.temp = true;
                lv.addFreshEntity(v);
            }
        }
        int cops = lv.getEntitiesOfClass(Cop.class, p.getBoundingBox().inflate(70)).size();
        if (cops < 2 && Wanted.stars(p) == 0 && r.nextFloat() < 0.15f) {
            BlockPos g = ground(lv, p, 20, 50, true);
            if (g != null) {
                spawnCop(lv, g, false);
                spawnCop(lv, g.east(), false);
            }
        }
    }

    /** Trouve un sol libre autour du joueur. sidewalk = trottoir obligatoire. */
    private static BlockPos ground(ServerLevel lv, ServerPlayer p, int min, int max, boolean sidewalk) {
        var r = lv.random;
        for (int i = 0; i < 10; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = min + r.nextDouble() * (max - min);
            int x = (int) (p.getX() + Math.cos(a) * d), z = (int) (p.getZ() + Math.sin(a) * d);
            if (!lv.hasChunkAt(new BlockPos(x, 64, z))) continue;
            int y = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            var below = lv.getBlockState(pos.below());
            if (!below.isSolid()) continue;
            if (sidewalk && !below.is(Blocks.LIGHT_GRAY_CONCRETE)) continue;
            if (inCity(p) && !sidewalk && !below.is(Blocks.GRAY_CONCRETE) && !below.is(Blocks.LIGHT_GRAY_CONCRETE) && !below.is(Blocks.GRASS_BLOCK)) continue;
            if (!lv.getBlockState(pos).isAir() || !lv.getBlockState(pos.above()).isAir()) continue;
            return pos;
        }
        return null;
    }
}
