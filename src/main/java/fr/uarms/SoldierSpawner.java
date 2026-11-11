package fr.uarms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.List;

/** Fait apparaitre des groupes de 5 a 50 soldats pres des joueurs, de temps en temps. */
public class SoldierSpawner {
    private static long nextAt = -1;
    private static final int CAP = 200;

    public static int count(MinecraftServer s) {
        int n = 0;
        for (ServerLevel l : s.getAllLevels()) n += l.getEntities(EntityTypeTest.forClass(Soldier.class), e -> true).size();
        return n;
    }

    public static int sweep(MinecraftServer s) {
        int n = 0;
        for (ServerLevel l : s.getAllLevels()) {
            for (Soldier e : l.getEntities(EntityTypeTest.forClass(Soldier.class), x -> true)) {
                e.discard();
                n++;
            }
        }
        return n;
    }

    public static void tick(MinecraftServer s) {
        long t = s.getTickCount();
        if (t % 100 != 0) return;
        RandomSource r = s.overworld().random;
        if (nextAt < 0) nextAt = t + 6000 + r.nextInt(6000);
        if (t < nextAt) return;
        nextAt = t + 6000 + r.nextInt(9000);
        if (!SoldierData.ENABLED || s.getWorldData().getDifficulty() == Difficulty.PEACEFUL) return;
        ServerLevel ow = s.overworld();
        List<ServerPlayer> ps = ow.players();
        if (ps.isEmpty()) return;
        ServerPlayer p = ps.get(r.nextInt(ps.size()));
        double a = r.nextDouble() * Math.PI * 2, dist = 60 + r.nextInt(50);
        BlockPos c = new BlockPos((int) (p.getX() + Math.cos(a) * dist), 0, (int) (p.getZ() + Math.sin(a) * dist));
        spawnGroup(ow, c, 5 + r.nextInt(46));
    }

    /** Renvoie le nombre de soldats reellement apparus. */
    public static int spawnGroup(ServerLevel level, BlockPos center, int n) {
        int room = CAP - count(level.getServer());
        n = Math.min(n, Math.max(0, room));
        double radius = 3 + Math.sqrt(n) * 1.8;
        RandomSource r = level.random;
        int ok = 0;
        for (int i = 0; i < n; i++) {
            for (int tries = 0; tries < 6; tries++) {
                int x = center.getX() + (int) Math.round((r.nextDouble() * 2 - 1) * radius);
                int z = center.getZ() + (int) Math.round((r.nextDouble() * 2 - 1) * radius);
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos pos = new BlockPos(x, y, z);
                BlockPos below = pos.below();
                if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) continue;
                if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(below).isEmpty()) continue;
                Soldier so = Reg.SOLDIER.get().create(level);
                if (so == null) continue;
                so.moveTo(x + 0.5, y, z + 0.5, r.nextFloat() * 360f, 0f);
                so.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
                level.addFreshEntityWithPassengers(so);
                ok++;
                break;
            }
        }
        return ok;
    }
}
