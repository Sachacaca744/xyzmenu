package fr.ugta;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Pilotage automatique : voiture de police conduite par un Cop, helicoptere de police. */
public class VehicleAI {
    public static void drive(Vehicle v, LivingEntity d) {
        if (v.kind == Kind.POLICE_HELI) {
            heli(v);
            return;
        }
        if (!(d instanceof Cop cop)) return;
        LivingEntity t = cop.getTarget();
        if (t == null || !t.isAlive()) {
            v.setSiren(false);
            return;
        }
        v.setSiren(true);
        double dx = t.getX() - v.getX(), dz = t.getZ() - v.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 14) {
            v.ejectPassengers();
            return;
        }
        float want = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
        float diff = Mth.wrapDegrees(want - v.getYRot());
        v.in_t = -Mth.clamp(diff / 12f, -1f, 1f);
        v.in_f = 1;
        if (v.reverseTicks() > 0) {
            v.in_f = -1;
            v.in_t = -v.in_t;
            v.setReverse(v.reverseTicks() - 1);
            return;
        }
        if (Math.abs(v.speed) < 0.05) v.setStuck(v.stuckTicks() + 1);
        else v.setStuck(0);
        if (v.stuckTicks() > 25) {
            v.setReverse(25);
            v.setStuck(0);
        }
        if (Math.abs(diff) > 70 && v.speed > 0.5) v.in_f = 0.2f;
    }

    private static void heli(Vehicle v) {
        Player t = v.targetId == null ? null : v.level().getPlayerByUUID(v.targetId);
        boolean ok = t != null && t.isAlive() && Wanted.stars(t) >= 3;
        if (!ok) {
            v.addLeaving();
            v.in_u = 1;
            v.aiVx *= 0.9;
            v.aiVz *= 0.9;
            if (v.leavingTicks() > 160) v.discard();
            return;
        }
        v.setHeliAngle(v.heliAngle() + 0.02f);
        double gx = t.getX() + Math.cos(v.heliAngle()) * 16, gz = t.getZ() + Math.sin(v.heliAngle()) * 16, gy = t.getY() + 22;
        double dx = gx - v.getX(), dz = gz - v.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        double sp = Math.min(len * 0.06, 0.8);
        v.aiVx = len > 0.5 ? dx / len * sp : 0;
        v.aiVz = len > 0.5 ? dz / len * sp : 0;
        double dy = gy - v.getY();
        v.in_u = dy > 2 ? 1 : dy < -2 ? -1 : 0;
        float want = (float) (Mth.atan2(-(t.getX() - v.getX()), t.getZ() - v.getZ()) * Mth.RAD_TO_DEG);
        v.setYRot(v.getYRot() + Mth.clamp(Mth.wrapDegrees(want - v.getYRot()), -4f, 4f));
        if (v.tickCount % 8 == 0 && v.distanceTo(t) < 55 && Wanted.canShootAir(t) && v.level() instanceof ServerLevel sl) {
            Vec3 o = v.position().add(0, 0.6, 0);
            Gun.shoot(sl, v, o, t.position().add(0, t.getBbHeight() * 0.6, 0), 2.5f, 0.06, Reg.SHOT.get(), 1.3f);
        }
    }
}
