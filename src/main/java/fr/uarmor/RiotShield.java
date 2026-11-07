package fr.uarmor;

import net.minecraft.world.item.ShieldItem;

/** Bouclier anti-emeute : un vrai bouclier (blocage, recul), mais indestructible une fois donne par /staff admin. */
public class RiotShield extends ShieldItem {
    public RiotShield(Properties props) {
        super(props);
    }
}
