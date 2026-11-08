package fr.utaxi;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.server.ServerStartingEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Events {
    /** joueurs actuellement dans une cabine (position de la cabine). */
    private static final Map<UUID, BlockPos> INSIDE = new HashMap<>();
    private static final Map<UUID, Long> LAST_TRAVEL = new HashMap<>();

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent e) {
        TaxiCfg.load();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        INSIDE.remove(e.getEntity().getUUID());
        LAST_TRAVEL.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp)) return;
        if (sp.tickCount % 4 != 0) return;
        BlockPos p = sp.blockPosition();
        BlockState st = sp.level().getBlockState(p);
        boolean inside = st.is(Reg.BOOTH.get());
        UUID id = sp.getUUID();
        if (inside) {
            if (!INSIDE.containsKey(id)) {
                INSIDE.put(id, p.immutable());
                List<String> names = new ArrayList<>();
                for (TaxiCfg.Dest d : TaxiCfg.get().destinations) names.add(d.name);
                if (names.isEmpty()) {
                    sp.sendSystemMessage(Component.literal("Le taxi n'a encore aucune destination (un OP doit faire /taxi add <nom>).")
                            .withStyle(ChatFormatting.YELLOW));
                } else {
                    Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp), new Net.OpenMsg(names));
                }
            }
        } else {
            INSIDE.remove(id);
        }
    }

    public static void go(ServerPlayer sp, int index) {
        BlockPos at = INSIDE.get(sp.getUUID());
        if (at == null || sp.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5) > 16) {
            sp.sendSystemMessage(Component.literal("Tu dois être dans la cabine.").withStyle(ChatFormatting.RED));
            return;
        }
        long now = sp.server.getTickCount();
        Long last = LAST_TRAVEL.get(sp.getUUID());
        if (last != null && now - last < 40) return;
        List<TaxiCfg.Dest> list = TaxiCfg.get().destinations;
        if (index < 0 || index >= list.size()) return;
        TaxiCfg.Dest d = list.get(index);
        ServerLevel target = sp.server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(d.dim)));
        if (target == null) {
            sp.sendSystemMessage(Component.literal("Destination introuvable (dimension absente).").withStyle(ChatFormatting.RED));
            return;
        }
        LAST_TRAVEL.put(sp.getUUID(), now);
        ServerLevel from = sp.serverLevel();
        from.playSound(null, sp.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.2F);
        from.sendParticles(ParticleTypes.CLOUD, sp.getX(), sp.getY() + 1, sp.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
        sp.teleportTo(target, d.x, d.y, d.z, d.yaw, d.pitch);
        target.playSound(null, sp.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.9F);
        target.sendParticles(ParticleTypes.CLOUD, d.x, d.y + 1, d.z, 30, 0.4, 0.8, 0.4, 0.05);
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
        sp.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Taxi Corvaillus").withStyle(ChatFormatting.AQUA)));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Bienvenue : " + d.name).withStyle(ChatFormatting.WHITE)));
        INSIDE.remove(sp.getUUID());
    }
}
