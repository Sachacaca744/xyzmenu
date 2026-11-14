package fr.udrone;

import net.minecraftforge.common.ForgeConfigSpec;

public class Cfg {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue BLOCK_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_MULT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        BLOCK_DAMAGE = b.comment("Les explosions des drones detruisent les blocs (false = effets et degats aux entites seulement)")
                .define("blockDamage", false);
        DAMAGE_MULT = b.comment("Multiplicateur des degats des armes des drones")
                .defineInRange("damageMultiplier", 1.0, 0.1, 10.0);
        SPEC = b.build();
    }
}
