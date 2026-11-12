package fr.ugta;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class Net {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Ugta.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CH.registerMessage(0, StarsMsg.class, StarsMsg::encode, StarsMsg::decode, StarsMsg::handle);
        CH.registerMessage(1, HornMsg.class, HornMsg::encode, HornMsg::decode, HornMsg::handle);
    }

    /** Serveur -> client : niveau de recherche. seen = un policier te voit. */
    public record StarsMsg(int stars, boolean seen) {
        static void encode(StarsMsg m, FriendlyByteBuf b) {
            b.writeByte(m.stars);
            b.writeBoolean(m.seen);
        }

        static StarsMsg decode(FriendlyByteBuf b) {
            return new StarsMsg(b.readByte(), b.readBoolean());
        }

        static void handle(StarsMsg m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> fr.ugta.client.StarsHud.set(m.stars(), m.seen())));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> serveur : klaxon. */
    public record HornMsg() {
        static void encode(HornMsg m, FriendlyByteBuf b) {
        }

        static HornMsg decode(FriendlyByteBuf b) {
            return new HornMsg();
        }

        static void handle(HornMsg m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sp != null && sp.getVehicle() instanceof Vehicle v && v.getControllingPassenger() == sp) v.horn();
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
