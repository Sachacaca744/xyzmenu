package fr.utaxi.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lecteur de modeles "geo.json" (format Bedrock / Blockbench, comme ceux de Cobblemon).
 * Passage fichier -> espace Blockbench : z -> -z, rotations (-x, -y, z). Modele immobile (pas d'animation).
 */
public class GeoModel {
    static class Cube {
        float[] from = new float[3], to = new float[3];
        float inflate;
        boolean mirror;
        float[] uvBox; // [u, v] ou null
        Map<String, float[]> faceUv = new HashMap<>(); // face -> [u, v, w, h]
        float[] pivot; // rotation propre du cube (optionnel)
        float[] rot;
    }

    static class Bone {
        String name;
        Bone parent;
        float[] pivot = new float[3];
        float[] rot = new float[3];
        boolean mirror;
        final List<Cube> cubes = new ArrayList<>();
        final List<Bone> children = new ArrayList<>();
    }

    private final List<Bone> roots = new ArrayList<>();
    private int texW = 64, texH = 64;
    public float minX = 1e9F, maxX = -1e9F, minY = 1e9F, maxY = -1e9F, minZ = 1e9F, maxZ = -1e9F;

    public static GeoModel parse(String json) {
        GeoModel m = new GeoModel();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject g = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject desc = g.getAsJsonObject("description");
        if (desc.has("texture_width")) m.texW = desc.get("texture_width").getAsInt();
        if (desc.has("texture_height")) m.texH = desc.get("texture_height").getAsInt();
        Map<String, Bone> map = new HashMap<>();
        List<Bone> all = new ArrayList<>();
        List<String> parents = new ArrayList<>();
        for (JsonElement be : g.getAsJsonArray("bones")) {
            JsonObject bo = be.getAsJsonObject();
            Bone b = new Bone();
            b.name = bo.get("name").getAsString();
            if (bo.has("pivot")) b.pivot = vec(bo.getAsJsonArray("pivot"));
            if (bo.has("rotation")) b.rot = vec(bo.getAsJsonArray("rotation"));
            if (bo.has("mirror")) b.mirror = bo.get("mirror").getAsBoolean();
            // fichier -> Blockbench
            b.pivot[2] = -b.pivot[2];
            b.rot = new float[]{-b.rot[0], -b.rot[1], b.rot[2]};
            if (bo.has("cubes")) {
                for (JsonElement ce : bo.getAsJsonArray("cubes")) {
                    Cube c = m.parseCube(ce.getAsJsonObject(), b.mirror);
                    b.cubes.add(c);
                }
            }
            map.put(b.name, b);
            all.add(b);
            parents.add(bo.has("parent") ? bo.get("parent").getAsString() : null);
        }
        for (int i = 0; i < all.size(); i++) {
            Bone b = all.get(i);
            String p = parents.get(i);
            if (p != null && map.containsKey(p) && map.get(p) != b) {
                b.parent = map.get(p);
                b.parent.children.add(b);
            } else {
                m.roots.add(b);
            }
        }
        if (m.minY > m.maxY) {
            throw new IllegalStateException("modele vide");
        }
        return m;
    }

    private static float[] vec(JsonArray a) {
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    private Cube parseCube(JsonObject co, boolean boneMirror) {
        Cube c = new Cube();
        float[] o = vec(co.getAsJsonArray("origin"));
        float[] s = vec(co.getAsJsonArray("size"));
        c.inflate = co.has("inflate") ? co.get("inflate").getAsFloat() : 0F;
        c.mirror = co.has("mirror") ? co.get("mirror").getAsBoolean() : boneMirror;
        // z -> -z
        float zMin = -(o[2] + s[2]);
        c.from = new float[]{o[0], o[1], zMin};
        c.to = new float[]{o[0] + s[0], o[1] + s[1], zMin + s[2]};
        if (co.has("uv")) {
            JsonElement u = co.get("uv");
            if (u.isJsonArray()) {
                c.uvBox = new float[]{u.getAsJsonArray().get(0).getAsFloat(), u.getAsJsonArray().get(1).getAsFloat()};
            } else {
                JsonObject fo = u.getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : fo.entrySet()) {
                    JsonObject f = e.getValue().getAsJsonObject();
                    float[] uv = new float[]{f.getAsJsonArray("uv").get(0).getAsFloat(), f.getAsJsonArray("uv").get(1).getAsFloat(), 0, 0};
                    if (f.has("uv_size")) {
                        uv[2] = f.getAsJsonArray("uv_size").get(0).getAsFloat();
                        uv[3] = f.getAsJsonArray("uv_size").get(1).getAsFloat();
                    }
                    c.faceUv.put(e.getKey(), uv);
                }
            }
        }
        if (co.has("rotation") && co.has("pivot")) {
            float[] r = vec(co.getAsJsonArray("rotation"));
            float[] p = vec(co.getAsJsonArray("pivot"));
            c.rot = new float[]{-r[0], -r[1], r[2]};
            c.pivot = new float[]{p[0], p[1], -p[2]};
        }
        minX = Math.min(minX, c.from[0]); maxX = Math.max(maxX, c.to[0]);
        minY = Math.min(minY, c.from[1]); maxY = Math.max(maxY, c.to[1]);
        minZ = Math.min(minZ, c.from[2]); maxZ = Math.max(maxZ, c.to[2]);
        return c;
    }

    /** Dessine tout le modele (unites = pixels ; l'appelant applique l'echelle). */
    public void render(PoseStack ps, VertexConsumer vc, int light) {
        for (Bone b : roots) {
            renderBone(b, ps, vc, light);
        }
    }

    private void renderBone(Bone b, PoseStack ps, VertexConsumer vc, int light) {
        ps.pushPose();
        transform(ps, b.pivot, b.rot);
        for (Cube c : b.cubes) {
            renderCube(c, ps, vc, light);
        }
        for (Bone ch : b.children) {
            renderBone(ch, ps, vc, light);
        }
        ps.popPose();
    }

    private static void transform(PoseStack ps, float[] pivot, float[] rot) {
        ps.translate(pivot[0], pivot[1], pivot[2]);
        if (rot[0] != 0 || rot[1] != 0 || rot[2] != 0) {
            ps.mulPose(new Quaternionf().rotationZYX((float) Math.toRadians(rot[2]),
                    (float) Math.toRadians(rot[1]), (float) Math.toRadians(rot[0])));
        }
        ps.translate(-pivot[0], -pivot[1], -pivot[2]);
    }

    private void renderCube(Cube c, PoseStack ps, VertexConsumer vc, int light) {
        boolean rotated = c.rot != null;
        if (rotated) {
            ps.pushPose();
            transform(ps, c.pivot, c.rot);
        }
        float x1 = c.from[0] - c.inflate, y1 = c.from[1] - c.inflate, z1 = c.from[2] - c.inflate;
        float x2 = c.to[0] + c.inflate, y2 = c.to[1] + c.inflate, z2 = c.to[2] + c.inflate;
        float w = c.to[0] - c.from[0], h = c.to[1] - c.from[1], d = c.to[2] - c.from[2];
        float[][] uvs = new float[6][]; // DOWN, UP, NORTH, SOUTH, WEST, EAST : {u1,v1,u2,v2}
        String[] names = {"down", "up", "north", "south", "west", "east"};
        if (c.uvBox != null) {
            float u = c.uvBox[0], v = c.uvBox[1];
            uvs[0] = new float[]{u + d + w + w, v, u + d + w, v + d};
            uvs[1] = new float[]{u + d + w, v + d, u + d, v};
            uvs[2] = new float[]{u + d, v + d, u + d + w, v + d + h};
            uvs[3] = new float[]{u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h};
            uvs[4] = new float[]{u + d + w, v + d, u + 2 * d + w, v + d + h};
            uvs[5] = new float[]{u, v + d, u + d, v + d + h};
            if (c.mirror) {
                for (int i = 0; i < 6; i++) {
                    float t = uvs[i][0]; uvs[i][0] = uvs[i][2]; uvs[i][2] = t;
                }
                float[] t = uvs[4]; uvs[4] = uvs[5]; uvs[5] = t;
            }
        } else {
            for (int i = 0; i < 6; i++) {
                float[] f = c.faceUv.get(names[i]);
                if (f == null) { uvs[i] = null; continue; }
                uvs[i] = new float[]{f[0], f[1], f[0] + f[2], f[1] + f[3]};
            }
        }
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float[][][] corners = {
                {{x1, y1, z2}, {x1, y1, z1}, {x2, y1, z1}, {x2, y1, z2}},
                {{x1, y2, z1}, {x1, y2, z2}, {x2, y2, z2}, {x2, y2, z1}},
                {{x2, y2, z1}, {x2, y1, z1}, {x1, y1, z1}, {x1, y2, z1}},
                {{x1, y2, z2}, {x1, y1, z2}, {x2, y1, z2}, {x2, y2, z2}},
                {{x1, y2, z1}, {x1, y1, z1}, {x1, y1, z2}, {x1, y2, z2}},
                {{x2, y2, z2}, {x2, y1, z2}, {x2, y1, z1}, {x2, y2, z1}}};
        float[][] normals = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
        for (int f = 0; f < 6; f++) {
            float[] uv = uvs[f];
            if (uv == null) continue;
            float[][] vt = {{uv[0], uv[1]}, {uv[0], uv[3]}, {uv[2], uv[3]}, {uv[2], uv[1]}};
            for (int k = 0; k < 4; k++) {
                float[] p = corners[f][k];
                vc.vertex(m, p[0], p[1], p[2]).color(255, 255, 255, 255)
                        .uv(vt[k][0] / texW, vt[k][1] / texH)
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                        .normal(n, normals[f][0], normals[f][1], normals[f][2]).endVertex();
            }
        }
        if (rotated) {
            ps.popPose();
        }
    }
}
