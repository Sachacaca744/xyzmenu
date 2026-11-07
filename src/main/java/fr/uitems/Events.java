package fr.uitems;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class Events {

    /** Commande tres longue pour obtenir l'epee (volontairement difficile a taper par hasard). */
    private static final String SWORD_COMMAND =
            "epee-du-fondateur-je-confirme-etre-le-fondateur-du-serveur-et-je-veux-lepee-supreme";

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("staff")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("item")
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            giveShirt(p, "staff");
                            giveShirt(p, "builder");
                            giveShirt(p, "fondateur");
                            return 1;
                        })
                        .then(Commands.literal("staff").executes(ctx -> {
                            giveShirt(ctx.getSource().getPlayerOrException(), "staff");
                            return 1;
                        }))
                        .then(Commands.literal("builder").executes(ctx -> {
                            giveShirt(ctx.getSource().getPlayerOrException(), "builder");
                            return 1;
                        }))
                        .then(Commands.literal("fondateur").executes(ctx -> {
                            giveShirt(ctx.getSource().getPlayerOrException(), "fondateur");
                            return 1;
                        })))
                .then(Commands.literal(SWORD_COMMAND)
                        .requires(src -> src.hasPermission(4))
                        .executes(ctx -> giveSword(ctx.getSource()))));
    }

    private static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack) && !stack.isEmpty()) {
            p.drop(stack, false);
        }
    }

    private static void giveShirt(ServerPlayer p, String kind) {
        Item item;
        String name;
        ChatFormatting color;
        switch (kind) {
            case "builder":
                item = ModItems.BUILDER_SHIRT.get();
                name = "T-shirt BUILDER";
                color = ChatFormatting.GOLD;
                break;
            case "fondateur":
                item = ModItems.FOUNDER_SHIRT.get();
                name = "T-shirt FONDATEUR";
                color = ChatFormatting.YELLOW;
                break;
            default:
                item = ModItems.STAFF_SHIRT.get();
                name = "T-shirt STAFF";
                color = ChatFormatting.RED;
                break;
        }
        ItemStack stack = new ItemStack(item);
        stack.setHoverName(Component.literal(name).withStyle(color).withStyle(ChatFormatting.BOLD)
                .withStyle(s -> s.withItalic(false)));
        give(p, stack);
        p.sendSystemMessage(Component.literal("Tu as reçu : " + name).withStyle(ChatFormatting.GREEN));
    }

    private static int giveSword(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ItemStack sword = new ItemStack(ModItems.FOUNDER_SWORD.get());
        CompoundTag tag = sword.getOrCreateTag();
        tag.putBoolean("Unbreakable", true);
        sword.setHoverName(Component.literal("Épée du Fondateur").withStyle(ChatFormatting.GOLD)
                .withStyle(ChatFormatting.BOLD).withStyle(s -> s.withItalic(false)));
        give(p, sword);
        p.sendSystemMessage(Component.literal("Tu as reçu l'Épée du Fondateur.").withStyle(ChatFormatting.GOLD));
        return 1;
    }

    /**
     * Avec l'epee du fondateur en main, un coup tue la cible meme en creatif.
     * Reserve aux joueurs de niveau OP 4 : pour les autres, c'est une epee normale.
     */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        if (!(attacker.getMainHandItem().getItem() instanceof FounderSword)) {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        if (!attacker.createCommandSourceStack().hasPermission(4)) {
            return;
        }
        // genericKill ignore l'invincibilite du mode creatif, les armures et les totems.
        target.hurt(attacker.damageSources().genericKill(), Float.MAX_VALUE);
        event.setCanceled(true);
    }
}
