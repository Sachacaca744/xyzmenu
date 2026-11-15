package fr.uwar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AircraftItem extends Item {
    private final Kind kind;

    public AircraftItem(Kind k, Properties p) {
        super(p);
        this.kind = k;
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        Level lv = c.getLevel();
        if (lv.isClientSide) return InteractionResult.SUCCESS;
        BlockPos pos = c.getClickedPos().relative(c.getClickedFace());
        if (c.getClickedFace() != Direction.UP && c.getClickedFace() != Direction.DOWN) pos = c.getClickedPos().relative(c.getClickedFace());
        Aircraft a = new Aircraft(Reg.AIRCRAFTS.get(kind).get(), lv, kind);
        double y = c.getClickedFace() == Direction.UP ? c.getClickedPos().getY() + 1.0 : pos.getY();
        a.moveTo(pos.getX() + 0.5, y, pos.getZ() + 0.5, c.getRotation() + 180f, 0f);
        a.applyStack(c.getItemInHand());
        if (!lv.noCollision(a, a.getBoundingBox())) {
            a.moveTo(a.getX(), a.getY() + 1.0, a.getZ());
            if (!lv.noCollision(a, a.getBoundingBox())) return InteractionResult.FAIL;
        }
        lv.addFreshEntity(a);
        if (c.getPlayer() == null || !c.getPlayer().getAbilities().instabuild) c.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal(kind.heli() ? "Clic droit sur le sol pour le poser, puis clic droit dessus pour monter" : "Clic droit sur une piste/le sol pour le poser, puis clic droit dessus pour monter"));
        CompoundTag t = s.getTagElement("ac");
        if (t != null) {
            tip.add(Component.literal("Coque " + Math.round(t.getFloat("Hp") * 100 / kind.maxHp) + " %  |  Carburant " + Math.round(t.getFloat("Fuel") * 100 / kind.fuelCap) + " %"));
            int[] a = t.getIntArray("Am");
            StringBuilder sb = new StringBuilder();
            for (W w : kind.weapons) if (a.length > w.ordinal()) sb.append(w.label).append(' ').append(a[w.ordinal()]).append("  ");
            tip.add(Component.literal(sb.toString()));
        } else {
            tip.add(Component.literal("Livre avec 40 % de carburant et 150 obus de canon"));
        }
    }
}
