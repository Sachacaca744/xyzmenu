package fr.udynamax;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class Net {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Udynamax.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CH.registerMessage(0, ActivateMsg.class, ActivateMsg::encode, ActivateMsg::decode, ActivateMsg::handle);
        CH.registerMessage(1, ScaleMsg.class, ScaleMsg::encode, ScaleMsg::decode, ScaleMsg::handle);
    }

    /** Client -> serveur : "je dynamaxe mon Pokemon". */
    public record ActivateMsg() {
        static void encode(ActivateMsg m, FriendlyByteBuf b) {
        }

        static ActivateMsg decode(FriendlyByteBuf b) {
            return new ActivateMsg();
        }

        static void handle(ActivateMsg m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sp == null) return;
                String err = Dyn.tryStart(sp);
                if (err != null) sp.displayClientMessage(Component.literal(err), true);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Serveur -> client : taille d'un Pokemon (Dynamax). */
    public record ScaleMsg(int entityId, float scale) {
        static void encode(ScaleMsg m, FriendlyByteBuf b) {
            b.writeVarInt(m.entityId);
            b.writeFloat(m.scale);
        }

        static ScaleMsg decode(FriendlyByteBuf b) {
            return new ScaleMsg(b.readVarInt(), b.readFloat());
        }

        static void handle(ScaleMsg m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                            fr.udynamax.client.ClientScale.apply(m.entityId(), m.scale())));
            ctx.get().setPacketHandled(true);
        }
    }
}
