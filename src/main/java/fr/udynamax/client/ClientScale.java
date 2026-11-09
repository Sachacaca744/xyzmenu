package fr.udynamax.client;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public class ClientScale {
    public static void apply(int id, float scale) {
        if (Minecraft.getInstance().level == null) return;
        Entity e = Minecraft.getInstance().level.getEntity(id);
        if (e instanceof PokemonEntity pe && pe.getPokemon() != null) {
            if (pe.getPokemon().getScaleModifier() != scale) {
                pe.getPokemon().setScaleModifier(scale);
                pe.refreshDimensions();
            }
        }
    }
}
