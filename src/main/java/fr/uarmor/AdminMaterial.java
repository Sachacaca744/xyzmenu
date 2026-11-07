package fr.uarmor;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

/** Armure Admin : bien au-dessus de la nethérite. Texture : textures/models/armor/admin_layer_1/2.png */
public enum AdminMaterial implements ArmorMaterial {
    ADMIN;

    private static final int[] BASE_DURABILITY = {11, 16, 15, 13}; // casque, plastron, jambieres, bottes
    private static final int[] DEFENSE = {6, 12, 10, 6};          // total 34 (le jeu plafonne a 20)

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return BASE_DURABILITY[type.ordinal()] * 500;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return DEFENSE[type.ordinal()];
    }

    @Override
    public int getEnchantmentValue() {
        return 30;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_NETHERITE;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.EMPTY;
    }

    @Override
    public String getName() {
        return Uarmor.MODID + ":admin";
    }

    @Override
    public float getToughness() {
        return 12.0F;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.25F; // par piece : 4 pieces = aucun recul
    }
}
