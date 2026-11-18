package fr.uking;

import net.minecraftforge.common.ForgeConfigSpec;

public class Cfg {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue PROTECT;
    public static final ForgeConfigSpec.IntValue NPC_FIEFS;
    public static final ForgeConfigSpec.BooleanValue NPC_WAR;
    public static final ForgeConfigSpec.BooleanValue RAIDS;
    public static final ForgeConfigSpec.IntValue RAID_MINUTES;
    public static final ForgeConfigSpec.IntValue INCOME_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_FIEFS;
    public static final ForgeConfigSpec.BooleanValue SIEGE_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue FREE_AMMO;
    public static final ForgeConfigSpec.DoubleValue COST_MULT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        PROTECT = b.comment("Les fiefs sont proteges : on ne peut pas casser/poser de blocs chez un autre sauf en guerre").define("protectFiefs", true);
        NPC_FIEFS = b.comment("Nombre de fiefs de seigneurs PNJ generes autour de votre premier fief (0 = aucun)").defineInRange("npcFiefs", 3, 0, 8);
        NPC_WAR = b.comment("Les seigneurs PNJ peuvent vous declarer la guerre").define("npcDeclareWar", true);
        RAIDS = b.comment("Les ennemis en guerre lancent des attaques sur votre fief").define("raids", true);
        RAID_MINUTES = b.comment("Delai moyen entre deux attaques (minutes)").defineInRange("raidMinutes", 8, 1, 120);
        INCOME_SECONDS = b.comment("Intervalle de revenu des fiefs (secondes)").defineInRange("incomeSeconds", 30, 5, 3600);
        MAX_FIEFS = b.comment("Nombre max de fiefs fondes par joueur (les fiefs conquis ne comptent pas)").defineInRange("maxFoundedFiefs", 2, 1, 10);
        SIEGE_BLOCKS = b.comment("Les rochers de catapulte detruisent les blocs").define("siegeBlockDamage", true);
        FREE_AMMO = b.comment("Catapultes et arbaletes sans munitions (mode arcade)").define("freeAmmo", false);
        COST_MULT = b.comment("Multiplicateur des couts en or").defineInRange("costMultiplier", 1.0, 0.1, 10.0);
        SPEC = b.build();
    }

    public static int cost(int base) {
        return Math.max(1, (int) Math.round(base * COST_MULT.get()));
    }
}
