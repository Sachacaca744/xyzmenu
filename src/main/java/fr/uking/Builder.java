package fr.uking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Random;

/** Construction procedurale des batiments (maisons, ferme, marche, caserne, forge, tour, muraille, chateau). */
public class Builder {
    static final int H_HOUSE = 0, H_FARM = 1, H_MARKET = 2, H_BARRACKS = 3, H_FORGE = 4, H_TOWER = 5, H_WALL = 6, H_CASTLE = 7;

    public static String tryBuild(ServerPlayer p, int type, BlockPos clicked, Direction towardPlayer) {
        ServerLevel lv = (ServerLevel) p.level();
        Fiefs fs = Fiefs.get(lv);
        Fief f = fs.nearestOwned(p.getUUID(), clicked, true);
        if (f == null) return "Hors de votre fief : posez le plan dans le territoire de votre pierre de fief.";
        if (f.level < Fief.BUILD_LEVEL[type]) return "Il faut un village (niveau 2) pour ce bâtiment.";
        if ((type == H_WALL || type == H_CASTLE) && f.counts[type] > 0) return "Déjà construit dans ce fief.";
        int cost = Cfg.cost(Fief.BUILD_COST[type]);
        if (f.gold < cost) return "Pas assez d'or dans le fief (" + cost + " nécessaires, " + f.gold + " disponibles).";
        if (type != H_WALL && Fiefs.hd(clicked, f.pos) < 4) return "Trop près de la pierre de fief.";
        int r = (towardPlayer.get2DDataValue() - Direction.SOUTH.get2DDataValue()) & 3;
        build(lv, f, type, clicked, r);
        f.gold -= cost;
        f.counts[type]++;
        if (type == H_CASTLE) f.level = 2;
        if (type == H_HOUSE && f.peasants.size() < 6 + f.counts[0] * 2) {
            fs.spawnUnit(lv, f, UType.PEASANT, Fiefs.spawnSpot(lv, clicked, 3, 6, lv.random), Soldier.O_DEFEND);
        }
        fs.setDirty();
        lv.playSound(null, clicked, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1f, 0.8f);
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("uking.build." + Fief.BUILDINGS[type]).append(" ✔"), true);
        return null;
    }

    public static void build(ServerLevel lv, Fief f, int type, BlockPos floor, int r) {
        Site s = new Site(lv, floor, r);
        switch (type) {
            case H_HOUSE -> house(s);
            case H_FARM -> farm(s);
            case H_MARKET -> market(s);
            case H_BARRACKS -> barracks(s);
            case H_FORGE -> forge(s);
            case H_TOWER -> tower(s);
            case H_WALL -> wall(new Site(lv, f.pos.below(), r), 34 + 6 * 0);
            case H_CASTLE -> castle(s);
        }
    }

    public static int surface(ServerLevel lv, int x, int z) {
        lv.getChunk(x >> 4, z >> 4);
        return lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    // ------------------------------------------------------------------ outils
    static final BlockState PLANKS = Blocks.OAK_PLANKS.defaultBlockState(), LOG = Blocks.OAK_LOG.defaultBlockState(), COBBLE = Blocks.COBBLESTONE.defaultBlockState(),
            BRICK = Blocks.STONE_BRICKS.defaultBlockState(), MOSSY = Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), CRACK = Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
            AIR = Blocks.AIR.defaultBlockState(), GLASS = Blocks.GLASS.defaultBlockState(), TORCH = Blocks.TORCH.defaultBlockState();

    static class Site {
        final ServerLevel lv;
        final BlockPos o;
        final int r;
        final Random rnd = new Random(7);

        Site(ServerLevel lv, BlockPos o, int r) { this.lv = lv; this.o = o; this.r = r & 3; }

        BlockPos w(int x, int y, int z) {
            for (int i = 0; i < r; i++) { int t = x; x = -z; z = t; }
            return o.offset(x, y, z);
        }

        Direction d(Direction local) {
            for (int i = 0; i < r; i++) local = local.getClockWise();
            return local;
        }

        void set(int x, int y, int z, BlockState st) { lv.setBlock(w(x, y, z), st, 18); }

        void box(int x1, int y1, int z1, int x2, int y2, int z2, BlockState st) {
            for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) set(x, y, z, st);
        }

        /** Murs seulement (rectangle creux), de y1 a y2. */
        void shell(int x1, int y1, int z1, int x2, int y2, int z2, BlockState st) {
            for (int y = y1; y <= y2; y++) for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++)
                if (x == x1 || x == x2 || z == z1 || z == z2) set(x, y, z, st);
        }

        BlockState brick() {
            int k = rnd.nextInt(10);
            return k == 0 ? MOSSY : k == 1 ? CRACK : BRICK;
        }

        void brickShell(int x1, int y1, int z1, int x2, int y2, int z2) {
            for (int y = y1; y <= y2; y++) for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++)
                if (x == x1 || x == x2 || z == z1 || z == z2) set(x, y, z, brick());
        }

        /** Terrasse : comble sous le sol, degage au dessus. */
        void prep(int x1, int z1, int x2, int z2, int h) {
            for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
                for (int y = -1; y >= -14; y--) {
                    BlockPos p = w(x, y, z);
                    BlockState b = lv.getBlockState(p);
                    if (!b.isAir() && !b.canBeReplaced() && b.getFluidState().isEmpty() && !b.is(BlockTags.LEAVES) && !b.is(BlockTags.LOGS)) break;
                    lv.setBlock(p, y == -1 ? Blocks.DIRT.defaultBlockState() : COBBLE, 18);
                }
                for (int y = 0; y <= h; y++) {
                    BlockPos p = w(x, y, z);
                    if (!lv.getBlockState(p).isAir()) lv.setBlock(p, AIR, 18);
                }
            }
        }

        void stairs(Block b, int x, int y, int z, Direction local, boolean top) {
            set(x, y, z, b.defaultBlockState().setValue(StairBlock.FACING, d(local)).setValue(StairBlock.HALF, top ? net.minecraft.world.level.block.state.properties.Half.TOP : net.minecraft.world.level.block.state.properties.Half.BOTTOM));
        }

        void door(int x, int y, int z, Direction local) {
            BlockState b = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, d(local));
            set(x, y, z, b.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            set(x, y + 1, z, b.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }

        void ladder(int x, int y1, int y2, int z, Direction local) {
            for (int y = y1; y <= y2; y++) set(x, y, z, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, d(local)));
        }

        void bed(int x, int y, int z, Direction headDir) {
            Direction hd = d(headDir);
            BlockPos foot = w(x, y, z);
            BlockState b = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, hd);
            lv.setBlock(foot, b.setValue(BedBlock.PART, BedPart.FOOT), 18);
            lv.setBlock(foot.relative(hd), b.setValue(BedBlock.PART, BedPart.HEAD), 18);
        }

        void wallTorch(int x, int y, int z, Direction out) {
            set(x, y, z, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, d(out)));
        }

        /** Toit a deux pans : mur a demi-largeur hx, longueur hz, premiere rangee y0. Faitage le long de z. */
        void roofZ(int hx, int hz, int y0, Block stair, BlockState fill) {
            for (int k = 0; k <= hx; k++) {
                int xe = hx + 1 - k;
                for (int z = -hz - 1; z <= hz + 1; z++) {
                    stairs(stair, -xe, y0 + k, z, Direction.EAST, false);
                    stairs(stair, xe, y0 + k, z, Direction.WEST, false);
                    for (int x = -xe + 1; x <= xe - 1; x++) set(x, y0 + k, z, fill);
                }
            }
        }
    }

    // ------------------------------------------------------------------ batiments
    static void house(Site s) {
        s.prep(-4, -4, 4, 4, 9);
        s.box(-3, 0, -3, 3, 0, 3, PLANKS);
        s.shell(-3, 1, -3, 3, 3, 3, PLANKS);
        for (int y = 1; y <= 3; y++) for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) s.set(c[0], y, c[1], LOG);
        for (int[] c : new int[][]{{-3, 0}, {3, 0}, {0, -3}, {-2, 3}, {2, 3}}) s.set(c[0], 2, c[1], GLASS);
        s.door(0, 1, 3, Direction.SOUTH);
        s.roofZ(3, 3, 4, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS.defaultBlockState());
        s.bed(-2, 1, -2, Direction.NORTH);
        s.bed(-1, 1, -2, Direction.NORTH);
        s.set(2, 1, -2, Blocks.CRAFTING_TABLE.defaultBlockState());
        s.set(2, 1, -1, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, s.d(Direction.WEST)));
        s.set(0, 3, 0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        s.set(0, 0, 4, Blocks.DIRT_PATH.defaultBlockState());
    }

    static void farm(Site s) {
        s.prep(-5, -5, 5, 5, 3);
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            boolean water = x == 0;
            s.set(x, 0, z, water ? Blocks.WATER.defaultBlockState() : Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            if (!water) s.set(x, 1, z, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        }
        for (int i = -5; i <= 5; i++) for (int[] c : new int[][]{{i, -5}, {i, 5}, {-5, i}, {5, i}}) {
            boolean gate = c[1] == 5 && c[0] == 0;
            s.set(c[0], 0, c[1], PLANKS);
            s.set(c[0], 1, c[1], gate ? AIR : Blocks.OAK_FENCE.defaultBlockState());
        }
        s.set(-5, 2, -5, TORCH); s.set(5, 2, -5, TORCH);
        s.set(-5, 1, -5, Blocks.OAK_FENCE.defaultBlockState()); s.set(5, 1, -5, Blocks.OAK_FENCE.defaultBlockState());
    }

    static void market(Site s) {
        s.prep(-5, -3, 5, 3, 6);
        s.box(-5, 0, -3, 5, 0, 3, Blocks.SPRUCE_PLANKS.defaultBlockState());
        for (int[] c : new int[][]{{-4, -2}, {4, -2}, {-4, 2}, {4, 2}, {0, -2}, {0, 2}})
            for (int y = 1; y <= 3; y++) s.set(c[0], y, c[1], Blocks.OAK_FENCE.defaultBlockState());
        for (int x = -5; x <= 5; x++) for (int z = -3; z <= 3; z++)
            s.set(x, 4, z, ((x + 5) / 2 % 2 == 0) ? Blocks.RED_WOOL.defaultBlockState() : Blocks.WHITE_WOOL.defaultBlockState());
        for (int x = -3; x <= 3; x += 2) {
            s.set(x, 1, -2, x % 4 == 0 ? Blocks.HAY_BLOCK.defaultBlockState() : Blocks.BARREL.defaultBlockState());
            s.set(x, 2, -2, x % 4 == 0 ? Blocks.MELON.defaultBlockState() : Blocks.PUMPKIN.defaultBlockState());
        }
        s.set(-2, 1, 2, Blocks.COMPOSTER.defaultBlockState());
        s.set(2, 1, 2, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, s.d(Direction.SOUTH)));
        s.set(0, 3, 0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    static void barracks(Site s) {
        s.prep(-4, -6, 4, 6, 10);
        s.box(-3, 0, -5, 3, 0, 5, COBBLE);
        s.brickShell(-3, 1, -5, 3, 4, 5);
        for (int[] c : new int[][]{{-3, -5}, {3, -5}, {-3, 5}, {3, 5}}) for (int y = 1; y <= 4; y++) s.set(c[0], y, c[1], LOG);
        for (int z = -3; z <= 3; z += 3) { s.set(-3, 3, z, GLASS); s.set(3, 3, z, GLASS); }
        s.door(0, 1, 5, Direction.SOUTH);
        s.roofZ(3, 5, 5, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        for (int z = -4; z <= 2; z += 2) { s.bed(-2, 1, z, Direction.SOUTH); s.bed(2, 1, z, Direction.SOUTH); }
        s.wallTorch(0, 3, -4, Direction.SOUTH);
        s.set(0, 1, -4, Blocks.BARREL.defaultBlockState());
        s.set(1, 1, -4, Blocks.ANVIL.defaultBlockState());
    }

    static void forge(Site s) {
        s.prep(-4, -4, 4, 4, 9);
        s.box(-3, 0, -3, 3, 0, 3, COBBLE);
        for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) for (int y = 1; y <= 3; y++) s.set(c[0], y, c[1], LOG);
        s.box(-2, 1, -3, 2, 3, -3, COBBLE);
        s.box(-1, 4, -3, 1, 8, -3, COBBLE);
        s.box(-3, 4, -3, 3, 4, 3, Blocks.DARK_OAK_SLAB.defaultBlockState());
        s.set(-2, 1, -2, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, s.d(Direction.SOUTH)));
        s.set(0, 1, -2, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlastFurnaceBlock.FACING, s.d(Direction.SOUTH)));
        s.set(2, 1, -2, Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, s.d(Direction.EAST)));
        s.set(-2, 1, 2, Blocks.SMITHING_TABLE.defaultBlockState());
        s.set(2, 1, 2, Blocks.GRINDSTONE.defaultBlockState());
        s.set(0, 1, 0, Blocks.LAVA_CAULDRON.defaultBlockState());
        s.set(0, 3, 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    static void tower(Site s) {
        s.prep(-4, -4, 4, 4, 17);
        s.box(-3, 0, -3, 3, 0, 3, COBBLE);
        s.brickShell(-3, 1, -3, 3, 12, 3);
        s.box(-3, 8, -3, 3, 8, 3, PLANKS);
        s.box(0, 8, -2, 0, 8, -2, AIR);
        s.ladder(0, 1, 8, -2, Direction.SOUTH);
        s.ladder(0, 9, 12, -2, Direction.SOUTH);
        s.door(0, 1, 3, Direction.SOUTH);
        for (int y : new int[]{4, 6, 10}) { s.set(-3, y, 0, Blocks.IRON_BARS.defaultBlockState()); s.set(3, y, 0, Blocks.IRON_BARS.defaultBlockState()); s.set(0, y, 3, Blocks.IRON_BARS.defaultBlockState()); }
        s.box(-4, 12, -4, 4, 12, 4, BRICK);
        for (int i = -4; i <= 4; i++) for (int[] c : new int[][]{{i, -4}, {i, 4}, {-4, i}, {4, i}})
            if ((i + 4) % 2 == 0) s.set(c[0], 13, c[1], Blocks.STONE_BRICK_WALL.defaultBlockState());
        s.box(-2, 12, -2, 2, 12, 2, AIR);
        s.box(0, 12, -2, 0, 12, -2, AIR);
        s.box(-2, 12, -2, 2, 12, 2, PLANKS);
        s.set(0, 12, -2, AIR);
        s.wallTorch(0, 5, -2, Direction.SOUTH);
        s.set(-3, 13, -3, Blocks.REDSTONE_TORCH.defaultBlockState());
    }

    static void wall(Site s, int half) {
        ServerLevel lv = s.lv;
        for (int i = -half; i <= half; i++) {
            for (int[] c : new int[][]{{i, -half}, {i, half}, {-half, i}, {half, i}}) {
                boolean gate = c[1] == half && Math.abs(c[0]) <= 1;
                BlockPos wp = s.w(c[0], 0, c[1]);
                int g = surface(lv, wp.getX(), wp.getZ());
                for (int y = g - 2; y <= g + 5; y++) {
                    BlockPos p = new BlockPos(wp.getX(), y, wp.getZ());
                    if (gate && y > g && y <= g + 4) { lv.setBlock(p, AIR, 18); continue; }
                    lv.setBlock(p, s.brick(), 18);
                }
                if (gate) { lv.setBlock(new BlockPos(wp.getX(), g + 4, wp.getZ()), Blocks.IRON_BARS.defaultBlockState(), 18); }
                if ((Math.abs(c[0]) + Math.abs(c[1])) % 2 == 0) lv.setBlock(new BlockPos(wp.getX(), g + 6, wp.getZ()), Blocks.STONE_BRICK_WALL.defaultBlockState(), 18);
                // couloir de ronde
                if (!gate && lv.getBlockState(new BlockPos(wp.getX(), g + 5, wp.getZ())).isAir() == false) { }
            }
        }
        for (int sx : new int[]{-half, half}) for (int sz : new int[]{-half, half}) {
            BlockPos c = s.w(sx, 0, sz);
            int g = surface(lv, c.getX(), c.getZ());
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                int gg = surface(lv, c.getX() + x, c.getZ() + z);
                for (int y = Math.min(g, gg) - 2; y <= g + 10; y++) {
                    boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2;
                    BlockPos p = new BlockPos(c.getX() + x, y, c.getZ() + z);
                    if (y <= g + 9 && !edge) { if (y > g) lv.setBlock(p, AIR, 18); else lv.setBlock(p, BRICK, 18); }
                    else if (y <= g + 9) lv.setBlock(p, s.brick(), 18);
                    else if (edge) lv.setBlock(p, BRICK, 18);
                }
                if (Math.abs(x) == 2 && Math.abs(z) == 2) lv.setBlock(new BlockPos(c.getX() + x, g + 11, c.getZ() + z), Blocks.STONE_BRICK_WALL.defaultBlockState(), 18);
            }
        }
    }

    static void castle(Site s) {
        s.prep(-15, -15, 15, 15, 26);
        s.box(-13, 0, -13, 13, 0, 13, Blocks.GRAVEL.defaultBlockState());
        // muraille d'enceinte
        s.brickShell(-13, 1, -13, 13, 8, 13);
        for (int i = -13; i <= 13; i += 2) for (int[] c : new int[][]{{i, -13}, {i, 13}, {-13, i}, {13, i}}) s.set(c[0], 9, c[1], Blocks.STONE_BRICK_WALL.defaultBlockState());
        s.box(-1, 1, 13, 1, 5, 13, AIR);
        for (int x = -1; x <= 1; x++) s.set(x, 5, 13, Blocks.IRON_BARS.defaultBlockState());
        s.box(-2, 6, 13, 2, 6, 13, BRICK);
        // tours d'angle
        for (int sx : new int[]{-12, 12}) for (int sz : new int[]{-12, 12}) {
            s.brickShell(sx - 2, 1, sz - 2, sx + 2, 15, sz + 2);
            s.box(sx - 2, 16, sz - 2, sx + 2, 16, sz + 2, BRICK);
            for (int i = -2; i <= 2; i++) for (int[] c : new int[][]{{i, -2}, {i, 2}, {-2, i}, {2, i}})
                if ((i + 2) % 2 == 0) s.set(sx + c[0], 17, sz + c[1], Blocks.STONE_BRICK_WALL.defaultBlockState());
            s.set(sx, 5, sz, Blocks.LANTERN.defaultBlockState());
            s.box(sx - 1, 1, sz - 1, sx + 1, 15, sz + 1, AIR);
        }
        // donjon
        s.box(-6, 0, -11, 6, 0, -2, BRICK);
        s.brickShell(-6, 1, -11, 6, 15, -2);
        s.box(-5, 7, -10, 5, 7, -3, PLANKS);
        s.box(-6, 15, -11, 6, 15, -2, BRICK);
        for (int i = -6; i <= 6; i += 2) { s.set(i, 16, -11, Blocks.STONE_BRICK_WALL.defaultBlockState()); s.set(i, 16, -2, Blocks.STONE_BRICK_WALL.defaultBlockState()); }
        for (int z = -11; z <= -2; z += 2) { s.set(-6, 16, z, Blocks.STONE_BRICK_WALL.defaultBlockState()); s.set(6, 16, z, Blocks.STONE_BRICK_WALL.defaultBlockState()); }
        s.box(-1, 1, -2, 1, 3, -2, AIR);
        s.door(0, 1, -2, Direction.SOUTH);
        s.set(-1, 1, -2, AIR); s.set(1, 1, -2, AIR);
        s.set(5, 7, -3, AIR);
        s.ladder(5, 1, 15, -4, Direction.WEST);
        s.set(5, 7, -4, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, s.d(Direction.WEST)));
        for (int y : new int[]{3, 10}) for (int x = -4; x <= 4; x += 4) s.set(x, y, -2, GLASS);
        for (int y : new int[]{3, 10}) { s.set(-6, y, -6, GLASS); s.set(6, y, -6, GLASS); }
        // salle du trone
        for (int z = -9; z <= -3; z++) s.set(0, 1, z, Blocks.RED_CARPET.defaultBlockState());
        s.set(0, 1, -10, Blocks.GOLD_BLOCK.defaultBlockState());
        s.set(0, 2, -10, Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, s.d(Direction.SOUTH)));
        s.wallTorch(-3, 4, -3, Direction.SOUTH); s.wallTorch(3, 4, -3, Direction.SOUTH);
        for (int x : new int[]{-3, 3}) s.set(x, 5, -1, Blocks.RED_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, s.d(Direction.SOUTH)));
        s.set(-2, 8, -9, Blocks.RED_BED.defaultBlockState());
        s.set(-2, 8, -9, AIR);
        s.bed(-4, 8, -9, Direction.SOUTH);
        s.set(4, 8, -9, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, s.d(Direction.WEST)));
        // cour
        s.set(-8, 1, 6, Blocks.ANVIL.defaultBlockState());
        s.set(8, 1, 6, Blocks.BARREL.defaultBlockState());
        for (int[] c : new int[][]{{-4, 12}, {4, 12}}) s.set(c[0], 3, c[1] - 1, Blocks.LANTERN.defaultBlockState());
    }
}
