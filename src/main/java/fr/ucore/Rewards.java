package fr.ucore;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

public class Rewards {

    public static void msg(ServerPlayer p, String text, ChatFormatting color) {
        p.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    private static void giveStack(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack) && !stack.isEmpty()) {
            p.drop(stack, false);
        }
    }

    public static String describe(Cfg.Reward r) {
        String label = r.label == null ? "" : r.label;
        switch (r.type) {
            case "MONEY":
                return r.value + " " + Cfg.get().currency;
            case "POKEMON":
                return "Pokémon : " + (label.isBlank() ? r.value : label);
            case "COMMAND":
                return label.isBlank() ? "commande : " + r.value : label;
            default:
                return r.count + "x " + (label.isBlank() ? r.value : label);
        }
    }

    /** Donne une recompense. Renvoie false si elle est invalide. */
    public static boolean give(ServerPlayer p, Cfg.Reward r) {
        switch (r.type) {
            case "MONEY": {
                long amount;
                try {
                    amount = Long.parseLong(r.value.trim());
                } catch (NumberFormatException e) {
                    return false;
                }
                if (amount <= 0) {
                    return false;
                }
                Store.addBalance(p.getUUID(), amount);
                msg(p, "+" + amount + " " + Cfg.get().currency, ChatFormatting.GREEN);
                return true;
            }
            case "POKEMON": {
                if (r.value.isBlank()) {
                    return false;
                }
                runCommand(p, "givepokemonother %player% " + r.value.trim());
                msg(p, "Tu as obtenu : " + describe(r), ChatFormatting.GREEN);
                return true;
            }
            case "COMMAND": {
                if (r.value.isBlank()) {
                    return false;
                }
                runCommand(p, r.value.trim());
                msg(p, "Tu as obtenu : " + describe(r), ChatFormatting.GREEN);
                return true;
            }
            default:
                return giveItem(p, r);
        }
    }

    private static boolean giveItem(ServerPlayer p, Cfg.Reward r) {
        String v = r.value.trim();
        String nbt = null;
        int brace = v.indexOf('{');
        if (brace >= 0) {
            nbt = v.substring(brace);
            v = v.substring(0, brace).trim();
        }
        ResourceLocation id = ResourceLocation.tryParse(v);
        if (id == null) {
            return false;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) {
            return false;
        }
        CompoundTag tag = null;
        if (nbt != null) {
            try {
                tag = TagParser.parseTag(nbt);
            } catch (CommandSyntaxException e) {
                return false;
            }
        }
        int remaining = Math.max(1, r.count);
        while (remaining > 0) {
            int n = Math.min(remaining, item.getMaxStackSize());
            ItemStack stack = new ItemStack(item, n);
            if (tag != null) {
                stack.setTag(tag.copy());
            }
            if (r.label != null && !r.label.isBlank()) {
                stack.setHoverName(Component.literal(r.label).withStyle(s -> s.withItalic(false)));
            }
            giveStack(p, stack);
            remaining -= n;
        }
        msg(p, "Tu as obtenu : " + describe(r), ChatFormatting.GREEN);
        return true;
    }

    public static void runCommand(ServerPlayer p, String command) {
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        cmd = cmd.replace("%player%", p.getGameProfile().getName());
        MinecraftServer server = p.getServer();
        if (server == null) {
            return;
        }
        try {
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(), cmd);
        } catch (Exception ignored) {
        }
    }

    public static boolean teleport(ServerPlayer p, Store.Spawn s) {
        MinecraftServer server = p.getServer();
        if (server == null || !s.set) {
            return false;
        }
        ResourceLocation dimId = ResourceLocation.tryParse(s.dim);
        ServerLevel level = dimId == null ? null
                : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
        if (level == null) {
            return false;
        }
        p.teleportTo(level, s.x, s.y, s.z, s.yaw, s.pitch);
        return true;
    }
}
