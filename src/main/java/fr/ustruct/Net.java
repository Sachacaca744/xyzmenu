package fr.ustruct;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class Net {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Ustruct.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CH.registerMessage(0, SelMsg.class, SelMsg::encode, SelMsg::decode, SelMsg::handle);
    }

    /** Serveur -> client : selection actuelle (pour l'afficher). */
    public record SelMsg(BlockPos a, BlockPos b, String dim) {
        static void encode(SelMsg m, FriendlyByteBuf buf) {
            buf.writeBoolean(m.a != null);
            if (m.a != null) buf.writeBlockPos(m.a);
            buf.writeBoolean(m.b != null);
            if (m.b != null) buf.writeBlockPos(m.b);
            buf.writeUtf(m.dim, 200);
        }

        static SelMsg decode(FriendlyByteBuf buf) {
            BlockPos a = buf.readBoolean() ? buf.readBlockPos() : null;
            BlockPos b = buf.readBoolean() ? buf.readBlockPos() : null;
            return new SelMsg(a, b, buf.readUtf(200));
        }

        static void handle(SelMsg m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    fr.ustruct.client.ClientSel.set(m.a(), m.b(), m.dim())));
            ctx.get().setPacketHandled(true);
        }
    }
}
