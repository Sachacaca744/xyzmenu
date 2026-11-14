package fr.udrone.client;

import fr.udrone.Kind;
import fr.udrone.W;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static fr.udrone.client.Mesh.*;

/** Modeles 3D procéduraux de chaque drone. Repere : +Z = avant, y=0 = sol, unite = 1 bloc. */
public class Models {
    public static class Prop {
        public Mesh blades = new Mesh(), disc = new Mesh();
        public float x, y, z, dir;
        public boolean zAxis;
    }

    public static class Ord {
        public Mesh m;
        public int weapon, idx;
    }

    public static class Model {
        public final Mesh body = new Mesh(), gimbal = new Mesh();
        public float gx, gy, gz;
        public final List<Prop> props = new ArrayList<>();
        public final List<Ord> ord = new ArrayList<>();
    }

    private static final Map<Kind, Model> CACHE = new EnumMap<>(Kind.class);
    public static Mesh bomb, missile, shell;

    public static synchronized Model get(Kind k) {
        return CACHE.computeIfAbsent(k, kk -> kk.wing ? reaper() : multi(kk));
    }

    // ------------------------------------------------------------------ munitions
    static {
        bomb = new Mesh();
        bomb.revolve(STEEL, 'z', 0, 0, 0, new float[][]{{-0.45f, 0.03f}, {-0.3f, 0.09f}, {0f, 0.125f}, {0.25f, 0.11f}, {0.42f, 0.06f}, {0.48f, 0.0f}}, 12);
        bomb.revolve(ACC, 'z', 0, 0, 0.12f, new float[][]{{0f, 0.126f}, {0.05f, 0.126f}}, 12);
        for (int i = 0; i < 4; i++) {
            bomb.push().rotZ(i * 90 + 45).box(HB, 0, 0.12f, -0.38f, 0.008f, 0.07f, 0.09f).pop();
        }
        missile = new Mesh();
        missile.revolve(MET, 'z', 0, 0, 0, new float[][]{{-0.65f, 0.05f}, {-0.55f, 0.07f}, {0.3f, 0.07f}, {0.5f, 0.05f}, {0.65f, 0.0f}}, 10);
        missile.revolve(ACC, 'z', 0, 0, 0.1f, new float[][]{{0f, 0.071f}, {0.06f, 0.071f}}, 10);
        for (int i = 0; i < 4; i++) {
            missile.push().rotZ(i * 90).box(STEEL, 0, 0.1f, -0.56f, 0.005f, 0.06f, 0.07f).pop();
            missile.push().rotZ(i * 90 + 45).box(STEEL, 0, 0.07f, 0.15f, 0.004f, 0.04f, 0.06f).pop();
        }
        shell = new Mesh();
        shell.revolve(STEEL, 'z', 0, 0, 0, new float[][]{{-0.3f, 0.06f}, {0.05f, 0.08f}, {0.18f, 0.07f}, {0.3f, 0.03f}, {0.34f, 0.0f}}, 10);
        shell.revolve(ACC, 'z', 0, 0, -0.1f, new float[][]{{0f, 0.081f}, {0.04f, 0.081f}}, 10);
    }

    private static Prop prop(float x, float y, float z, int blades, float r, float w, float dir, boolean zAxis) {
        Prop p = new Prop();
        p.x = x; p.y = y; p.z = z; p.dir = dir; p.zAxis = zAxis;
        p.blades.blades(blades, r, w);
        p.disc.disc(BLUR, r, 20);
        return p;
    }

    private static void led(Mesh m, boolean red, float x, float y, float z, float s) {
        m.box(red ? RLED : LED, x, y, z, s, s, s);
    }

    // ------------------------------------------------------------------ MQ-9
    static Model reaper() {
        Model m = new Model();
        Mesh b = m.body;
        final float Y = 1.0f;
        // fuselage
        b.loft(HA, new float[][]{
                {-2.8f, Y + 0.12f, 0.06f, 0.07f}, {-2.2f, Y + 0.12f, 0.14f, 0.16f}, {-1.4f, Y + 0.08f, 0.30f, 0.34f},
                {-0.4f, Y + 0.04f, 0.46f, 0.48f}, {0.6f, Y + 0.04f, 0.50f, 0.50f}, {1.4f, Y, 0.46f, 0.44f},
                {2.0f, Y - 0.03f, 0.38f, 0.36f}, {2.5f, Y - 0.05f, 0.26f, 0.25f}, {2.85f, Y - 0.06f, 0.12f, 0.12f}}, 0, 2.6f, 16);
        // radome (nez)
        b.loft(HB, new float[][]{
                {1.95f, Y - 0.03f, 0.385f, 0.365f}, {2.5f, Y - 0.05f, 0.265f, 0.255f}, {2.9f, Y - 0.06f, 0.0f, 0.0f}}, 0, 2.4f, 16);
        // bande d'identification
        b.loft(ACC, new float[][]{{0.2f, Y + 0.04f, 0.505f, 0.505f}, {0.35f, Y + 0.04f, 0.505f, 0.505f}}, 0, 2.6f, 16);
        // verriere de capteurs et dome SATCOM
        b.ellipsoid(CAN, 0, Y + 0.28f, 1.75f, 0.27f, 0.2f, 0.6f, 14, 6);
        b.ellipsoid(WHITE, 0, Y + 0.46f, 0.55f, 0.42f, 0.30f, 0.55f, 14, 6);
        // ailes
        for (int s = -1; s <= 1; s += 2) {
            float[][] w = {{0.35f, Y - 0.05f, -0.55f, 1.45f, 0.20f}, {1.6f, Y - 0.04f, -0.50f, 1.20f, 0.13f},
                    {3.0f, Y - 0.02f, -0.40f, 0.95f, 0.09f}, {4.5f, Y, -0.30f, 0.55f, 0.04f}};
            for (float[] r : w) r[0] *= s;
            b.foil(HA, w);
            // bord d'attaque et saumon d'aile
            b.box(HB, s * 4.5f, Y, -0.02f, 0.04f, 0.03f, 0.3f);
            led(b, s > 0, s * 4.52f, Y, -0.3f, 0.06f);
        }
        // empennage en V inverse
        for (int s = -1; s <= 1; s += 2) {
            b.push().translate(s * 0.1f, Y + 0.14f, -2.2f).rotZ(s > 0 ? -52 : 180 + 52);
            b.foil(HA, new float[][]{{0f, 0f, -0.45f, 0.95f, 0.08f}, {0.9f, 0f, -0.32f, 0.72f, 0.05f}, {1.75f, 0f, -0.22f, 0.45f, 0.03f}});
            b.pop();
        }
        led(b, false, 0, Y + 0.3f, -2.5f, 0.05f);
        // aileron ventral, nacelle et helice propulsive
        b.box(HB, 0, Y - 0.26f, -2.3f, 0.02f, 0.28f, 0.34f);
        b.tube(HB, 'z', 0, Y + 0.12f, -2.95f, 0.10f, 0.09f, 0.3f, 12);
        b.tube(MET, 'z', 0, Y + 0.12f, -3.2f, 0.1f, 0.0f, 0.15f, 12);
        // admission d'air + pod mitrailleuse + antennes
        b.box(MET, 0, Y - 0.42f, -1.2f, 0.16f, 0.07f, 0.35f);
        b.box(MET, 0, Y - 0.38f, 1.0f, 0.07f, 0.07f, 0.28f);
        b.tube(STEEL, 'z', 0, Y - 0.38f, 1.4f, 0.02f, 0.02f, 0.3f, 8);
        b.box(WHITE, 0.2f, Y + 0.62f, -0.2f, 0.01f, 0.18f, 0.01f);
        b.box(WHITE, -0.25f, Y + 0.55f, -1.4f, 0.01f, 0.14f, 0.01f);
        // panneaux decoratifs
        b.box(HB, 0, Y + 0.51f, -0.7f, 0.2f, 0.01f, 0.35f);
        b.box(MET, 0.3f, Y + 0.3f, 0.2f, 0.01f, 0.1f, 0.3f);
        b.box(MET, -0.3f, Y + 0.3f, 0.2f, 0.01f, 0.1f, 0.3f);
        // train d'atterrissage tricycle
        b.box(HB, 0, 0.40f, 1.5f, 0.03f, 0.24f, 0.03f);
        b.tube(RUB, 'x', 0, 0.17f, 1.5f, 0.17f, 0.17f, 0.11f, 12);
        b.tube(STEEL, 'x', 0, 0.17f, 1.5f, 0.07f, 0.07f, 0.13f, 8);
        b.box(HB, 0, 0.64f, -0.3f, 0.64f, 0.03f, 0.03f);
        for (int s = -1; s <= 1; s += 2) {
            b.box(HB, s * 0.62f, 0.42f, -0.3f, 0.03f, 0.25f, 0.03f);
            b.box(HB, s * 0.45f, 0.58f, -0.3f, 0.02f, 0.12f, 0.02f);
            b.tube(RUB, 'x', s * 0.62f, 0.17f, -0.3f, 0.17f, 0.17f, 0.11f, 12);
            b.tube(STEEL, 'x', s * 0.62f, 0.17f, -0.3f, 0.07f, 0.07f, 0.13f, 8);
        }
        // pylones et munitions (visibles tant qu'il en reste)
        float[] bx = {0.95f, -0.95f, 1.45f, -1.45f};
        float[] mx = {2.1f, -2.1f, 2.6f, -2.6f};
        for (int i = 0; i < 4; i++) {
            b.box(HB, bx[i], Y - 0.2f, 0.0f, 0.03f, 0.08f, 0.22f);
            b.box(HB, mx[i], Y - 0.18f, 0.0f, 0.03f, 0.08f, 0.22f);
            Ord o2 = new Ord();
            o2.m = shifted(missile, mx[i], Y - 0.42f, 0.05f);
            o2.weapon = 0;
            o2.idx = i;
            m.ord.add(o2);
            Ord ob = new Ord();
            ob.m = shifted(bomb, bx[i], Y - 0.42f, 0.0f);
            ob.weapon = 1;
            ob.idx = i;
            m.ord.add(ob);
        }
        // tourelle optronique (nacelle orientable)
        m.gx = 0; m.gy = Y - 0.52f; m.gz = 2.05f;
        Mesh g = m.gimbal;
        g.ellipsoid(HB, 0, 0, 0, 0.21f, 0.21f, 0.21f, 14, 8);
        g.box(MET, 0, 0, 0.0f, 0.215f, 0.03f, 0.215f);
        g.box(LENS, 0, 0.02f, 0.2f, 0.09f, 0.09f, 0.03f);
        g.box(LENS, 0.0f, -0.1f, 0.19f, 0.045f, 0.045f, 0.03f);
        g.box(RLED, 0.12f, 0.1f, 0.17f, 0.02f, 0.02f, 0.02f);
        // helice propulsive
        m.props.add(prop(0, Y + 0.12f, -3.28f, 3, 0.95f, 0.16f, 1f, true));
        return m;
    }

    private static Mesh shifted(Mesh src, float x, float y, float z) {
        Mesh m = new Mesh();
        for (float[] q : src.q) {
            float[] c = q.clone();
            for (int i = 0; i < 4; i++) {
                c[i * 3] += x;
                c[i * 3 + 1] += y;
                c[i * 3 + 2] += z;
            }
            m.q.add(c);
        }
        return m;
    }

    // ------------------------------------------------------------------ multirotors
    static Model multi(Kind k) {
        Model m = new Model();
        Mesh b = m.body;
        int n;
        float arm, pr, s;
        float[] ang;
        switch (k) {
            case MINI -> { n = 4; arm = 0.27f; pr = 0.14f; s = 0.62f; ang = null; }
            case QUAD -> { n = 4; arm = 0.42f; pr = 0.22f; s = 1.0f; ang = null; }
            case FAST -> { n = 4; arm = 0.36f; pr = 0.20f; s = 1.1f; ang = new float[]{30, -30, 150, -150}; }
            case SURVEIL -> { n = 6; arm = 0.46f; pr = 0.22f; s = 1.5f; ang = null; }
            default -> { n = 8; arm = 0.50f; pr = 0.22f; s = 1.9f; ang = null; }
        }
        b.push().scale(s);
        // corps
        b.loft(HA, new float[][]{{-0.22f, 0.22f, 0.05f, 0.035f}, {-0.1f, 0.22f, 0.12f, 0.075f}, {0.05f, 0.22f, 0.14f, 0.085f},
                {0.18f, 0.21f, 0.10f, 0.06f}, {0.25f, 0.20f, 0.05f, 0.04f}}, 0, 3f, 14);
        b.box(HB, 0, 0.30f, -0.03f, 0.085f, 0.024f, 0.12f);
        b.box(ACC, 0, 0.326f, -0.03f, 0.04f, 0.004f, 0.10f);
        b.box(MET, 0, 0.16f, -0.05f, 0.07f, 0.012f, 0.1f);
        led(b, false, 0, 0.255f, -0.2f, 0.012f);
        // antennes
        b.box(WHITE, 0.05f, 0.36f, -0.2f, 0.004f, 0.06f, 0.004f);
        b.box(WHITE, -0.05f, 0.36f, -0.2f, 0.004f, 0.05f, 0.004f);
        // patins
        for (int sd = -1; sd <= 1; sd += 2) {
            b.tube(MET, 'z', sd * 0.15f, 0.025f, 0.0f, 0.012f, 0.012f, 0.42f, 6);
            for (int z = -1; z <= 1; z += 2) b.box(MET, sd * 0.13f, 0.09f, z * 0.12f, 0.008f, 0.07f, 0.008f);
        }
        // bras + moteurs + helices
        for (int i = 0; i < n; i++) {
            float a = ang != null ? ang[i] : (i + 0.5f) * 360f / n;
            float dx = (float) Math.sin(Math.toRadians(a)), dz = (float) Math.cos(Math.toRadians(a));
            float ex = dx * arm, ez = dz * arm;
            b.push().translate(0, 0.24f, 0).rotY(a).box(HB, 0, 0, arm / 2, 0.015f, 0.012f, arm / 2).pop();
            b.tube(MET, 'y', ex, 0.255f, ez, 0.036f, 0.036f, 0.06f, 10);
            b.tube(ACC, 'y', ex, 0.288f, ez, 0.03f, 0.03f, 0.01f, 10);
            boolean front = dz > 0;
            led(b, front && dx > 0, ex * 0.97f, 0.235f, ez * 0.97f, 0.012f);
            Prop p = prop(ex * s, 0.31f * s, ez * s, k == Kind.MINI ? 2 : 2, pr * s, 0.045f * s, i % 2 == 0 ? 1 : -1, false);
            m.props.add(p);
        }
        // equipement specifique
        switch (k) {
            case QUAD -> {
                b.box(MET, 0, 0.115f, -0.02f, 0.03f, 0.03f, 0.1f);
                b.tube(STEEL, 'z', 0, 0.115f, 0.13f, 0.011f, 0.011f, 0.14f, 8);
                b.box(HB, 0.07f, 0.13f, -0.02f, 0.02f, 0.025f, 0.05f);
                b.box(HB, -0.07f, 0.13f, -0.02f, 0.02f, 0.025f, 0.05f);
            }
            case FAST -> {
                b.loft(HB, new float[][]{{0.2f, 0.215f, 0.02f, 0.02f}, {0.2f, 0.215f, 0.02f, 0.02f}}, 0, 2f, 6);
                Mesh wh = new Mesh();
                wh.push().scale(s);
                wh.revolve(ACC, 'z', 0, 0.2f, 0.22f, new float[][]{{-0.08f, 0.045f}, {0.06f, 0.05f}, {0.16f, 0.04f}, {0.24f, 0.0f}}, 10);
                wh.revolve(STEEL, 'z', 0, 0.2f, 0.17f, new float[][]{{0f, 0.052f}, {0.015f, 0.052f}}, 10);
                wh.pop();
                Ord o = new Ord();
                o.m = wh;
                o.weapon = 0;
                o.idx = 0;
                m.ord.add(o);
                b.box(CAMO, 0, 0.25f, -0.08f, 0.1f, 0.006f, 0.08f);
            }
            case SURVEIL -> {
                b.box(SOL, 0, 0.33f, 0.0f, 0.22f, 0.006f, 0.16f);
                b.box(MET, 0, 0.32f, 0.0f, 0.04f, 0.012f, 0.04f);
                for (int sd = -1; sd <= 1; sd += 2) for (int z = -1; z <= 1; z += 2) {
                    b.box(MET, sd * 0.2f, 0.07f, z * 0.15f, 0.01f, 0.09f, 0.01f);
                    b.box(RUB, sd * 0.2f, 0.012f, z * 0.15f, 0.025f, 0.012f, 0.025f);
                }
            }
            case HEAVY -> {
                b.box(HB, 0, 0.335f, 0.0f, 0.11f, 0.014f, 0.15f);
                for (int sd = -1; sd <= 1; sd += 2) {
                    b.box(HB, sd * 0.15f, 0.22f, 0.0f, 0.012f, 0.065f, 0.13f);
                    b.tube(STEEL, 'z', sd * 0.17f, 0.2f, 0.12f, 0.028f, 0.03f, 0.34f, 8);
                    b.tube(MET, 'z', sd * 0.17f, 0.2f, 0.3f, 0.034f, 0.034f, 0.03f, 8);
                    b.box(ACC, sd * 0.15f, 0.29f, -0.05f, 0.013f, 0.012f, 0.05f);
                }
                for (int i = 0; i < 4; i++) {
                    Ord o = new Ord();
                    o.m = bombAt(s, (i % 2 == 0 ? -0.075f : 0.075f), 0.095f, (i < 2 ? -0.07f : 0.09f));
                    o.weapon = 2;
                    o.idx = i;
                    m.ord.add(o);
                }
            }
            default -> {
            }
        }
        b.pop();
        // nacelle camera orientable
        float gs = k == Kind.SURVEIL ? 1.25f : 1.0f;
        m.gx = 0; m.gy = 0.17f * s; m.gz = 0.2f * s;
        Mesh g = m.gimbal;
        g.push().scale(s * gs);
        g.ellipsoid(HB, 0, 0, 0, 0.055f, 0.055f, 0.055f, 12, 6);
        g.box(MET, 0, 0.0f, 0, 0.058f, 0.008f, 0.058f);
        g.tube(MET, 'z', 0, 0, 0.05f, 0.03f, 0.03f, 0.04f, 10);
        g.box(LENS, 0, 0, 0.075f, 0.024f, 0.024f, 0.006f);
        if (k == Kind.SURVEIL) g.tube(MET, 'z', 0, 0, 0.1f, 0.035f, 0.032f, 0.07f, 10);
        if (k == Kind.HEAVY) {
            g.tube(STEEL, 'z', 0.035f, -0.03f, 0.1f, 0.01f, 0.01f, 0.14f, 6);
            g.tube(STEEL, 'z', -0.035f, -0.03f, 0.1f, 0.01f, 0.01f, 0.14f, 6);
        }
        g.pop();
        return m;
    }

    private static Mesh bombAt(float s, float x, float y, float z) {
        return shiftedScaled(bomb, x * s, y * s, z * s, s * 0.42f);
    }

    private static Mesh shiftedScaled(Mesh src, float x, float y, float z, float sc) {
        Mesh m = new Mesh();
        for (float[] q : src.q) {
            float[] c = q.clone();
            for (int i = 0; i < 4; i++) {
                c[i * 3] = c[i * 3] * sc + x;
                c[i * 3 + 1] = c[i * 3 + 1] * sc + y;
                c[i * 3 + 2] = c[i * 3 + 2] * sc + z;
            }
            m.q.add(c);
        }
        return m;
    }
}
