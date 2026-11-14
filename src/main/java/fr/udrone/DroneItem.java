package fr.udrone;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class DroneItem extends Item {
    public final Kind kind;

    public DroneItem(Kind k, Properties p) {
        super(p);
        this.kind = k;
    }

    @Override
    public Component getName(ItemStack s) {
        return Component.literal(kind.title);
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        Level l = c.getLevel();
        if (!(l instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        Vec3 pos = Vec3.atBottomCenterOf(c.getClickedPos().relative(c.getClickedFace()));
        if (c.getClickedFace().getAxis().isHorizontal()) pos = pos.add(0, 0.0, 0);
        Drone d = Drone.spawn(sl, kind, pos, c.getPlayer(), c.getItemInHand().getTag());
        if (d == null) return InteractionResult.FAIL;
        if (!sl.noCollision(d.getBoundingBox())) {
            d.setPos(pos.x, pos.y + 1.0, pos.z);
            if (!sl.noCollision(d.getBoundingBox())) {
                if (c.getPlayer() != null) c.getPlayer().displayClientMessage(Component.literal("Pas assez de place."), true);
                return InteractionResult.FAIL;
            }
        }
        sl.addFreshEntity(d);
        if (c.getPlayer() != null) {
            if (!c.getPlayer().getAbilities().instabuild) c.getItemInHand().shrink(1);
            c.getPlayer().displayClientMessage(Component.literal(kind.title + " pose. Lie-le avec la telecommande (clic droit sur le drone)."), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
        int b = s.hasTag() && s.getTag().contains("Batt") ? s.getTag().getInt("Batt") : kind.battery;
        float hp = s.hasTag() && s.getTag().contains("Hp") ? s.getTag().getFloat("Hp") : kind.hp;
        tip.add(Component.literal("Batterie : " + b * 100 / kind.battery + " %  |  PV : " + (int) hp + "/" + (int) kind.hp).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Autonomie : " + kind.battery / 1200 + " min  |  Portee : " + kind.range + " m").withStyle(ChatFormatting.GRAY));
        if (kind.weapons.length == 0) tip.add(Component.literal("Equipement : camera seule").withStyle(ChatFormatting.DARK_GRAY));
        for (int i = 0; i < kind.weapons.length; i++)
            tip.add(Component.literal("Equipement : " + kind.weapons[i].title + " x" + kind.ammo[i]).withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.literal("Clic droit sur un bloc : poser").withStyle(ChatFormatting.DARK_AQUA));
    }
}
