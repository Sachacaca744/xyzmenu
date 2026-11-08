package fr.utaxi;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class Net {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Utaxi.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CHANNEL.registerMessage(0, OpenMsg.class, OpenMsg::encode, OpenMsg::decode, OpenMsg::handle);
        CHANNEL.registerMessage(1, GoMsg.class, GoMsg::encode, GoMsg::decode, GoMsg::handle);
    }

    /** Serveur -> client : ouvre le menu avec la liste des destinations. */
    public record OpenMsg(List<String> names) {
        static void encode(OpenMsg m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.names.size());
            for (String n : m.names) buf.writeUtf(n, 64);
        }

        static OpenMsg decode(FriendlyByteBuf buf) {
            int n = Math.min(buf.readVarInt(), 200);
            List<String> l = new ArrayList<>();
            for (int i = 0; i < n; i++) l.add(buf.readUtf(64));
            return new OpenMsg(l);
        }

        static void handle(OpenMsg m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    fr.utaxi.client.ClientHooks.openTaxi(m.names())));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> serveur : le joueur a choisi une destination. */
    public record GoMsg(int index) {
        static void encode(GoMsg m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.index);
        }

        static GoMsg decode(FriendlyByteBuf buf) {
            return new GoMsg(buf.readVarInt());
        }

        static void handle(GoMsg m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sp != null) Events.go(sp, m.index());
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
