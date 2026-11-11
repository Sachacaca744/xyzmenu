package fr.uarms;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class Events {
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.getServer() != null) SoldierSpawner.tick(e.getServer());
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        SoldierData.ENABLED = SoldierData.get(e.getServer()).enabled;
        if (!SoldierData.ENABLED) SoldierSpawner.sweep(e.getServer());
    }

    /** Quand les soldats sont desactives, aucun ne peut (re)apparaitre, meme depuis une sauvegarde. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && e.getEntity() instanceof Soldier && !SoldierData.ENABLED && !Cmds.forcing) {
            e.setCanceled(true);
        }
    }
}
