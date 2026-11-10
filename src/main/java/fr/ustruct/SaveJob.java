package fr.ustruct;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/** Enregistre un volume du monde dans un fichier .ustruct. */
public class SaveJob extends Jobs.Job {
    private final ServerLevel level;
    private final BlockPos min;
    private final int sx, sy, sz;
    private final String name;
    private final boolean entities;
    private final Walker w;
    private final Map<BlockState, Integer> palette = new LinkedHashMap<>();
    private final Path tmpCells, tmpBe;
    private DataOutputStream cells, bes;
    private int beCount = 0;
    private int runIdx = -1;
    private int runLen = 0;
    private boolean started = false;
    private final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
    private volatile boolean finishing = false;
    private volatile boolean finished = false;
    private volatile String error = null;

    public SaveJob(ServerLevel level, BlockPos min, int sx, int sy, int sz, String name, boolean entities) {
        this.level = level;
        this.min = min.immutable();
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.name = name;
        this.entities = entities;
        this.w = new Walker(sx, sy, sz);
        this.label = "Sauvegarde '" + name + "'";
        Path dir = Store.structures();
        this.tmpCells = dir.resolve("." + name + ".cells.tmp");
        this.tmpBe = dir.resolve("." + name + ".be.tmp");
    }

    @Override
    public double progress() {
        return finishing ? 0.99 : (double) w.index / Math.max(1, w.total());
    }

    @Override
    public void abort() {
        closeQuiet();
        try {
            Files.deleteIfExists(tmpCells);
            Files.deleteIfExists(tmpBe);
        } catch (IOException ignored) {
        }
    }

    private void closeQuiet() {
        try {
            if (cells != null) cells.close();
        } catch (IOException ignored) {
        }
        try {
            if (bes != null) bes.close();
        } catch (IOException ignored) {
        }
    }

    private void flushRun() throws IOException {
        if (runLen > 0) {
            Io.writeVarInt(cells, runIdx);
            Io.writeVarInt(cells, runLen);
        }
        runLen = 0;
    }

    @Override
    public boolean step(MinecraftServer server, long deadline) throws Exception {
        if (error != null) throw new IOException(error);
        if (finishing) return finished;
        if (!started) {
            cells = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmpCells.toFile()), 1 << 16));
            bes = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmpBe.toFile()), 1 << 16));
            started = true;
        }
        int n = 0;
        boolean more = true;
        while (more) {
            mp.set(min.getX() + w.x, min.getY() + w.y, min.getZ() + w.z);
            BlockState s = level.getBlockState(mp);
            if (s.isAir()) s = Blocks.AIR.defaultBlockState();
            Integer idx = palette.get(s);
            if (idx == null) {
                idx = palette.size();
                palette.put(s, idx);
            }
            if (idx == runIdx) {
                runLen++;
            } else {
                flushRun();
                runIdx = idx;
                runLen = 1;
            }
            if (s.hasBlockEntity()) {
                BlockEntity be = level.getBlockEntity(mp);
                if (be != null) {
                    CompoundTag t = be.saveWithFullMetadata();
                    t.remove("x");
                    t.remove("y");
                    t.remove("z");
                    bes.writeInt(w.x);
                    bes.writeInt(w.y);
                    bes.writeInt(w.z);
                    NbtIo.write(t, bes);
                    beCount++;
                }
            }
            more = w.next();
            if (more && (++n & 255) == 0 && System.nanoTime() > deadline) return false;
        }
        flushRun();
        closeQuiet();
        // entites decoratives (cadres, tableaux, supports d'armure)
        final List<CompoundTag> ents = new ArrayList<>();
        if (entities) {
            AABB box = new AABB(min.getX(), min.getY(), min.getZ(), min.getX() + sx, min.getY() + sy, min.getZ() + sz);
            for (Entity e : level.getEntitiesOfClass(Entity.class, box, x -> x instanceof HangingEntity || x instanceof ArmorStand)) {
                CompoundTag t = new CompoundTag();
                if (e.save(t)) ents.add(t);
            }
        }
        final List<BlockState> pal = new ArrayList<>(palette.keySet());
        finishing = true;
        final String dim = level.dimension().location().toString();
        final int bec = beCount;
        Thread th = new Thread(() -> {
            try {
                assemble(pal, ents, dim, bec);
                finished = true;
            } catch (Throwable t) {
                error = String.valueOf(t);
            }
        }, "ustruct-save");
        th.setDaemon(true);
        th.start();
        return false;
    }

    private void assemble(List<BlockState> pal, List<CompoundTag> ents, String dim, int bec) throws IOException {
        Path out = Store.file(name);
        Path part = Store.structures().resolve(name + ".ustruct.part");
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(new FileOutputStream(part.toFile()), 1 << 16), 1 << 16))) {
            o.writeUTF("USTR1");
            o.writeUTF(name);
            o.writeInt(sx);
            o.writeInt(sy);
            o.writeInt(sz);
            o.writeUTF(dim);
            o.writeInt(min.getX());
            o.writeInt(min.getY());
            o.writeInt(min.getZ());
            o.writeLong(System.currentTimeMillis());
            o.writeByte(entities ? 1 : 0);
            ListTag pl = new ListTag();
            for (BlockState s : pal) pl.add(NbtUtils.writeBlockState(s));
            CompoundTag root = new CompoundTag();
            root.put("P", pl);
            NbtIo.write(root, o);
            try (InputStream in = new BufferedInputStream(new FileInputStream(tmpCells.toFile()), 1 << 16)) {
                in.transferTo(o);
            }
            o.writeInt(bec);
            try (InputStream in = new BufferedInputStream(new FileInputStream(tmpBe.toFile()), 1 << 16)) {
                in.transferTo(o);
            }
            o.writeInt(ents.size());
            for (CompoundTag t : ents) NbtIo.write(t, o);
        }
        Files.move(part, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(tmpCells);
        Files.deleteIfExists(tmpBe);
    }
}
