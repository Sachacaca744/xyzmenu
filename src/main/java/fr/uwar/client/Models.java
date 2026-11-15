package fr.uwar.client;

import fr.uwar.Kind;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static fr.uwar.client.Mesh.*;

/** Modeles 3D proceduraux. Repere : +Z = avant, +X = gauche, y=0 = sol, unite = 1 bloc. */
public class Models {
    public static class Ord {
        public Mesh m;
        public int weapon, idx;
    }

    public static class Model {
        public final Mesh body = new Mesh(), gear = new Mesh(), flame = new Mesh();
        public final Mesh rotor = new Mesh(), rotorDisc = new Mesh(), tail = new Mesh(), tailDisc = new Mesh();
        public float rx, ry, rz, tx, ty, tz;
        public final List<Ord> ord = new ArrayList<>();
        public final List<float[]> missSt = new ArrayList<>(), bombSt = new ArrayList<>();
        public float pivotY = 1.3f;
    }

    private static final Map<Kind, Model> CACHE = new EnumMap<>(Kind.class);
    public static Mesh bomb = new Mesh(), missile = new Mesh(), rocket = new Mesh();

    public static synchronized Model get(Kind k) {
        return CACHE.computeIfAbsent(k, kk -> kk.heli() ? heli() : jet());
    }

    static void buildBomb(Mesh b) {
        b.revolve(STEEL, 'z', 0, 0, 0, new float[][]{{-0.45f, 0.03f}, {-0.3f, 0.09f}, {0f, 0.125f}, {0.25f, 0.11f}, {0.42f, 0.06f}, {0.48f, 0.0f}}, 12);
        b.revolve(ACC, 'z', 0, 0, 0.12f, new float[][]{{0f, 0.126f}, {0.05f, 0.126f}}, 12);
        for (int i = 0; i < 4; i++) b.push().rotZ(i * 90 + 45).box(HB, 0, 0.12f, -0.38f, 0.008f, 0.07f, 0.09f).pop();
    }

    static void buildMissile(Mesh m) {
        m.revolve(MET, 'z', 0, 0, 0, new float[][]{{-0.65f, 0.05f}, {-0.55f, 0.07f}, {0.3f, 0.07f}, {0.5f, 0.05f}, {0.65f, 0.0f}}, 10);
        m.revolve(ACC, 'z', 0, 0, 0.1f, new float[][]{{0f, 0.071f}, {0.06f, 0.071f}}, 10);
        for (int i = 0; i < 4; i++) {
            m.push().rotZ(i * 90).box(STEEL, 0, 0.1f, -0.56f, 0.005f, 0.06f, 0.07f).pop();
            m.push().rotZ(i * 90 + 45).box(STEEL, 0, 0.07f, 0.15f, 0.004f, 0.04f, 0.06f).pop();
        }
    }

    static void buildRocket(Mesh m) {
        m.revolve(MET, 'z', 0, 0, 0, new float[][]{{-0.5f, 0.05f}, {-0.4f, 0.07f}, {0.25f, 0.07f}, {0.42f, 0.04f}, {0.5f, 0.0f}}, 8);
        m.revolve(ACC, 'z', 0, 0, 0.08f, new float[][]{{0f, 0.071f}, {0.05f, 0.071f}}, 8);
        for (int i = 0; i < 4; i++) m.push().rotZ(i * 90 + 45).box(STEEL, 0, 0.08f, -0.42f, 0.005f, 0.05f, 0.06f).pop();
        // flamme de propulsion
        m.revolve(FLAME, 'z', 0, 0, -0.75f, new float[][]{{-0.35f, 0.0f}, {-0.1f, 0.05f}, {0.2f, 0.06f}}, 8);
    }

    static {
        buildBomb(bomb);
        buildMissile(missile);
        buildRocket(rocket);
    }

    private static Ord ord(Model m, int weapon, int idx, float x, float y, float z, float scale, int kind) {
        Ord o = new Ord();
        o.weapon = weapon;
        o.idx = idx;
        o.m = new Mesh();
        o.m.push().translate(x, y, z).scale(scale);
        switch (kind) {
            case 0 -> buildMissile(o.m);
            case 1 -> buildBomb(o.m);
            case 2 -> {
                // nacelle de roquettes
                o.m.revolve(MET, 'z', 0, 0, 0, new float[][]{{-0.5f, 0.2f}, {-0.45f, 0.24f}, {0.45f, 0.24f}, {0.5f, 0.2f}}, 10);
                o.m.revolve(ACC, 'z', 0, 0, 0.42f, new float[][]{{0f, 0.245f}, {0.06f, 0.245f}}, 10);
            }
            default -> {
            }
        }
        o.m.pop();
        m.ord.add(o);
        return o;
    }

    private static void wheel(Mesh b, float x, float y, float z, float r, float w) {
        b.tube(RUB, 'x', x, y, z, r, r, w, 10);
        b.tube(MET, 'x', x, y, z, r * 0.5f, r * 0.5f, w + 0.02f, 8);
    }

    // ------------------------------------------------------------------ hélicoptère d'attaque
    static Model heli() {
        Model m = new Model();
        Mesh b = m.body;
        b.loft(CAMO, new float[][]{{3.9f, 0.95f, 0.10f, 0.10f}, {3.4f, 1.05f, 0.38f, 0.40f}, {2.6f, 1.2f, 0.62f, 0.70f}, {1.4f, 1.35f, 0.78f, 0.88f},
                {0.0f, 1.4f, 0.80f, 0.92f}, {-1.4f, 1.45f, 0.70f, 0.80f}, {-2.4f, 1.55f, 0.50f, 0.55f}}, 0, 2.4f, 12);
        b.loft(HA, new float[][]{{-2.2f, 1.6f, 0.42f, 0.45f}, {-4.0f, 1.75f, 0.30f, 0.33f}, {-6.0f, 1.95f, 0.18f, 0.20f}, {-6.4f, 2.0f, 0.14f, 0.16f}}, 0, 2.2f, 10);
        b.ellipsoid(LENS, 0, 1.88f, 2.65f, 0.40f, 0.34f, 0.85f, 12, 8);
        b.ellipsoid(LENS, 0, 2.18f, 1.15f, 0.44f, 0.40f, 0.90f, 12, 8);
        // moteurs
        for (int s = -1; s <= 1; s += 2) {
            b.ellipsoid(HB, s * 0.95f, 1.98f, -0.5f, 0.38f, 0.36f, 1.2f, 10, 6);
            b.box(MET, s * 0.95f, 1.98f, 0.75f, 0.28f, 0.26f, 0.04f);
            b.revolve(STEEL, 'z', s * 0.95f, 1.98f, -1.85f, new float[][]{{-0.2f, 0.3f}, {0.2f, 0.34f}}, 10);
        }
        // mat + moyeu
        b.tube(MET, 'y', 0, 2.6f, -0.3f, 0.14f, 0.1f, 0.6f, 8);
        b.box(MET, 0, 2.95f, -0.3f, 0.2f, 0.08f, 0.2f);
        // empennage
        b.push().translate(0, 2.4f, -5.9f).rotX(18).box(HA, 0, 0, 0, 0.05f, 0.7f, 0.45f).pop();
        b.box(HB, 0, 1.95f, -5.5f, 1.1f, 0.04f, 0.3f);
        b.box(RLED, 0, 3.05f, -6.2f, 0.05f, 0.05f, 0.05f);
        // ailes courtes
        b.box(HB, 0, 1.15f, 0.3f, 1.9f, 0.05f, 0.4f);
        b.box(LED, 1.92f, 1.15f, 0.3f, 0.05f, 0.06f, 0.06f);
        b.box(RLED, -1.92f, 1.15f, 0.3f, 0.05f, 0.06f, 0.06f);
        // canon de nez
        b.box(MET, 0, 0.72f, 3.1f, 0.12f, 0.12f, 0.35f);
        b.tube(STEEL, 'z', 0, 0.72f, 3.95f, 0.05f, 0.05f, 1.1f, 6);
        // capteur de nez
        b.ellipsoid(MET, 0, 1.05f, 3.75f, 0.16f, 0.16f, 0.22f, 8, 5);
        // train
        Mesh g = m.gear;
        for (int s = -1; s <= 1; s += 2) {
            g.box(MET, s * 0.78f, 0.68f, 0.6f, 0.04f, 0.32f, 0.04f);
            wheel(g, s * 0.8f, 0.3f, 0.6f, 0.3f, 0.2f);
        }
        g.box(MET, 0, 1.1f, -4.8f, 0.03f, 0.8f, 0.03f);
        wheel(g, 0, 0.3f, -4.8f, 0.18f, 0.12f);
        // helices
        m.rx = 0;
        m.ry = 3.05f;
        m.rz = -0.3f;
        m.rotor.blades(4, 5.6f, 0.42f);
        m.rotorDisc.disc(BLUR, 5.6f, 28);
        m.tx = 0.25f;
        m.ty = 2.05f;
        m.tz = -6.35f;
        m.tail.blades(4, 0.95f, 0.16f);
        m.tailDisc.disc(BLUR, 0.95f, 16);
        // munitions visibles
        for (int s = -1; s <= 1; s += 2) {
            int i = s < 0 ? 0 : 1;
            ord(m, 1, i, s * 1.1f, 0.88f, 0.4f, 1.8f, 2);
            m.missSt.add(new float[]{s * 1.75f, 0.92f, 0.45f});
            m.missSt.add(new float[]{s * 2.2f, 0.92f, 0.45f});
        }
        m.pivotY = 1.4f;
        return m;
    }

    // ------------------------------------------------------------------ avion de chasse
    private static void wing(Mesh b, int mat, float s, float[][] sec) {
        float[][] t = new float[sec.length][];
        for (int i = 0; i < sec.length; i++) t[i] = new float[]{sec[i][0] * s, sec[i][1], sec[i][2], sec[i][3], sec[i][4]};
        b.foil(mat, t);
    }

    static Model jet() {
        Model m = new Model();
        Mesh b = m.body;
        b.loft(CAMO, new float[][]{{6.2f, 1.0f, 0.03f, 0.03f}, {5.4f, 1.0f, 0.18f, 0.17f}, {4.2f, 1.04f, 0.40f, 0.38f}, {2.8f, 1.08f, 0.52f, 0.50f},
                {0.8f, 1.1f, 0.60f, 0.56f}, {-1.5f, 1.1f, 0.60f, 0.55f}, {-3.6f, 1.05f, 0.52f, 0.50f}, {-5.2f, 1.0f, 0.44f, 0.42f}, {-5.7f, 1.0f, 0.42f, 0.40f}}, 0, 2.3f, 12);
        b.revolve(MET, 'z', 0, 1.0f, 5.8f, new float[][]{{-0.6f, 0.19f}, {0f, 0.1f}, {0.45f, 0.0f}}, 10);
        b.ellipsoid(LENS, 0, 1.58f, 2.2f, 0.36f, 0.34f, 1.05f, 12, 8);
        b.box(HB, 0, 0.55f, 2.4f, 0.36f, 0.2f, 0.8f);
        b.box(MET, 0, 0.55f, 3.22f, 0.3f, 0.17f, 0.02f);
        float[][] w = {{0.5f, 0.98f, -2.4f, 3.8f, 0.30f}, {2.5f, 1.0f, -3.1f, 2.6f, 0.18f}, {4.9f, 1.02f, -3.7f, 1.4f, 0.10f}};
        wing(b, HA, 1, w);
        wing(b, HA, -1, w);
        float[][] tp = {{0.5f, 1.0f, -6.0f, 2.6f, 0.18f}, {2.6f, 0.98f, -6.6f, 1.5f, 0.10f}};
        wing(b, HB, 1, tp);
        wing(b, HB, -1, tp);
        b.push().translate(0, 1.0f, 0).rotZ(90);
        b.foil(HB, new float[][]{{0.5f, 0f, -5.6f, 3.2f, 0.14f}, {2.2f, 0f, -6.5f, 1.6f, 0.08f}});
        b.pop();
        b.box(ACC, 0.0f, 2.8f, -6.3f, 0.09f, 0.18f, 0.2f);
        b.revolve(STEEL, 'z', 0, 1.0f, -6.0f, new float[][]{{-0.5f, 0.4f}, {0f, 0.36f}, {0.5f, 0.3f}}, 12);
        b.box(LED, 4.95f, 1.02f, -3.0f, 0.04f, 0.05f, 0.1f);
        b.box(RLED, -4.95f, 1.02f, -3.0f, 0.04f, 0.05f, 0.1f);
        b.box(WHITE, 0, 1.42f, -6.5f, 0.03f, 0.03f, 0.03f);
        // post-combustion
        m.flame.revolve(FLAME, 'z', 0, 1.0f, -7.8f, new float[][]{{-1.8f, 0.0f}, {-1.2f, 0.2f}, {0.0f, 0.3f}, {1.2f, 0.33f}}, 10);
        // train
        Mesh g = m.gear;
        g.box(MET, 0, 0.55f, 3.4f, 0.03f, 0.36f, 0.03f);
        wheel(g, 0, 0.2f, 3.4f, 0.17f, 0.14f);
        for (int s = -1; s <= 1; s += 2) {
            g.box(MET, s * 0.9f, 0.55f, -0.4f, 0.04f, 0.45f, 0.04f);
            wheel(g, s * 0.9f, 0.2f, -0.4f, 0.22f, 0.18f);
        }
        // munitions
        for (int s = -1; s <= 1; s += 2) {
            int i = s < 0 ? 0 : 1;
            m.missSt.add(new float[]{s * 5.1f, 1.0f, -2.8f});
            m.missSt.add(new float[]{s * 2.7f, 0.78f, -1.0f});
            m.bombSt.add(new float[]{s * 1.6f, 0.55f, 0.1f});
            m.bombSt.add(new float[]{s * 3.5f, 0.7f, -1.2f});
            ord(m, 1, i, s * 4.2f, 0.82f, -2.0f, 1.8f, 2);
        }
        m.pivotY = 1.0f;
        return m;
    }
}
