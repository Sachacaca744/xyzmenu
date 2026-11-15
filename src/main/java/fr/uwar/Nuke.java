package fr.uwar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Bombe nucleaire.
 * Zones (rayons de base, multiplies par la config nukeScale) :
 *   0 - 500 m      : explosion, cratere (tout disparait)
 *   500 m - 2 km   : zone carbonisee + poison permanent
 *   2 km - 5 km    : souffle qui detruit tout (constructions, arbres) + poison
 *   5 km - 10 km   : arbres sans feuilles, animaux morts
 * Les chunks charges sont traites progressivement (pas de lag) ; les autres le sont a leur chargement,
 * grace a la liste des explosions sauvegardee dans le monde.
 */
@Mod.EventBusSubscriber(modid = Uwar.MODID)
public class Nuke {
    public static final double R0 = 500, R1 = 2000, R2 = 5000, R3 = 10000;
    /** Pour les tests : remplace nukeScale si > 0. */
    public static double scaleOverride = -1;

    static double cfgScale() {
        return scaleOverride > 0 ? scaleOverride : Cfg.NUKE_SCALE.get();
    }

    // ------------------------------------------------------------ donnees sauvegardees
    public static class Site {
        public final String dim;
        public final double x, y, z, sc;
        final Set<Long> done = new HashSet<>();

        Site(String dim, double x, double y, double z, double sc) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.sc = sc;
        }

        double maxR() {
            return R3 * sc;
        }

        double hd(double px, double pz) {
            return Math.hypot(px - x, pz - z);
        }
    }

    public static class Data extends SavedData {
        public final List<Site> sites = new ArrayList<>();

        static Data load(CompoundTag t) {
            Data d = new Data();
            ListTag l = t.getList("S", 10);
            for (int i = 0; i < l.size(); i++) {
                CompoundTag c = l.getCompound(i);
                d.sites.add(new Site(c.getString("D"), c.getDouble("X"), c.getDouble("Y"), c.getDouble("Z"), c.getDouble("C")));
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            ListTag l = new ListTag();
            for (Site s : sites) {
                CompoundTag c = new CompoundTag();
                c.putString("D", s.dim);
                c.putDouble("X", s.x);
                c.putDouble("Y", s.y);
                c.putDouble("Z", s.z);
                c.putDouble("C", s.sc);
                l.add(c);
            }
            t.put("S", l);
            return t;
        }
    }

    static Data data(ServerLevel any) {
        return any.getServer().overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "uwar_nukes");
    }

    static String dimOf(ServerLevel l) {
        return l.dimension().location().toString();
    }

    // ------------------------------------------------------------ explosion
    static class Job {
        final Site site;
        final ServerLevel lv;
        int ring = 0, anim = 0;
        final int maxRing;
        boolean scanning = true;

        Job(Site s, ServerLevel lv) {
            this.site = s;
            this.lv = lv;
            this.maxRing = (int) Math.ceil(s.maxR() / 16.0) + 2;
        }
    }

    static final List<Job> JOBS = new ArrayList<>();
    record Work(Site site, ServerLevel lv, long pos) {}
    static final ConcurrentLinkedQueue<Work> QUEUE = new ConcurrentLinkedQueue<>();
    /** Compteurs pour les tests. */
    public static int chunksDone = 0;

    public static boolean busy() {
        return !JOBS.isEmpty() || !QUEUE.isEmpty();
    }

    public static Site detonate(ServerLevel sl, Vec3 p, Entity owner) {
        return detonate(sl, p, owner, cfgScale());
    }

    public static Site detonate(ServerLevel sl, Vec3 p, Entity owner, double sc) {
        if (!Cfg.NUKE_ENABLED.get()) {
            Blast.boom(sl, p, 40f, 200f, null, owner, true);
            return null;
        }
        Site s = new Site(dimOf(sl), p.x, p.y, p.z, sc);
        Data d = data(sl);
        d.sites.add(s);
        d.setDirty();
        Job j = new Job(s, sl);
        JOBS.add(j);
        // son + flash chez tout le monde
        sl.playSound(null, p.x, p.y, p.z, Reg.BOOM.get(), SoundSource.HOSTILE, 400f, 0.35f);
        sl.playSound(null, p.x, p.y, p.z, Reg.BOOM.get(), SoundSource.HOSTILE, 400f, 0.6f);
        Net.CH.send(PacketDistributor.DIMENSION.with(sl::dimension), new Net.NukeFlash((float) p.x, (float) p.y, (float) p.z, (float) sc));
        for (ServerPlayer sp : sl.getServer().getPlayerList().getPlayers())
            sp.displayClientMessage(Component.literal("ALERTE : explosion nucleaire en " + (int) p.x + " " + (int) p.z), false);
        blastEntities(sl, s, owner);
        return s;
    }

    static boolean isAnimal(Entity e) {
        return e instanceof Animal || e instanceof WaterAnimal || e instanceof AmbientCreature;
    }

    static void kill(ServerLevel sl, Entity e, boolean player) {
        e.invulnerableTime = 0;
        if (player) e.hurt(sl.damageSources().explosion(null, null), 100000f);
        else e.hurt(sl.damageSources().genericKill(), 100000f);
        if (e.isAlive() && !(e instanceof Player)) e.discard();
    }

    /** Effet immediat du souffle sur les entites chargees. */
    static void blastEntities(ServerLevel sl, Site s, Entity owner) {
        double r0 = R0 * s.sc, r1 = R1 * s.sc, r2 = R2 * s.sc;
        List<Entity> all = new ArrayList<>();
        for (Entity e : sl.getAllEntities()) all.add(e);
        for (Entity e : all) {
            if (!(e instanceof LivingEntity) && !(e instanceof Aircraft)) continue;
            double d = s.hd(e.getX(), e.getZ());
            if (d > r2) continue;
            if (e instanceof Player pl) {
                if (d <= r1) kill(sl, pl, true);
                else {
                    pl.invulnerableTime = 0;
                    pl.hurt(sl.damageSources().explosion(null, null), 14f);
                    pl.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0));
                    pl.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 300, 0));
                }
            } else if (e instanceof Aircraft ac) {
                e.invulnerableTime = 0;
                if (d <= r1) e.hurt(sl.damageSources().explosion(null, null), 100000f);
            } else {
                kill(sl, e, false);
            }
        }
    }

    // ------------------------------------------------------------ blocs
    static boolean terrain(BlockState s) {
        if (s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER) || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND)) return true;
        Block b = s.getBlock();
        if (b == Blocks.GRAVEL || b == Blocks.SANDSTONE || b == Blocks.RED_SANDSTONE || b == Blocks.CLAY || b == Blocks.SNOW_BLOCK
                || b == Blocks.ICE || b == Blocks.PACKED_ICE || b == Blocks.BLUE_ICE || b == Blocks.WATER || b == Blocks.LAVA || b == Blocks.BEDROCK
                || b == Blocks.OBSIDIAN || b == Blocks.BLACKSTONE || b == Blocks.SMOOTH_BASALT || b == Blocks.COAL_BLOCK || b == Blocks.MOSS_BLOCK
                || b == Blocks.CALCITE || b == Blocks.DRIPSTONE_BLOCK || b == Blocks.END_STONE || b == Blocks.MAGMA_BLOCK || b == Blocks.POWDER_SNOW
                || b == Blocks.SUSPICIOUS_SAND || b == Blocks.SUSPICIOUS_GRAVEL) return true;
        String n = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(b).getPath();
        return n.endsWith("_ore") || n.contains("terracotta") && !n.contains("glazed") || n.endsWith("netherrack") || n.endsWith("nylium") || n.equals("soul_sand") || n.equals("soul_soil") || n.equals("basalt");
    }

    static BlockState charred(net.minecraft.util.RandomSource r) {
        float f = r.nextFloat();
        return f < 0.07f ? Blocks.COAL_BLOCK.defaultBlockState() : f < 0.55f ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
    }

    /** Ecriture rapide dans une section (sans mise a jour de voisins), avec suivi des colonnes modifiees. */
    static void put(LevelChunk c, int lx, int y, int lz, BlockState st, int[] lo, int[] hi, int wx, int wz) {
        LevelChunkSection sec = c.getSection(c.getSectionIndex(y));
        BlockState old = sec.getBlockState(lx, y & 15, lz);
        if (old == st) return;
        if (old.hasBlockEntity()) c.removeBlockEntity(new BlockPos(wx, y, wz));
        sec.setBlockState(lx, y & 15, lz, st, false);
        int i = lz * 16 + lx;
        if (lo[i] == Integer.MAX_VALUE || y < lo[i]) lo[i] = y;
        if (y > hi[i]) hi[i] = y;
    }

    static boolean processChunk(Site s, ServerLevel lv, LevelChunk c) {
        ChunkPos cp = c.getPos();
        long key = cp.toLong();
        if (!s.done.add(key)) return false;
        double r0 = R0 * s.sc, r1 = R1 * s.sc, r2 = R2 * s.sc, r3 = R3 * s.sc;
        int minY = lv.getMinBuildHeight();
        int[] lo = new int[256], hi = new int[256];
        java.util.Arrays.fill(lo, Integer.MAX_VALUE);
        java.util.Arrays.fill(hi, Integer.MIN_VALUE);
        BlockState air = Blocks.AIR.defaultBlockState();
        var rnd = lv.random;
        double depthMax = Math.max(4.0, 70.0 * Math.sqrt(s.sc));
        boolean any = false;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = cp.getMinBlockX() + lx, wz = cp.getMinBlockZ() + lz;
                double d = s.hd(wx + 0.5, wz + 0.5);
                if (d > r3) continue;
                int top = c.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz) - 1;
                if (top < minY) continue;
                if (d < r0) {
                    double q = d / r0;
                    int floor = (int) Math.ceil(s.y - depthMax * Math.sqrt(Math.max(0, 1 - q * q)));
                    for (int y = top; y >= Math.max(floor, minY); y--) {
                        BlockState st = c.getBlockState(new BlockPos(wx, y, wz));
                        if (st.isAir() || st.getBlock().defaultDestroyTime() < 0) continue;
                        put(c, lx, y, lz, air, lo, hi, wx, wz);
                        any = true;
                    }
                } else if (d < r2) {
                    // detruit tout ce qui n'est pas du terrain ; zone carbonisee : le sol devient noir
                    int y = top, lim = Math.max(minY, top - 90);
                    boolean carbon = d < r1;
                    BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
                    while (y >= lim) {
                        BlockState st = c.getBlockState(mp.set(wx, y, wz));
                        if (st.isAir()) {
                            y--;
                            continue;
                        }
                        if (terrain(st)) break;
                        if (st.getBlock().defaultDestroyTime() >= 0) {
                            put(c, lx, y, lz, air, lo, hi, wx, wz);
                            any = true;
                        }
                        y--;
                    }
                    if (carbon && y >= lim) {
                        for (int k = 0; k < 3 && y - k >= minY; k++) {
                            BlockState st = c.getBlockState(mp.set(wx, y - k, wz));
                            if (st.is(BlockTags.DIRT) || st.is(BlockTags.SAND) || st.getBlock() == Blocks.GRAVEL || st.getBlock() == Blocks.SNOW_BLOCK) {
                                put(c, lx, y - k, lz, charred(rnd), lo, hi, wx, wz);
                                any = true;
                            }
                        }
                    }
                } else {
                    // arbres sans feuilles
                    int lim = Math.max(minY, top - 70);
                    BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
                    for (int y = top; y >= lim; y--) {
                        BlockState st = c.getBlockState(mp.set(wx, y, wz));
                        if (st.is(BlockTags.LEAVES)) {
                            put(c, lx, y, lz, air, lo, hi, wx, wz);
                            any = true;
                        }
                    }
                }
            }
        }
        if (any) {
            Heightmap.primeHeightmaps(c, EnumSet.of(Heightmap.Types.WORLD_SURFACE, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES));
            var le = lv.getLightEngine();
            for (int i = 0; i < 256; i++) {
                if (lo[i] == Integer.MAX_VALUE) continue;
                int wx = cp.getMinBlockX() + (i & 15), wz = cp.getMinBlockZ() + (i >> 4);
                le.checkBlock(new BlockPos(wx, lo[i], wz));
                if (hi[i] != lo[i]) le.checkBlock(new BlockPos(wx, hi[i], wz));
            }
            c.setUnsaved(true);
            for (ServerPlayer p : lv.getChunkSource().chunkMap.getPlayers(cp, false))
                p.connection.send(new ClientboundLevelChunkWithLightPacket(c, lv.getLightEngine(), null, null));
        }
        if (System.getProperty("uwar.selftest") != null && cp.z == 6 && cp.x >= -9 && cp.x <= 2)
            System.out.println("NUKEDBG chunk " + cp + " any=" + any + " site=(" + s.x + "," + s.y + "," + s.z + ") sc=" + s.sc + " top0=" + c.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8));
        chunksDone++;
        return true;
    }

    // ------------------------------------------------------------ effets permanents
    static int periodic = 0;

    static void maintain(ServerLevel lv, List<Site> sites) {
        for (Entity e : lv.getAllEntities()) {
            if (!(e instanceof LivingEntity le) || !e.isAlive()) continue;
            for (Site s : sites) {
                double d = s.hd(e.getX(), e.getZ());
                if (d > s.maxR()) continue;
                if (isAnimal(e) && !(e instanceof Player)) {
                    kill(lv, e, false);
                    break;
                }
                if (d <= R2 * s.sc && !(e instanceof Aircraft)) {
                    int amp = d <= R1 * s.sc ? 1 : 0;
                    le.addEffect(new MobEffectInstance(MobEffects.POISON, 140, amp, false, true));
                    if (e instanceof Player) le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 140, 0, false, false));
                    break;
                }
            }
        }
    }

    // ------------------------------------------------------------ animation du champignon
    static void particles(ServerLevel lv, Site s, ParticleOptions t, double x, double y, double z, int n, double dx, double dy, double dz, double sp) {
        for (ServerPlayer p : lv.players()) {
            double ddx = p.getX() - x, ddz = p.getZ() - z;
            if (ddx * ddx + ddz * ddz > 700 * 700) continue;
            lv.sendParticles(p, t, true, x, y, z, n, dx, dy, dz, sp);
        }
    }

    static void animate(Job j) {
        Site s = j.site;
        ServerLevel lv = j.lv;
        int t = j.anim++;
        double v = Math.max(0.12, Math.sqrt(s.sc));
        var rnd = lv.random;
        if (t == 0) particles(lv, s, ParticleTypes.EXPLOSION_EMITTER, s.x, s.y + 2, s.z, 4, 6 * v, 3, 6 * v, 0);
        if (t < 40) {
            double r = (t + 1) * 2.6 * v;
            for (int i = 0; i < 60; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, b = rnd.nextDouble() * Math.PI - Math.PI / 2;
                double px = s.x + Math.cos(a) * Math.cos(b) * r, py = s.y + Math.max(0.5, r * 0.55 + Math.sin(b) * r * 0.55), pz = s.z + Math.sin(a) * Math.cos(b) * r;
                particles(lv, s, i % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, px, py, pz, 1, 0.2, 0.2, 0.2, 0.02);
            }
        }
        if (t < 90) {
            double r = t * 7 * v;
            for (int i = 0; i < 90; i++) {
                double a = i * Math.PI * 2 / 90 + rnd.nextDouble() * 0.05;
                particles(lv, s, ParticleTypes.CLOUD, s.x + Math.cos(a) * r, s.y + 0.6, s.z + Math.sin(a) * r, 0, Math.cos(a), 0.0, Math.sin(a), 0.05);
            }
        }
        if (t >= 8 && t < 260) {
            double h = Math.min(250, (t - 8) * 2.2) * v * 1.2;
            for (int k = 0; k < 6; k++) {
                double hh = rnd.nextDouble() * h;
                double w = (6 + hh * 0.04) * v;
                particles(lv, s, k % 2 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE, s.x + (rnd.nextDouble() - 0.5) * w, s.y + hh, s.z + (rnd.nextDouble() - 0.5) * w, 1, 0.5, 0.5, 0.5, 0.02);
                if (hh < h * 0.5) particles(lv, s, ParticleTypes.FLAME, s.x + (rnd.nextDouble() - 0.5) * w, s.y + hh, s.z + (rnd.nextDouble() - 0.5) * w, 1, 0.3, 0.3, 0.3, 0.01);
            }
            if (t > 60) {
                double ch = s.y + Math.min(250, (60 - 8) * 2.2 + (t - 60) * 0.6) * v * 1.2;
                double cr = (30 + Math.min(60, (t - 60) * 0.5)) * v;
                for (int i = 0; i < 40; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    double rr = cr * (0.75 + rnd.nextDouble() * 0.35);
                    particles(lv, s, i % 4 == 0 ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE, s.x + Math.cos(a) * rr, ch + (rnd.nextDouble() - 0.5) * 18 * v, s.z + Math.sin(a) * rr, 1, 0.4, 0.4, 0.4, 0.02);
                }
            }
        }
    }

    // ------------------------------------------------------------ boucle serveur
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        long t0 = System.nanoTime();
        for (var it = JOBS.iterator(); it.hasNext(); ) {
            Job j = it.next();
            if (j.anim < 300) animate(j);
            if (j.scanning) {
                int budget = 12000;
                while (budget > 0 && j.ring <= j.maxRing) {
                    ChunkPos c0 = new ChunkPos(BlockPos.containing(j.site.x, 0, j.site.z));
                    int r = j.ring++;
                    int n = 0;
                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                            n++;
                            LevelChunk c = j.lv.getChunkSource().getChunkNow(c0.x + dx, c0.z + dz);
                            if (c != null) QUEUE.add(new Work(j.site, j.lv, c.getPos().toLong()));
                        }
                    }
                    budget -= Math.max(n, 8);
                }
                if (j.ring > j.maxRing) j.scanning = false;
            }
            if (!j.scanning && j.anim >= 300) it.remove();
        }
        while ((System.nanoTime() - t0) < 22_000_000L) {
            Work w = QUEUE.poll();
            if (w == null) break;
            ChunkPos cp = new ChunkPos(w.pos());
            LevelChunk c = w.lv().getChunkSource().getChunkNow(cp.x, cp.z);
            if (c != null) processChunk(w.site(), w.lv(), c);
        }
        if (++periodic >= 20) {
            periodic = 0;
            var srv = e.getServer();
            if (srv == null) return;
            for (ServerLevel lv : srv.getAllLevels()) {
                List<Site> sl = new ArrayList<>();
                for (Site s : data(lv).sites) if (s.dim.equals(dimOf(lv))) sl.add(s);
                if (!sl.isEmpty()) maintain(lv, sl);
            }
        }
    }

    @SubscribeEvent
    public static void chunkLoad(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel lv) || !(e.getChunk() instanceof LevelChunk c)) return;
        var srv = lv.getServer();
        if (srv == null || srv.overworld() == null) return;
        String dim = dimOf(lv);
        ChunkPos cp = c.getPos();
        double cx = cp.getMinBlockX() + 8, cz = cp.getMinBlockZ() + 8;
        for (Site s : data(lv).sites) {
            if (!s.dim.equals(dim)) continue;
            if (s.hd(cx, cz) > s.maxR() + 24) continue;
            if (s.done.contains(cp.toLong())) continue;
            QUEUE.add(new Work(s, lv, cp.toLong()));
        }
    }
}
