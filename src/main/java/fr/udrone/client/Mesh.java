package fr.udrone.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Constructeur de maillage procedural : quads a faces plates, UV dans une grille 4x4 de materiaux. */
public class Mesh {
    public static final int HA = 0, HB = 1, MET = 2, LENS = 3, RUB = 4, ACC = 5, PROP = 6, LED = 7, SOL = 8, STEEL = 9,
            CAN = 10, WHITE = 11, BLUR = 12, CAMO = 13, RLED = 14;

    /** quad : 12 positions, 8 uv, 3 normale, 1 materiau */
    public final List<float[]> q = new ArrayList<>();
    private final ArrayDeque<Matrix4f> st = new ArrayDeque<>();
    private Matrix4f cur = new Matrix4f();

    public Mesh push() { st.push(new Matrix4f(cur)); return this; }
    public Mesh pop() { cur = st.pop(); return this; }
    public Mesh translate(float x, float y, float z) { cur.translate(x, y, z); return this; }
    public Mesh rotX(float d) { cur.rotateX((float) Math.toRadians(d)); return this; }
    public Mesh rotY(float d) { cur.rotateY((float) Math.toRadians(d)); return this; }
    public Mesh rotZ(float d) { cur.rotateZ((float) Math.toRadians(d)); return this; }
    public Mesh scale(float s) { cur.scale(s); return this; }

    private Vector3f tv(float x, float y, float z) {
        return cur.transformPosition(x, y, z, new Vector3f());
    }

    private void quadW(int mat, Vector3f a, Vector3f b, Vector3f c, Vector3f d, Vector3f hint) {
        Vector3f d1 = new Vector3f(c).sub(a), d2 = new Vector3f(d).sub(b);
        Vector3f n = d1.cross(d2, new Vector3f());
        if (n.lengthSquared() < 1e-12f) n.set(0, 1, 0);
        else n.normalize();
        Vector3f cen = new Vector3f(a).add(b).add(c).add(d).mul(0.25f);
        if (hint != null && n.dot(cen.sub(hint, new Vector3f())) < 0) n.negate();
        int col = mat & 3, row = mat >> 2;
        float u0 = (col + 0.04f) / 4f, u1 = (col + 0.96f) / 4f, v0 = (row + 0.04f) / 4f, v1 = (row + 0.96f) / 4f;
        q.add(new float[]{a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, d.x, d.y, d.z,
                u0, v0, u1, v0, u1, v1, u0, v1, n.x, n.y, n.z, mat});
    }

    private static Vector3f avg(Vector3f[] p) {
        Vector3f c = new Vector3f();
        for (Vector3f v : p) c.add(v);
        return c.mul(1f / p.length);
    }

    /** Anneaux de points locaux (rings[i][j] = {x,y,z}), relies en bande fermee, avec bouchons. */
    public Mesh rings(int mat, float[][][] r, boolean capA, boolean capB) {
        int n = r[0].length;
        Vector3f[][] w = new Vector3f[r.length][n];
        Vector3f[] cen = new Vector3f[r.length];
        for (int i = 0; i < r.length; i++) {
            for (int j = 0; j < n; j++) w[i][j] = tv(r[i][j][0], r[i][j][1], r[i][j][2]);
            cen[i] = avg(w[i]);
        }
        for (int i = 0; i < r.length - 1; i++) {
            Vector3f hint = new Vector3f(cen[i]).add(cen[i + 1]).mul(0.5f);
            for (int j = 0; j < n; j++) {
                int k = (j + 1) % n;
                quadW(mat, w[i][j], w[i][k], w[i + 1][k], w[i + 1][j], hint);
            }
        }
        if (capA) for (int j = 0; j < n; j++)
            quadW(mat, w[0][j], w[0][(j + 1) % n], cen[0], cen[0], cen[r.length - 1]);
        if (capB) for (int j = 0; j < n; j++)
            quadW(mat, w[r.length - 1][j], w[r.length - 1][(j + 1) % n], cen[r.length - 1], cen[r.length - 1], cen[0]);
        return this;
    }

    private static float sp(double c, float n) {
        return (float) (Math.signum(c) * Math.pow(Math.abs(c), 2.0 / n));
    }

    /** Anneau superellipse dans le plan XY a la profondeur z. */
    public static float[][] ell(float z, float cx, float cy, float rx, float ry, float n, int seg) {
        float[][] p = new float[seg][];
        for (int j = 0; j < seg; j++) {
            double a = 2 * Math.PI * j / seg;
            p[j] = new float[]{cx + rx * sp(Math.cos(a), n), cy + ry * sp(Math.sin(a), n), z};
        }
        return p;
    }

    /** Fuselage : lignes {z, cy, rx, ry}. */
    public Mesh loft(int mat, float[][] s, float cx, float n, int seg) {
        float[][][] r = new float[s.length][][];
        for (int i = 0; i < s.length; i++) r[i] = ell(s[i][0], cx, s[i][1], s[i][2], s[i][3], n, seg);
        return rings(mat, r, true, true);
    }

    private static final float[][] FOIL = {{1f, 0f}, {0.7f, 0.55f}, {0.3f, 0.5f}, {0f, 0.05f}, {0.3f, -0.3f}, {0.7f, -0.45f}};

    /** Profil d'aile : lignes {x, y, zBordFuite, corde, epaisseur}. */
    public Mesh foil(int mat, float[][] s) {
        float[][][] r = new float[s.length][FOIL.length][];
        for (int i = 0; i < s.length; i++)
            for (int j = 0; j < FOIL.length; j++)
                r[i][j] = new float[]{s[i][0], s[i][1] + s[i][4] * FOIL[j][1], s[i][2] + s[i][3] * FOIL[j][0]};
        return rings(mat, r, false, true);
    }

    public Mesh ellipsoid(int mat, float cx, float cy, float cz, float rx, float ry, float rz, int seg, int stacks) {
        float[][][] r = new float[stacks + 1][seg][];
        for (int i = 0; i <= stacks; i++) {
            double phi = Math.toRadians(-82 + 164.0 * i / stacks);
            for (int j = 0; j < seg; j++) {
                double th = 2 * Math.PI * j / seg;
                r[i][j] = new float[]{cx + (float) (rx * Math.cos(phi) * Math.cos(th)), cy + (float) (ry * Math.sin(phi)),
                        cz + (float) (rz * Math.cos(phi) * Math.sin(th))};
            }
        }
        return rings(mat, r, true, true);
    }

    /** Tube/cone le long de l'axe ('x','y','z') centre en (cx,cy,cz), longueur totale len, rayons r0 -> r1. */
    public Mesh tube(int mat, char axis, float cx, float cy, float cz, float r0, float r1, float len, int seg) {
        push();
        translate(cx, cy, cz);
        if (axis == 'x') rotY(90);
        else if (axis == 'y') rotX(-90);
        float[][][] r = {ell(-len / 2, 0, 0, r0, r0, 2f, seg), ell(len / 2, 0, 0, r1, r1, 2f, seg)};
        rings(mat, r, true, true);
        pop();
        return this;
    }

    /** Tube profile : lignes {z, rayon} le long de l'axe z local. */
    public Mesh revolve(int mat, char axis, float cx, float cy, float cz, float[][] zr, int seg) {
        push();
        translate(cx, cy, cz);
        if (axis == 'x') rotY(90);
        else if (axis == 'y') rotX(-90);
        float[][][] r = new float[zr.length][][];
        for (int i = 0; i < zr.length; i++) r[i] = ell(zr[i][0], 0, 0, zr[i][1], zr[i][1], 2f, seg);
        rings(mat, r, true, true);
        pop();
        return this;
    }

    public Mesh box(int mat, float cx, float cy, float cz, float hx, float hy, float hz) {
        float[][] c = new float[8][];
        int k = 0;
        for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) for (int l = 0; l < 2; l++)
            c[k++] = new float[]{cx + (i == 0 ? -hx : hx), cy + (j == 0 ? -hy : hy), cz + (l == 0 ? -hz : hz)};
        Vector3f[] w = new Vector3f[8];
        for (int i = 0; i < 8; i++) w[i] = tv(c[i][0], c[i][1], c[i][2]);
        Vector3f h = tv(cx, cy, cz);
        // index = i*4 + j*2 + l
        quadW(mat, w[0], w[1], w[3], w[2], h); // -x
        quadW(mat, w[4], w[5], w[7], w[6], h); // +x
        quadW(mat, w[0], w[1], w[5], w[4], h); // -y
        quadW(mat, w[2], w[3], w[7], w[6], h); // +y
        quadW(mat, w[0], w[2], w[6], w[4], h); // -z
        quadW(mat, w[1], w[3], w[7], w[5], h); // +z
        return this;
    }

    /** Disque plat dans le plan XZ (normale +Y). */
    public Mesh disc(int mat, float r, int seg) {
        Vector3f c = tv(0, 0, 0), h = tv(0, -1, 0);
        for (int j = 0; j < seg; j++) {
            double a0 = 2 * Math.PI * j / seg, a1 = 2 * Math.PI * (j + 1) / seg;
            quadW(mat, tv((float) (r * Math.cos(a0)), 0, (float) (r * Math.sin(a0))),
                    tv((float) (r * Math.cos(a1)), 0, (float) (r * Math.sin(a1))), c, c, h);
        }
        return this;
    }

    /** Pales d'helice dans le plan XZ. */
    public Mesh blades(int n, float r, float w) {
        for (int i = 0; i < n; i++) {
            push();
            rotY(i * 360f / n);
            rotX(10);
            box(PROP, r / 2 + 0.01f, 0, 0, r / 2, 0.006f, w / 2);
            pop();
        }
        box(MET, 0, 0, 0, 0.02f, 0.014f, 0.02f);
        return this;
    }
}
