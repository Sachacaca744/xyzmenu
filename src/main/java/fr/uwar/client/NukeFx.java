package fr.uwar.client;

import fr.uwar.Uwar;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Flash blanc de l'explosion nucleaire (plus ou moins fort selon la distance). */
@Mod.EventBusSubscriber(modid = Uwar.MODID, value = Dist.CLIENT)
public class NukeFx {
    static long start = -1;
    static float strength;

    public static void flash(float x, float y, float z, float sc) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        double d = Math.hypot(mc.player.getX() - x, mc.player.getZ() - z);
        double reach = 12000.0 * sc;
        if (d > reach) return;
        strength = (float) Math.max(0.25, 1.0 - d / reach);
        start = mc.level.getGameTime();
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (start < 0 || mc.level == null) return;
        float age = mc.level.getGameTime() - start + e.getPartialTick();
        if (age > 140) {
            start = -1;
            return;
        }
        float a = age < 10 ? 1f : 1f - (age - 10) / 130f;
        int al = (int) (Math.max(0, Math.min(1, a * strength)) * 255);
        if (al <= 0) return;
        e.getGuiGraphics().fill(0, 0, e.getWindow().getGuiScaledWidth(), e.getWindow().getGuiScaledHeight(), (al << 24) | 0xFFFFE8);
    }
}
