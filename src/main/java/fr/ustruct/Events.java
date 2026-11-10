package fr.ustruct;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class Events {
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.getServer() != null) Jobs.tick(e.getServer());
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent e) {
        Jobs.stop();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) Sel.sync(sp);
    }

    private static boolean wand(ItemStack s) {
        return s.is(Reg.WAND.get());
    }

    @SubscribeEvent
    public static void onLeft(PlayerInteractEvent.LeftClickBlock e) {
        if (!wand(e.getItemStack())) return;
        e.setCanceled(true);
        if (e.getLevel().isClientSide() || e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        if (e.getEntity() instanceof ServerPlayer sp && sp.hasPermissions(2)) Sel.set(sp, true, e.getPos());
    }

    @SubscribeEvent
    public static void onRight(PlayerInteractEvent.RightClickBlock e) {
        if (!wand(e.getItemStack())) return;
        e.setCanceled(true);
        if (e.getLevel().isClientSide() || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (e.getEntity() instanceof ServerPlayer sp && sp.hasPermissions(2)) Sel.set(sp, false, e.getPos());
    }
}
