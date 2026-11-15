package fr.uwar;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Munitions, bidon de carburant, kit de reparation. Clic droit sur l'appareil pour ravitailler. */
public class SupplyItem extends Item {
    public enum Type { AMMO, FUEL, REPAIR }

    public static final float FUEL_PER_CAN = 300f, REPAIR_HP = 45f;
    public final Type type;
    public final W weapon;

    public SupplyItem(Type t, W w, Properties p) {
        super(p);
        this.type = t;
        this.weapon = w;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        switch (type) {
            case AMMO -> tip.add(Component.literal("+" + weapon.perItem + " " + weapon.label + " (clic droit sur l'appareil, ou touche R en vol)"));
            case FUEL -> tip.add(Component.literal("+" + (int) FUEL_PER_CAN + " carburant (clic droit sur l'appareil)"));
            case REPAIR -> tip.add(Component.literal("+" + (int) REPAIR_HP + " points de coque (clic droit sur l'appareil)"));
        }
    }

    /** Clic droit dans le vide : ravitaille l'appareil que l'on regarde (plus pratique que viser exactement). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level lv, Player p, InteractionHand h) {
        ItemStack st = p.getItemInHand(h);
        if (p.getVehicle() instanceof Aircraft a) {
            if (!lv.isClientSide) a.supply(p, st);
            return InteractionResultHolder.sidedSuccess(st, lv.isClientSide);
        }
        Aircraft best = null;
        double bd = 36;
        for (Aircraft a : lv.getEntitiesOfClass(Aircraft.class, new AABB(p.position(), p.position()).inflate(6))) {
            double d = a.distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = a;
            }
        }
        if (best != null) {
            if (!lv.isClientSide) best.supply(p, st);
            return InteractionResultHolder.sidedSuccess(st, lv.isClientSide);
        }
        return InteractionResultHolder.pass(st);
    }
}
