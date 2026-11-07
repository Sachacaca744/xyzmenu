package fr.uarmor;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class Events {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("staff")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("admin")
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            give(p, ModItems.HELMET.get(), "Casque Admin");
                            give(p, ModItems.CHESTPLATE.get(), "Plastron Admin");
                            give(p, ModItems.LEGGINGS.get(), "Jambières Admin");
                            give(p, ModItems.BOOTS.get(), "Bottes Admin");
                            give(p, ModItems.RIOT_SHIELD.get(), "Bouclier Anti-Émeute Admin");
                            return 1;
                        })
                        .then(Commands.literal("casque").executes(ctx -> {
                            give(ctx.getSource().getPlayerOrException(), ModItems.HELMET.get(), "Casque Admin");
                            return 1;
                        }))
                        .then(Commands.literal("plastron").executes(ctx -> {
                            give(ctx.getSource().getPlayerOrException(), ModItems.CHESTPLATE.get(), "Plastron Admin");
                            return 1;
                        }))
                        .then(Commands.literal("jambieres").executes(ctx -> {
                            give(ctx.getSource().getPlayerOrException(), ModItems.LEGGINGS.get(), "Jambières Admin");
                            return 1;
                        }))
                        .then(Commands.literal("bottes").executes(ctx -> {
                            give(ctx.getSource().getPlayerOrException(), ModItems.BOOTS.get(), "Bottes Admin");
                            return 1;
                        }))
                        .then(Commands.literal("bouclier").executes(ctx -> {
                            give(ctx.getSource().getPlayerOrException(), ModItems.RIOT_SHIELD.get(),
                                    "Bouclier Anti-Émeute Admin");
                            return 1;
                        }))));
    }

    private static void give(ServerPlayer p, Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putBoolean("Unbreakable", true);
        stack.setHoverName(Component.literal(name).withStyle(ChatFormatting.DARK_RED)
                .withStyle(ChatFormatting.BOLD).withStyle(s -> s.withItalic(false)));
        if (!p.getInventory().add(stack) && !stack.isEmpty()) {
            p.drop(stack, false);
        }
        p.sendSystemMessage(Component.literal("Tu as reçu : " + name).withStyle(ChatFormatting.GREEN));
    }

    private static boolean isAdminPiece(ItemStack s) {
        Item i = s.getItem();
        return i == ModItems.HELMET.get() || i == ModItems.CHESTPLATE.get()
                || i == ModItems.LEGGINGS.get() || i == ModItems.BOOTS.get();
    }

    /** Bonus de panoplie complete (4 pieces) : resistance, feu, respiration, vision de nuit. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (p.tickCount % 40 != 0) return;
        if (isAdminPiece(p.getItemBySlot(EquipmentSlot.HEAD))
                && isAdminPiece(p.getItemBySlot(EquipmentSlot.CHEST))
                && isAdminPiece(p.getItemBySlot(EquipmentSlot.LEGS))
                && isAdminPiece(p.getItemBySlot(EquipmentSlot.FEET))) {
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 2, true, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100, 0, true, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 100, 0, true, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, false));
        }
    }
}
