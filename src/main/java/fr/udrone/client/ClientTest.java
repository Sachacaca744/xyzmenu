package fr.udrone.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Capture d'ecran automatique pour verifier le rendu (actif uniquement avec -Dudrone.clienttest). */
public class ClientTest {
    static int ticks, worldTicks;
    static boolean created;
    static int step = 0;

    public static void init() {
        MinecraftForge.EVENT_BUS.register(ClientTest.class);
        System.out.println("CLIENTTEST: actif");
    }

    static void cmd(String c) {
        Minecraft.getInstance().player.connection.sendCommand(c);
    }

    static void shot(String n) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, n + ".png", mc.getMainRenderTarget(), c -> {});
        System.out.println("CLIENTTEST: capture " + n);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        if (mc.level == null) {
            if (!created && mc.screen instanceof TitleScreen && ticks > 60) {
                created = true;
                System.out.println("CLIENTTEST: creation du monde");
                mc.createWorldOpenFlows().createFreshLevel("udtest",
                        new LevelSettings("udtest", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(1234L, false, false),
                        ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
            if (ticks > 6000) {
                System.out.println("CLIENTTEST: timeout titre");
                System.exit(2);
            }
            return;
        }
        if (mc.player == null) return;
        worldTicks++;
        int t = worldTicks;
        try {
            switch (t) {
                case 150 -> {
                    mc.options.hideGui = true;
                    cmd("time set noon");
                    cmd("gamemode creative");
                    cmd("tp @s 0 -60 0 180 0");
                    cmd("udrone_selftest models");
                }
                case 200 -> cmd("tp @s -2 -58 -2 180 12");
                case 260 -> shot("01_overview");
                case 261 -> cmd("tp @s -14 -58 4 180 14");
                case 300 -> shot("02_reaper_front");
                case 301 -> cmd("tp @s -22 -57 12 -90 8");
                case 340 -> shot("03_reaper_side");
                case 341 -> cmd("tp @s -8 -58.5 3.5 180 10");
                case 380 -> shot("04_heavy");
                case 381 -> cmd("tp @s -3 -59 4.5 180 10");
                case 420 -> shot("05_surveil");
                case 421 -> cmd("tp @s 1 -59 5 180 12");
                case 460 -> shot("06_quad");
                case 461 -> cmd("tp @s 5 -59 5.5 180 12");
                case 500 -> shot("07_fast");
                case 501 -> cmd("tp @s 8 -59 5.5 180 12");
                case 540 -> shot("08_mini");
                case 541 -> {
                    cmd("tp @s -2 -58 -4 180 -5");
                    cmd("udrone_selftest fly");
                }
                case 620 -> shot("09_flying");
                case 640 -> cmd("tp @s -2 -58 -12 180 -10");
                case 700 -> shot("10_flying_far");
                case 701 -> {
                    mc.options.hideGui = false;
                    cmd("udrone_selftest pilot quad");
                }
                case 760 -> shot("11_fpv_quad");
                case 800 -> cmd("udrone_selftest station");
                case 880 -> shot("12_station_screen");
                case 900 -> {
                    mc.setScreen(null);
                    cmd("udrone_selftest pilot reaper");
                }
                case 980 -> shot("13_fpv_reaper");
                case 1000 -> {
                    System.out.println("CLIENTTEST: termine");
                    mc.stop();
                }
                default -> {
                }
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
            System.out.println("CLIENTTEST: exception " + ex);
        }
        if (t > 1600) System.exit(3);
    }
}
