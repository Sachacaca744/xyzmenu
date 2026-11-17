package fr.uwar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Effets des bombes speciales (incendiaire, acide, sous-munitions, thermobarique, gaz, sismique, foudre). */
@Mod.EventBusSubscriber(modid = Uwar.MODID)
public class Payload {
    interface Step {
        void run(Task t);
    }

    static class Task {
        final ServerLevel lv;
        final Vec3 c;
        final W w;
        final Entity owner;
        final Step step;
        final int life, every;
        int age = 0;

        Task(ServerLevel lv, Vec3 c, W w, Entity owner, int life, int every, Step s) {
            this.lv = lv;
            this.c = c;
            this.w = w;
            this.owner = owner;
            this.life = life;
            this.every = every;
            this.step = s;
        }
    }

    static final List<Task> TASKS = new ArrayList<>();

    public static int active() {
        return TASKS.size();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || TASKS.isEmpty()) return;
        for (int i = TASKS.size() - 1; i >= 0; i--) {
            Task t = TASKS.get(i);
            try {
                if (t.age % t.every == 0) t.step.run(t);
            } catch (Throwable ex) {
                System.out.println("UWAR payload erreur " + t.w + " : " + ex);
                t.age = t.life;
            }
            if (++t.age >= t.life) TASKS.remove(i);
        }
    }

    static boolean breakable(ServerLevel lv, BlockPos p, BlockState st) {
        return !st.isAir() && st.getDestroySpeed(lv, p) >= 0 && st.getBlock().getExplosionResistance() < 100f;
    }

    static boolean blocksOn() {
        return Cfg.HEAVY_BLOCKS.get();
    }

    static BlockPos surface(ServerLevel lv, double x, double z) {
        return lv.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, 0, z));
    }

    static void spark(ServerLevel lv, ParticleOptions p, double x, double y, double z, int n, double sp) {
        lv.sendParticles(p, x, y, z, n, 1.2, 0.8, 1.2, sp);
    }

    static List<LivingEntity> living(ServerLevel lv, Vec3 c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : lv.getEntities((Entity) null, new AABB(c, c).inflate(r), x -> x instanceof LivingEntity && x.isAlive() && !(x instanceof ArmorStand))) {
            if (e.position().distanceTo(c) <= r + 1) out.add((LivingEntity) e);
        }
        return out;
    }

    public static void detonate(ServerLevel sl, W w, Vec3 p, Entity owner) {
        sl.playSound(null, p.x, p.y, p.z, Reg.BOOM.get(), SoundSource.NEUTRAL, 3.0f, w == W.THERMO ? 0.5f : 1.0f);
        switch (w) {
            case INCEN -> incendiary(sl, p, owner);
            case ACID -> acid(sl, p, owner);
            case CLUSTER -> cluster(sl, p, owner);
            case THERMO -> thermo(sl, p, owner);
            case GAS -> gas(sl, p, owner);
            case SEISMIC -> seismic(sl, p, owner);
            case LIGHT -> lightning(sl, p, owner);
            default -> Blast.boom(sl, p, w.radius, w.dmg, null, owner, w.heavy);
        }
    }

    // ----------------------------------------------------------------- incendiaire
    static void incendiary(ServerLevel sl, Vec3 p, Entity owner) {
        Blast.boom(sl, p, 5f, 20f, null, owner, false);
        double R = 14;
        BlockPos c = BlockPos.containing(p);
        int placed = 0;
        for (int i = 0; i < 520; i++) {
            double a = sl.random.nextDouble() * Math.PI * 2, d = Math.sqrt(sl.random.nextDouble()) * R;
            BlockPos s = surface(sl, p.x + Math.cos(a) * d, p.z + Math.sin(a) * d);
            if (Math.abs(s.getY() - c.getY()) > 10) continue;
            if (sl.getBlockState(s).isAir() && Blocks.FIRE.defaultBlockState().canSurvive(sl, s) && sl.random.nextFloat() < 0.65f) {
                if (blocksOn()) sl.setBlock(s, Blocks.FIRE.defaultBlockState(), 3);
                placed++;
            }
        }
        for (LivingEntity e : living(sl, p, R)) {
            e.setSecondsOnFire(12);
            e.invulnerableTime = 0;
            e.hurt(sl.damageSources().inFire(), 6f);
        }
        sl.sendParticles(ParticleTypes.FLAME, p.x, p.y + 1, p.z, 300, R * 0.4, 1.5, R * 0.4, 0.06);
        sl.sendParticles(ParticleTypes.LAVA, p.x, p.y + 1, p.z, 60, R * 0.3, 1, R * 0.3, 0);
        // brasier durable : les creatures qui restent brulent
        TASKS.add(new Task(sl, p, W.INCEN, owner, 240, 10, t -> {
            for (LivingEntity e : living(t.lv, t.c, 11)) {
                if (!e.fireImmune()) e.setSecondsOnFire(6);
            }
            t.lv.sendParticles(ParticleTypes.FLAME, t.c.x, t.c.y + 0.5, t.c.z, 30, 5, 0.6, 5, 0.02);
            t.lv.sendParticles(ParticleTypes.LARGE_SMOKE, t.c.x, t.c.y + 2, t.c.z, 8, 4, 1, 4, 0.02);
        }));
    }

    // ----------------------------------------------------------------- acide
    static void acid(ServerLevel sl, Vec3 p, Entity owner) {
        sl.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y + 0.5, p.z, 4, 1.5, 0.5, 1.5, 0);
        TASKS.add(new Task(sl, p, W.ACID, owner, 200, 4, t -> {
            double depth = t.age * 0.055;
            Vec3 cc = new Vec3(t.c.x, t.c.y - depth, t.c.z);
            double R = 6.5;
            for (int i = 0; i < 70; i++) {
                double dx = (t.lv.random.nextDouble() * 2 - 1) * R, dy = (t.lv.random.nextDouble() * 2 - 1) * R, dz = (t.lv.random.nextDouble() * 2 - 1) * R;
                if (dx * dx + dy * dy + dz * dz > R * R) continue;
                BlockPos bp = BlockPos.containing(cc.x + dx, cc.y + dy, cc.z + dz);
                BlockState st = t.lv.getBlockState(bp);
                if (blocksOn() && breakable(t.lv, bp, st)) {
                    t.lv.setBlock(bp, st.getFluidState().isEmpty() ? Blocks.AIR.defaultBlockState() : st.getFluidState().createLegacyBlock(), 3);
                    t.lv.sendParticles(ParticleTypes.COMPOSTER, bp.getX() + 0.5, bp.getY() + 0.8, bp.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.02);
                }
            }
            t.lv.sendParticles(ParticleTypes.SNEEZE, t.c.x, t.c.y + 0.8, t.c.z, 25, 3.5, 0.5, 3.5, 0.02);
            t.lv.sendParticles(ParticleTypes.COMPOSTER, t.c.x, t.c.y + 0.4, t.c.z, 20, 4, 0.4, 4, 0.03);
            if (t.age % 8 == 0) {
                t.lv.playSound(null, t.c.x, t.c.y, t.c.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.NEUTRAL, 1.2f, 0.7f);
                for (LivingEntity e : living(t.lv, new Vec3(t.c.x, t.c.y - depth * 0.5, t.c.z), 8)) {
                    e.invulnerableTime = 0;
                    e.hurt(t.lv.damageSources().magic(), 3.0f);
                    e.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                }
            }
        }));
    }

    // ----------------------------------------------------------------- sous-munitions
    static void cluster(ServerLevel sl, Vec3 p, Entity owner) {
        TASKS.add(new Task(sl, p, W.CLUSTER, owner, 60, 2, t -> {
            if (t.age >= 48) return;
            for (int k = 0; k < 1; k++) {
                double a = t.lv.random.nextDouble() * Math.PI * 2, d = Math.sqrt(t.lv.random.nextDouble()) * 22;
                BlockPos s = surface(t.lv, t.c.x + Math.cos(a) * d, t.c.z + Math.sin(a) * d);
                double y = Math.abs(s.getY() - t.c.y) > 12 ? t.c.y : s.getY();
                Blast.boom(t.lv, new Vec3(t.c.x + Math.cos(a) * d, y + 0.5, t.c.z + Math.sin(a) * d), 3.5f, 22f, null, t.owner, false);
            }
        }));
    }

    /** Nombre de petites explosions prevues par la bombe a sous-munitions. */
    public static final int CLUSTER_BOOMS = 24;

    // ----------------------------------------------------------------- thermobarique
    static void thermo(ServerLevel sl, Vec3 p, Entity owner) {
        sl.sendParticles(ParticleTypes.CLOUD, p.x, p.y + 1, p.z, 120, 6, 2, 6, 0.01);
        TASKS.add(new Task(sl, p, W.THERMO, owner, 14, 1, t -> {
            if (t.age < 10) {
                for (LivingEntity e : living(t.lv, t.c, 28)) {
                    Vec3 to = t.c.subtract(e.position());
                    if (to.length() < 2) continue;
                    Vec3 v = to.normalize().scale(0.45);
                    e.push(v.x, 0.05, v.z);
                    e.hurtMarked = true;
                }
                t.lv.sendParticles(ParticleTypes.CLOUD, t.c.x, t.c.y + 1.5, t.c.z, 40, 7, 2, 7, -0.06);
                if (t.age == 0) t.lv.playSound(null, t.c.x, t.c.y, t.c.z, SoundEvents.PORTAL_AMBIENT, SoundSource.NEUTRAL, 3.0f, 0.5f);
            } else if (t.age == 10) {
                Blast.boom(t.lv, t.c, W.THERMO.radius, W.THERMO.dmg, null, t.owner, true);
                t.lv.sendParticles(ParticleTypes.FLAME, t.c.x, t.c.y + 3, t.c.z, 500, 8, 4, 8, 0.25);
            }
        }));
    }

    // ----------------------------------------------------------------- gaz
    static void gas(ServerLevel sl, Vec3 p, Entity owner) {
        TASKS.add(new Task(sl, p, W.GAS, owner, 600, 10, t -> {
            t.lv.sendParticles(ParticleTypes.SNEEZE, t.c.x, t.c.y + 1.5, t.c.z, 60, 7, 1.5, 7, 0.01);
            t.lv.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, t.c.x, t.c.y + 1.5, t.c.z, 40, 7, 1.5, 7, 0.0);
            for (LivingEntity e : living(t.lv, new Vec3(t.c.x, t.c.y + 1.5, t.c.z), 12)) {
                e.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
                e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
            }
        }));
    }

    // ----------------------------------------------------------------- sismique
    static void seismic(ServerLevel sl, Vec3 p, Entity owner) {
        sl.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 5f, 0.4f);
        TASKS.add(new Task(sl, p, W.SEISMIC, owner, 80, 2, t -> {
            double R = 40;
            for (int i = 0; i < 70; i++) {
                double a = t.lv.random.nextDouble() * Math.PI * 2, d = Math.sqrt(t.lv.random.nextDouble()) * R;
                BlockPos s = surface(t.lv, t.c.x + Math.cos(a) * d, t.c.z + Math.sin(a) * d).below();
                if (Math.abs(s.getY() - t.c.y) > 14) continue;
                BlockState st = t.lv.getBlockState(s);
                if (blocksOn() && breakable(t.lv, s, st)) {
                    t.lv.setBlock(s, Blocks.AIR.defaultBlockState(), 3);
                    if (t.lv.random.nextFloat() < 0.5f) {
                        BlockPos s2 = s.below();
                        BlockState st2 = t.lv.getBlockState(s2);
                        if (breakable(t.lv, s2, st2)) t.lv.setBlock(s2, Blocks.AIR.defaultBlockState(), 3);
                    }
                    t.lv.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, s.getX() + 0.5, s.getY() + 1.0, s.getZ() + 0.5, 1, 0.2, 0.1, 0.2, 0.02);
                }
            }
            for (LivingEntity e : living(t.lv, t.c, R + 5)) {
                e.invulnerableTime = 0;
                e.hurt(t.lv.damageSources().magic(), 2.0f);
                e.push((t.lv.random.nextDouble() - 0.5) * 0.5, 0.45, (t.lv.random.nextDouble() - 0.5) * 0.5);
                e.hurtMarked = true;
            }
            if (t.age % 6 == 0) t.lv.playSound(null, t.c.x, t.c.y, t.c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 3f, 0.3f);
        }));
    }

    // ----------------------------------------------------------------- foudre
    static void bolt(ServerLevel lv, double x, double y, double z) {
        LightningBolt b = EntityType.LIGHTNING_BOLT.create(lv);
        if (b == null) return;
        b.moveTo(x, y, z);
        lv.addFreshEntity(b);
    }

    static void lightning(ServerLevel sl, Vec3 p, Entity owner) {
        TASKS.add(new Task(sl, p, W.LIGHT, owner, 60, 3, t -> {
            double R = 30;
            List<LivingEntity> ls = living(t.lv, t.c, R + 8);
            ls.removeIf(e -> e == t.owner || (t.owner != null && e == t.owner.getVehicle()));
            if (!ls.isEmpty() && t.lv.random.nextFloat() < 0.7f) {
                LivingEntity e = ls.get(t.lv.random.nextInt(ls.size()));
                bolt(t.lv, e.getX(), e.getY(), e.getZ());
            } else {
                double a = t.lv.random.nextDouble() * Math.PI * 2, d = Math.sqrt(t.lv.random.nextDouble()) * R;
                BlockPos s = surface(t.lv, t.c.x + Math.cos(a) * d, t.c.z + Math.sin(a) * d);
                bolt(t.lv, s.getX() + 0.5, s.getY(), s.getZ() + 0.5);
            }
        }));
    }
}
