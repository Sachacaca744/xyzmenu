package fr.uboards.client;

import com.mojang.blaze3d.platform.NativeImage;
import fr.uboards.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Cache des images de tableaux, recues du serveur. */
public class ClientImages {
    private static final Map<String, ResourceLocation> READY = new HashMap<>();
    private static final Set<String> REQUESTED = new HashSet<>();
    private static final Map<String, byte[][]> PARTS = new HashMap<>();

    /** Renvoie la texture si elle est prete, sinon la demande au serveur et renvoie null. */
    public static ResourceLocation get(String hash) {
        ResourceLocation r = READY.get(hash);
        if (r != null) {
            return r;
        }
        if (REQUESTED.add(hash)) {
            Net.CHANNEL.sendToServer(new Net.ImgReqMsg(hash));
        }
        return null;
    }

    public static void onChunk(String hash, int index, int total, byte[] data) {
        if (total <= 0 || total > 400 || index < 0 || index >= total || READY.containsKey(hash)) {
            return;
        }
        byte[][] parts = PARTS.computeIfAbsent(hash, k -> new byte[total][]);
        if (parts.length != total) {
            PARTS.remove(hash);
            return;
        }
        parts[index] = data;
        for (byte[] p : parts) {
            if (p == null) {
                return;
            }
        }
        PARTS.remove(hash);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            for (byte[] p : parts) {
                bos.write(p);
            }
            NativeImage img = NativeImage.read(new ByteArrayInputStream(bos.toByteArray()));
            DynamicTexture tex = new DynamicTexture(img);
            READY.put(hash, Minecraft.getInstance().getTextureManager().register("uboards_img", tex));
        } catch (Exception e) {
            // image illisible : on la laisse en "chargement"
        }
    }
}
