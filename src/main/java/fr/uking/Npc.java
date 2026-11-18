package fr.uking;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.Random;

/** Generation des fiefs de seigneurs PNJ autour du joueur. */
public class Npc {
    public static void generateAround(ServerLevel lv, Fiefs fs, BlockPos center, int count) {
        Random rnd = new Random(center.asLong() ^ 0x5eedL);
        int made = 0, tries = 0;
        while (made < count && tries++ < 40) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 170 + rnd.nextDouble() * 140;
            int x = center.getX() + (int) (Math.cos(a) * d), z = center.getZ() + (int) (Math.sin(a) * d);
            boolean ok = true;
            for (Fief f : fs.all) if (Fiefs.hd(f.pos, new BlockPos(x, 0, z)) < 130) ok = false;
            if (!ok) continue;
            int lvl = made == 0 ? 0 : rnd.nextInt(3);
            if (create(lv, fs, x, z, lvl, rnd) != null) made++;
        }
    }

    static boolean flatDry(ServerLevel lv, int x, int z) {
        int min = 999, max = -999;
        for (int dx : new int[]{-14, 0, 14}) for (int dz : new int[]{-14, 0, 14}) {
            int g = Builder.surface(lv, x + dx, z + dz);
            if (!lv.getBlockState(new BlockPos(x + dx, g, z + dz)).getFluidState().isEmpty()) return false;
            min = Math.min(min, g); max = Math.max(max, g);
        }
        return max - min <= 7;
    }

    /** Cree et construit un fief PNJ de niveau lvl centre en (x,z). */
    public static Fief create(ServerLevel lv, Fiefs fs, int x, int z, int lvl, Random rnd) {
        if (!flatDry(lv, x, z)) return null;
        int g = Builder.surface(lv, x, z);
        BlockPos floor = new BlockPos(x, g, z);
        Fief f = fs.create(fs.freshName(rnd), null, floor.above(), lvl);
        f.name = (lvl == 2 ? "Château de " : lvl == 1 ? "Bourg de " : "Hameau de ") + f.name;
        f.lord = Fiefs.LORDS[rnd.nextInt(Fiefs.LORDS.length)];
        f.color = 1 + rnd.nextInt(5);
        f.gold = 80 + 100 * lvl + rnd.nextInt(60);
        f.lastIncome = lv.getGameTime();
        // place centrale
        Builder.Site s = new Builder.Site(lv, floor, 0);
        s.prep(-4, -4, 4, 4, 4);
        s.box(-3, 0, -3, 3, 0, 3, Blocks.STONE_BRICKS.defaultBlockState());
        lv.setBlock(floor.above(), Reg.FIEF_STONE.get().defaultBlockState(), 3);
        int houses = 2 + lvl;
        for (int i = 0; i < houses; i++) {
            double a = Math.PI * 2 * i / houses + 0.6;
            place(lv, f, Builder.H_HOUSE, floor, (int) (Math.cos(a) * 14), (int) (Math.sin(a) * 14));
        }
        place(lv, f, Builder.H_FARM, floor, 0, 22);
        place(lv, f, Builder.H_TOWER, floor, 20, -20);
        if (lvl >= 1) {
            place(lv, f, Builder.H_MARKET, floor, -22, 0);
            place(lv, f, Builder.H_BARRACKS, floor, 22, 4);
            place(lv, f, Builder.H_FORGE, floor, -20, -18);
        }
        if (lvl >= 1) Builder.build(lv, f, Builder.H_WALL, floor, 0);
        if (lvl >= 2) {
            // le chateau remplace la place : on le construit au nord, au dessus de la muraille interieure
            place(lv, f, Builder.H_CASTLE, floor, 0, -40);
        }
        // garnison
        BlockPos hb = floor.above();
        UType[] mix = {UType.SPEARMAN, UType.ARCHER, UType.SPEARMAN, UType.CROSSBOW, UType.KNIGHT, UType.KNIGHT, UType.ARCHER, UType.SPEARMAN};
        int n = 3 + lvl * 3;
        for (int i = 0; i < n; i++) fs.spawnUnit(lv, f, mix[i % mix.length], Fiefs.spawnSpot(lv, hb, 3, 9, rnd), Soldier.O_DEFEND);
        for (int i = 0; i < 3 + lvl * 2; i++) fs.spawnUnit(lv, f, UType.PEASANT, Fiefs.spawnSpot(lv, hb, 4, 12, rnd), Soldier.O_DEFEND);
        fs.spawnUnit(lv, f, UType.LORD, Fiefs.spawnSpot(lv, hb, 2, 4, rnd), Soldier.O_DEFEND);
        if (lvl >= 1) {
            Catapult c = fs.spawnCatapult(lv, f.key(), Fiefs.spawnSpot(lv, hb, 8, 12, rnd), 0, 6);
            if (c != null) f.catapults.add(c.blockPosition());
        }
        fs.setDirty();
        System.out.println("UKING: fief PNJ " + f.name + " niveau " + lvl + " en " + f.pos.toShortString());
        return f;
    }

    static void place(ServerLevel lv, Fief f, int type, BlockPos floor, int dx, int dz) {
        int g = Builder.surface(lv, floor.getX() + dx, floor.getZ() + dz);
        int r = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? 1 : 3) : (dz > 0 ? 2 : 0);
        Builder.build(lv, f, type, new BlockPos(floor.getX() + dx, g, floor.getZ() + dz), r);
        f.counts[type]++;
    }
}
