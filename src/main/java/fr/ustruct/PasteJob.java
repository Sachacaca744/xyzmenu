package fr.ustruct;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.io.*;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/** Place une structure .ustruct dans le monde (rotation, miroir, air optionnel). */
public class PasteJob extends Jobs.Job {
    private final ServerLevel level;
    private final BlockPos origin;
    private final int rot;        // 0, 90, 180, 270
    private final int mirror;     // 0 aucun, 1 x, 2 z
    private final boolean replaceAir;
    private DataInputStream in;
    private int sx, sy, sz;
    private int oldX, oldY, oldZ;
    private boolean hasEntities;
    private BlockState[] pal;
    private Walker w;
    private int phase = 0; // 0 blocs, 1 blockentities, 2 entites, 3 fin
    private int runRemaining = 0;
    private BlockState cur;
    private int beLeft = -1, entLeft = -1;
    private final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
    private final String name;

    public PasteJob(ServerLevel level, String name, BlockPos origin, int rot, int mirror, boolean replaceAir) throws IOException {
        this.level = level;
        this.origin = origin.immutable();
        this.rot = rot;
        this.mirror = mirror;
        this.replaceAir = replaceAir;
        this.name = name;
        this.label = "Placement '" + name + "'";
        open();
    }

    public static int[] size(String name) throws IOException {
        try (DataInputStream d = new DataInputStream(new BufferedInputStream(new GZIPInputStream(
                new FileInputStream(Store.file(name).toFile()), 1 << 16)))) {
            readHead(d);
            return new int[]{h_sx, h_sy, h_sz};
        }
    }

    // en-tete lu (partage simple pour size/info)
    static int h_sx, h_sy, h_sz, h_ox, h_oy, h_oz;
    static String h_name, h_dim;
    static long h_time;
    static int h_flags;

    static void readHead(DataInputStream d) throws IOException {
        String magic = d.readUTF();
        if (!magic.equals("USTR1")) throw new IOException("fichier invalide");
        h_name = d.readUTF();
        h_sx = d.readInt();
        h_sy = d.readInt();
        h_sz = d.readInt();
        h_dim = d.readUTF();
        h_ox = d.readInt();
        h_oy = d.readInt();
        h_oz = d.readInt();
        h_time = d.readLong();
        h_flags = d.readByte();
    }

    public static synchronized String[] info(String name) throws IOException {
        File f = Store.file(name).toFile();
        try (DataInputStream d = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(f), 1 << 16)))) {
            readHead(d);
            CompoundTag root = NbtIo.read(d);
            int p = root.getList("P", Tag.TAG_COMPOUND).size();
            return new String[]{h_sx + " x " + h_sy + " x " + h_sz, ((long) h_sx * h_sy * h_sz) + " blocs",
                    p + " types de blocs", (f.length() / 1024) + " Ko", new java.util.Date(h_time).toString(),
                    h_dim + " @ " + h_ox + " " + h_oy + " " + h_oz, (h_flags & 1) != 0 ? "avec entites" : "sans entites"};
        }
    }

    public static synchronized Object[] origin(String name) throws IOException {
        try (DataInputStream d = new DataInputStream(new BufferedInputStream(new GZIPInputStream(
                new FileInputStream(Store.file(name).toFile()), 1 << 16)))) {
            readHead(d);
            return new Object[]{h_dim, new BlockPos(h_ox, h_oy, h_oz)};
        }
    }

    private synchronized void open() throws IOException {
        in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(
                new FileInputStream(Store.file(name).toFile()), 1 << 16)));
        readHead(in);
        sx = h_sx;
        sy = h_sy;
        sz = h_sz;
        oldX = h_ox;
        oldY = h_oy;
        oldZ = h_oz;
        hasEntities = (h_flags & 1) != 0;
        CompoundTag root = NbtIo.read(in);
        var lookup = level.holderLookup(Registries.BLOCK);
        var list = root.getList("P", Tag.TAG_COMPOUND);
        pal = new BlockState[list.size()];
        Rotation r = rot == 90 ? Rotation.CLOCKWISE_90 : rot == 180 ? Rotation.CLOCKWISE_180
                : rot == 270 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.NONE;
        Mirror m = mirror == 1 ? Mirror.FRONT_BACK : mirror == 2 ? Mirror.LEFT_RIGHT : Mirror.NONE;
        for (int i = 0; i < pal.length; i++) {
            BlockState s = NbtUtils.readBlockState(lookup, list.getCompound(i));
            pal[i] = s.mirror(m).rotate(r);
        }
        w = new Walker(sx, sy, sz);
    }

    /** Taille du volume apres rotation. */
    public static int[] rotated(int sx, int sy, int sz, int rot) {
        return (rot == 90 || rot == 270) ? new int[]{sz, sy, sx} : new int[]{sx, sy, sz};
    }

    private void transform(int x, int y, int z, BlockPos.MutableBlockPos out) {
        if (mirror == 1) x = sx - 1 - x;
        else if (mirror == 2) z = sz - 1 - z;
        int nx, nz;
        switch (rot) {
            case 90 -> {
                nx = sz - 1 - z;
                nz = x;
            }
            case 180 -> {
                nx = sx - 1 - x;
                nz = sz - 1 - z;
            }
            case 270 -> {
                nx = z;
                nz = sx - 1 - x;
            }
            default -> {
                nx = x;
                nz = z;
            }
        }
        out.set(origin.getX() + nx, origin.getY() + y, origin.getZ() + nz);
    }

    @Override
    public double progress() {
        return phase == 0 ? (double) w.index / Math.max(1, w.total()) : 0.97;
    }

    @Override
    public void abort() {
        try {
            if (in != null) in.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public synchronized boolean step(MinecraftServer server, long deadline) throws Exception {
        int n = 0;
        if (phase == 0) {
            boolean more = true;
            while (more) {
                if (runRemaining == 0) {
                    int idx = Io.readVarInt(in);
                    runRemaining = Io.readVarInt(in);
                    cur = pal[idx];
                }
                if (replaceAir || !cur.isAir()) {
                    transform(w.x, w.y, w.z, mp);
                    level.setBlock(mp, cur, 2 | 16 | 32);
                    n += 4;
                }
                runRemaining--;
                more = w.next();
                if (more && (++n & 255) == 0 && System.nanoTime() > deadline) return false;
            }
            phase = 1;
        }
        if (phase == 1) {
            if (beLeft < 0) beLeft = in.readInt();
            while (beLeft > 0) {
                int x = in.readInt(), y = in.readInt(), z = in.readInt();
                CompoundTag t = NbtIo.read(in);
                beLeft--;
                transform(x, y, z, mp);
                BlockEntity be = level.getBlockEntity(mp);
                if (be != null) {
                    t.putInt("x", mp.getX());
                    t.putInt("y", mp.getY());
                    t.putInt("z", mp.getZ());
                    try {
                        be.load(t);
                        be.setChanged();
                        level.sendBlockUpdated(mp, be.getBlockState(), be.getBlockState(), 3);
                    } catch (Exception ignored) {
                    }
                }
                if ((++n & 15) == 0 && System.nanoTime() > deadline) return false;
            }
            phase = 2;
        }
        if (phase == 2) {
            if (entLeft < 0) entLeft = in.readInt();
            boolean doEnt = hasEntities && rot == 0 && mirror == 0;
            int dx = origin.getX() - oldX, dy = origin.getY() - oldY, dz = origin.getZ() - oldZ;
            while (entLeft > 0) {
                CompoundTag t = NbtIo.read(in);
                entLeft--;
                if (!doEnt) continue;
                ListTag pos = t.getList("Pos", Tag.TAG_DOUBLE);
                if (pos.size() == 3) {
                    ListTag np = new ListTag();
                    np.add(DoubleTag.valueOf(pos.getDouble(0) + dx));
                    np.add(DoubleTag.valueOf(pos.getDouble(1) + dy));
                    np.add(DoubleTag.valueOf(pos.getDouble(2) + dz));
                    t.put("Pos", np);
                }
                if (t.contains("TileX")) {
                    t.putInt("TileX", t.getInt("TileX") + dx);
                    t.putInt("TileY", t.getInt("TileY") + dy);
                    t.putInt("TileZ", t.getInt("TileZ") + dz);
                }
                t.remove("UUID");
                t.putUUID("UUID", UUID.randomUUID());
                EntityType.create(t, level).ifPresent(level::addFreshEntity);
            }
        }
        in.close();
        return true;
    }
}
