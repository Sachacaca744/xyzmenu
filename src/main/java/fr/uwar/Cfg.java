package fr.uwar;

import net.minecraftforge.common.ForgeConfigSpec;

public class Cfg {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue BLOCK_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_MULT;
    public static final ForgeConfigSpec.DoubleValue FUEL_USE;
    public static final ForgeConfigSpec.BooleanValue UNLIMITED_AMMO;
    public static final ForgeConfigSpec.BooleanValue LOCK_PLAYERS;
    public static final ForgeConfigSpec.BooleanValue HEAVY_BLOCKS;
    public static final ForgeConfigSpec.DoubleValue NUKE_SCALE;
    public static final ForgeConfigSpec.BooleanValue NUKE_ENABLED;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        BLOCK_DAMAGE = b.comment("Les explosions (roquettes, missiles, bombes) detruisent les blocs (false = degats aux entites seulement)")
                .define("blockDamage", false);
        DAMAGE_MULT = b.comment("Multiplicateur des degats des armes")
                .defineInRange("damageMultiplier", 1.0, 0.1, 10.0);
        FUEL_USE = b.comment("Multiplicateur de consommation de carburant")
                .defineInRange("fuelUse", 1.0, 0.0, 10.0);
        UNLIMITED_AMMO = b.comment("Munitions illimitees (mode arcade)")
                .define("unlimitedAmmo", false);
        LOCK_PLAYERS = b.comment("Les missiles peuvent verrouiller les joueurs")
                .define("lockPlayers", false);
        HEAVY_BLOCKS = b.comment("Les armes lourdes (croisiere, balistique, anti-bunker, soute a bombes) detruisent les blocs")
                .define("heavyBlockDamage", true);
        NUKE_SCALE = b.comment("Echelle des zones de la bombe nucleaire (1.0 = 500 m / 2 km / 5 km / 10 km ; 0.1 = 50 m / 200 m / 500 m / 1 km)")
                .defineInRange("nukeScale", 1.0, 0.01, 1.0);
        NUKE_ENABLED = b.comment("false = la bombe nucleaire devient une grosse explosion normale")
                .define("nukeEnabled", true);
        SPEC = b.build();
    }
}
