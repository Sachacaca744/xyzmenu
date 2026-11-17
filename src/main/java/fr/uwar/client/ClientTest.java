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
                case 581 -> cmd("tp @s 34 -58 -4 0 6");
                case 586 -> shot("09b_mini_front");
                case 587 -> cmd("tp @s 48 -57 15 90 8");
                case 592 -> shot("09c_mini_side");
                case 593 -> cmd("tp @s 28 -55 28 -150 12");
                case 598 -> shot("09d_mini_34_rear");
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
                    mc.options.keyUp.setDown(false);
                    mc.options.keyDown.setDown(false);
                    cmd("uwar_selftest unride");
                }
                case 1420 -> cmd("tp @s 34 -60 10 0 0");
                case 1430 -> cmd("uwar_selftest ride mini");
                case 1450 -> {
                    mc.player.setXRot(-14f);
                    mc.player.setYRot(0f);
                    mc.options.keyUp.setDown(true);
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                }
                case 1560 -> {
                    log("mini en vol");
                    shot("22_mini_vol");
                    mc.player.setYRot(90f);
                }
                case 1590 -> {
                    shot("23_mini_virage");
                    log("mini virage");
                }
                case 1620 -> {
                    log("mini fin");
                    mc.options.keyUp.setDown(false);
                    cmd("uwar_selftest unride");
                    cmd("gamemode spectator");
                }
                case 1625 -> cmd("tp @s 90 -52 -2 0 6");
                case 1626 -> {
                    cmd("gamemode creative");
                    mc.options.hideGui = true;
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                }
                case 1631 -> shot("40_b17_face");
                case 1632 -> cmd("tp @s 135 -50 40 90 6");
                case 1637 -> shot("41_b17_profil");
                case 1638 -> cmd("tp @s 60 -45 80 -150 10");
                case 1643 -> shot("42_b17_34_arriere");
                case 1644 -> cmd("tp @s 90 -22 40 0 90");
                case 1649 -> shot("43_b17_dessus");
                case 1650 -> {
                    mc.options.hideGui = false;
                    cmd("tp @s 90 -60 38 0 0");
                }
                case 1655 -> cmd("uwar_selftest ride b17");
                case 1670 -> {
                    cmd("uwar_selftest fill");
                    mc.player.setXRot(-14f);
                    mc.player.setYRot(180f);
                    mc.options.keyUp.setDown(true);
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                }
                case 1950 -> {
                    log("b17 en vol");
                    shot("44_b17_vol");
                    cmd("uwar_selftest sel bomb");
                    mc.player.setXRot(24f);
                }
                case 1995 -> {
                    shot("45_b17_viseur_bombes");
                    mc.options.keyAttack.setDown(true);
                }
                case 2025 -> {
                    mc.options.keyAttack.setDown(false);
                    shot("46_b17_bombes_larguees");
                }
                case 2040 -> {
                    mc.options.keyUp.setDown(false);
                    cmd("uwar_selftest unride");
                    cmd("gamemode spectator");
                }
                case 2050 -> {
                    cmd("tp @s 0 -52 -115 0 -14");
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    mc.options.hideGui = true;
                    mc.player.setYRot(0f);
                    mc.player.setXRot(-14f);
                }
                case 2120 -> {
                    mc.options.hideGui = false;
                    cmd("uwar_selftest nuke 0.05");
                }
                case 2122, 2165, 2250, 2390 -> cmd("effect clear @s");
                case 2126 -> shot("30_nuke_flash");
                case 2170 -> shot("31_nuke_boule_de_feu");
                case 2260 -> shot("32_nuke_colonne");
                case 2400 -> shot("33_nuke_champignon");
                case 2850 -> {
                    mc.player.setXRot(10f);
                    cmd("tp @s 0 -20 -60 0 25");
                }
                case 2900 -> shot("34_nuke_apres_cratere");
                case 2910 -> {
                    cmd("tp @s 200 -48 -38 0 8");
                    mc.player.setXRot(8f);
                    mc.player.setYRot(0f);
                }
                case 2915 -> cmd("uwar_selftest payload incen 200 -60 0");
                case 2945 -> shot("50_bombe_incendiaire");
                case 2950 -> cmd("uwar_selftest payload acid 200 -60 0");
                case 2995 -> shot("51_bombe_acide");
                case 3000 -> cmd("uwar_selftest payload thermo 200 -60 0");
                case 3016 -> shot("52_bombe_thermobarique");
                case 3050 -> cmd("uwar_selftest payload gas 200 -60 0");
                case 3100 -> shot("53_bombe_gaz");
                case 3110 -> cmd("uwar_selftest payload light 200 -60 0");
                case 3130 -> shot("54_bombe_foudre");
                case 3190 -> cmd("uwar_selftest payload cluster 200 -60 0");
                case 3215 -> shot("55_bombe_sous_munitions");
                case 3260 -> cmd("uwar_selftest payload seismic 200 -60 0");
                case 3290 -> shot("56_bombe_sismique");
                case 3300 -> {
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
        if (t > 3200) System.exit(3);
    }
}
