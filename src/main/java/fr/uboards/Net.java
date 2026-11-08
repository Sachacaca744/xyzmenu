package fr.uboards;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class Net {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Uboards.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, EditMsg.class, EditMsg::encode, EditMsg::decode, EditMsg::handle);
        CHANNEL.registerMessage(id++, ImgReqMsg.class, ImgReqMsg::encode, ImgReqMsg::decode, ImgReqMsg::handle);
        CHANNEL.registerMessage(id++, ImgChunkMsg.class, ImgChunkMsg::encode, ImgChunkMsg::decode, ImgChunkMsg::handle);
    }

    /** Client -> serveur : texte (ecran) ou image (tableau) a appliquer. */
    public record EditMsg(boolean remote, BlockPos pos, String text) {
        static void encode(EditMsg m, FriendlyByteBuf buf) {
            buf.writeBoolean(m.remote);
            buf.writeBlockPos(m.pos);
            buf.writeUtf(m.text, 1000);
        }

        static EditMsg decode(FriendlyByteBuf buf) {
            return new EditMsg(buf.readBoolean(), buf.readBlockPos(), buf.readUtf(1000));
        }

        static void handle(EditMsg m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sp != null) {
                    Actions.edit(sp, m.remote(), m.pos(), m.text());
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> serveur : "envoie-moi l'image <hash>". */
    public record ImgReqMsg(String hash) {
        static void encode(ImgReqMsg m, FriendlyByteBuf buf) {
            buf.writeUtf(m.hash, 64);
        }

        static ImgReqMsg decode(FriendlyByteBuf buf) {
            return new ImgReqMsg(buf.readUtf(64));
        }

        static void handle(ImgReqMsg m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sp != null) {
                    Actions.sendImage(sp, m.hash());
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Serveur -> client : un morceau d'image. */
    public record ImgChunkMsg(String hash, int index, int total, byte[] data) {
        static void encode(ImgChunkMsg m, FriendlyByteBuf buf) {
            buf.writeUtf(m.hash, 64);
            buf.writeVarInt(m.index);
            buf.writeVarInt(m.total);
            buf.writeByteArray(m.data);
        }

        static ImgChunkMsg decode(FriendlyByteBuf buf) {
            return new ImgChunkMsg(buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(60000));
        }

        static void handle(ImgChunkMsg m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    fr.uboards.client.ClientImages.onChunk(m.hash(), m.index(), m.total(), m.data())));
            ctx.get().setPacketHandled(true);
        }
    }
}
