package fr.uwar.client;

import fr.uwar.Net;
import net.minecraft.client.CameraType;
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

/** Captures d'ecran automatiques + vol reel au clavier simule (actif uniquement avec -Duwar.clienttest). */
public class ClientTest {
    static int ticks, worldTicks;
    static boolean created;

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

    static void log(String s) {
        Minecraft mc = Minecraft.getInstance();
        var v = mc.player.getVehicle();
        System.out.println("CLIENTTEST: " + s + " | vehicule=" + (v == null ? "aucun" : v.getClass().getSimpleName() + " pos=" + v.blockPosition()
                + " v=" + String.format("%.2f", v.getDeltaMovement().length())) + " joueur=" + mc.player.blockPosition());
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
                mc.createWorldOpenFlows().createFreshLevel("uwtest",
                        new LevelSettings("uwtest", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
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
                    cmd("gamemode spectator");
                    cmd("tp @s 0 -60 0 0 0");
                    cmd("uwar_selftest models");
                }
                case 200 -> cmd("tp @s 0 -56 -12 0 8");
                case 260 -> shot("01_overview");
                case 261 -> cmd("tp @s -12 -58 2 0 6");
                case 300 -> shot("02_heli_front");
                case 301 -> cmd("tp @s 6 -57 15 90 8");
                case 340 -> shot("03_heli_side");
                case 341 -> cmd("tp @s -22 -56 27 150 10");
                case 380 -> shot("04_heli_34_rear");
                case 381 -> cmd("tp @s -12 -45 15 0 90");
                case 420 -> shot("05_heli_top");
                case 421 -> cmd("tp @s 14 -58 -4 0 6");
                case 460 -> shot("06_jet_front");
                case 461 -> cmd("tp @s 30 -57 15 90 8");
                case 500 -> shot("07_jet_side");
                case 501 -> cmd("tp @s 5 -55 30 -150 10");
                case 540 -> shot("08_jet_34_rear");
                case 541 -> cmd("tp @s 14 -40 15 0 90");
                case 580 -> shot("09_jet_top");
                // ---- vol reel de l'helicoptere (client = autorite de vol)
                case 600 -> {
                    cmd("gamemode creative");
                    cmd("tp @s -12 -60 10 0 0");
                }
                case 620 -> cmd("uwar_selftest ride heli");
                case 645 -> {
                    mc.options.hideGui = false;
                    var v = mc.player.getVehicle();
                    if (v instanceof fr.uwar.Aircraft ac) mc.setScreen(new LoadoutScreen(ac));
                }
                case 648 -> {
                    if (mc.player.getVehicle() instanceof fr.uwar.Aircraft ac)
                        Net.CH.sendToServer(new Net.LoadoutMsg(ac.getId(), fr.uwar.W.CANNON.bit() | fr.uwar.W.IR.bit() | fr.uwar.W.BUSTER.bit() | fr.uwar.W.FLAK.bit()));
                }
                case 652 -> {
                    shot("20_ecran_armement_helico");
                    mc.setScreen(null);
                }
                case 653 -> {
                    mc.options.hideGui = false;
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    log("embarque heli");
                    shot("10_heli_cockpit_sol");
                }
                case 660 -> mc.options.keyJump.setDown(true);
                case 740 -> {
                    log("heli en montee");
                    shot("11_heli_cockpit_vol");
                    mc.options.keyJump.setDown(false);
                    mc.options.keyUp.setDown(true);
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                }
                case 800 -> {
                    log("heli avance");
                    shot("12_heli_3eme_personne");
                    mc.options.keyUp.setDown(false);
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    mc.options.keyAttack.setDown(true);
                }
                case 815 -> shot("13_heli_canon_tir");
                case 830 -> {
                    mc.options.keyAttack.setDown(false);
                    Net.CH.sendToServer(new Net.CmdMsg(Net.C_WEAPON));
                }
                case 840 -> mc.options.keyAttack.setDown(true);
                case 850 -> {
                    shot("14_heli_roquettes");
                    mc.options.keyAttack.setDown(false);
                }
                case 880 -> {
                    log("heli apres tirs");
                    cmd("uwar_selftest unride");
                }
                // ---- vol reel de l'avion de chasse
                case 900 -> cmd("tp @s 14 -60 10 0 0");
                case 920 -> cmd("uwar_selftest ride jet");
                case 925 -> {
                    if (mc.player.getVehicle() instanceof fr.uwar.Aircraft ac) mc.setScreen(new LoadoutScreen(ac));
                }
                case 928 -> {
                    if (mc.player.getVehicle() instanceof fr.uwar.Aircraft ac)
                        Net.CH.sendToServer(new Net.LoadoutMsg(ac.getId(), fr.uwar.W.CANNON.bit() | fr.uwar.W.CRUISE.bit() | fr.uwar.W.BALLISTIC.bit() | fr.uwar.W.IR.bit() | fr.uwar.W.BAY.bit()));
                }
                case 931 -> cmd("uwar_selftest fill");
                case 934 -> {
                    shot("21_ecran_armement_avion");
                    mc.setScreen(null);
                    Net.CH.sendToServer(new Net.CmdMsg(Net.C_WEAPON));
                    Net.CH.sendToServer(new Net.CmdMsg(Net.C_WEAPON));
                }
                case 940 -> {
                    mc.player.setXRot(-14f);
                    mc.player.setYRot(0f);
                    mc.options.keyUp.setDown(true);
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    log("jet embarque");
                }
                case 1000 -> {
                    shot("15_jet_roulage");
                    log("jet roulage");
                }
                case 1100 -> {
                    shot("16_jet_decollage");
                    log("jet decollage");
                }
                case 1200 -> {
                    mc.options.keySprint.setDown(false);
                    mc.options.keyJump.setDown(true);
                    shot("17_jet_postcombustion");
                    log("jet postcombustion");
                }
                case 1260 -> {
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    mc.options.keyJump.setDown(false);
                }
                case 1290 -> {
                    shot("18_jet_cockpit_hud");
                    log("jet hud");
                    mc.options.keyAttack.setDown(true);
                }
                case 1300 -> shot("19_jet_tir_canon");
                case 1310 -> mc.options.keyAttack.setDown(false);
                case 1330 -> {
                    mc.options.keyUp.setDown(false);
                    mc.options.keyDown.setDown(true);
                }
                case 1400 -> {
                    log("jet fin");
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
        if (t > 2400) System.exit(3);
    }
}
