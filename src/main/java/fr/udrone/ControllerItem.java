package fr.udrone;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Telecommande : liste de drones lies. Clic droit = piloter le drone selectionne, Maj+clic = changer de drone. */
public class ControllerItem extends Item {
    public ControllerItem(Properties p) {
        super(p);
    }

    public static List<UUID> list(ItemStack s) {
        List<UUID> out = new ArrayList<>();
        if (!s.hasTag()) return out;
        ListTag l = s.getTag().getList("Drones", Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) {
            try {
                out.add(UUID.fromString(l.getString(i)));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    static void save(ItemStack s, List<UUID> ids) {
        ListTag l = new ListTag();
        for (UUID u : ids) l.add(StringTag.valueOf(u.toString()));
        s.getOrCreateTag().put("Drones", l);
    }

    public static boolean bind(ItemStack s, Drone d) {
        List<UUID> ids = list(s);
        if (ids.contains(d.getUUID()) || ids.size() >= 8) return false;
        ids.add(d.getUUID());
        save(s, ids);
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand h) {
        ItemStack s = p.getItemInHand(h);
        if (!(level instanceof ServerLevel sl) || !(p instanceof ServerPlayer sp)) return InteractionResultHolder.success(s);
        List<UUID> ids = list(s);
        // purge des drones disparus (si leur chunk est charge)
        List<UUID> alive = new ArrayList<>();
        for (UUID u : ids) {
            Entity e = sl.getEntity(u);
            if (e instanceof Drone d && d.state() != Drone.DEAD) alive.add(u);
            else if (e == null) alive.add(u);
        }
        if (alive.size() != ids.size()) {
            ids = alive;
            save(s, ids);
        }
        if (ids.isEmpty()) {
            p.displayClientMessage(Component.literal("Aucun drone lie : clic droit sur un drone avec la telecommande."), true);
            return InteractionResultHolder.fail(s);
        }
        CompoundTag t = s.getOrCreateTag();
        int sel = Math.floorMod(t.getInt("Sel"), ids.size());
        if (p.isShiftKeyDown()) {
            sel = (sel + 1) % ids.size();
            t.putInt("Sel", sel);
            Entity e = sl.getEntity(ids.get(sel));
            p.displayClientMessage(Component.literal("Drone selectionne " + (sel + 1) + "/" + ids.size() + " : "
                    + (e instanceof Drone d ? d.kind.title : "hors de portee / non charge")), true);
            return InteractionResultHolder.success(s);
        }
        Entity e = sl.getEntity(ids.get(sel));
        if (e instanceof Drone d) {
            d.station = null;
            if (d.takeControl(sp)) p.displayClientMessage(Component.literal("Controle de : " + d.kind.title), true);
        } else p.displayClientMessage(Component.literal("Ce drone est hors de portee (chunk non charge)."), true);
        return InteractionResultHolder.success(s);
    }

    @Override
    public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal("Drones lies : " + list(s).size() + "/8").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Clic droit : piloter le drone selectionne").withStyle(ChatFormatting.DARK_AQUA));
        tip.add(Component.literal("Maj + clic droit : drone suivant").withStyle(ChatFormatting.DARK_AQUA));
        tip.add(Component.literal("Clic droit sur un drone : le lier").withStyle(ChatFormatting.DARK_AQUA));
        tip.add(Component.literal("Clic droit sur une station : lier tous les drones").withStyle(ChatFormatting.DARK_AQUA));
    }
}
