package fr.upaint;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
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

    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Upaint.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        int id = 0;
        CH.registerMessage(id++, ChunkMsg.class, ChunkMsg::encode, ChunkMsg::decode, ChunkMsg::handle);
        CH.registerMessage(id++, DeltaMsg.class, DeltaMsg::encode, DeltaMsg::decode, DeltaMsg::handle);
        CH.registerMessage(id++, SetBrushMsg.class, SetBrushMsg::encode, SetBrushMsg::decode, SetBrushMsg::handle);
    }

    public record ChunkMsg(int cx, int cz, List<PaintData.Entry> list) {
        static void encode(ChunkMsg m, FriendlyByteBuf b) {
            b.writeInt(m.cx);
            b.writeInt(m.cz);
            b.writeVarInt(m.list.size());
            for (PaintData.Entry e : m.list) {
                b.writeLong(e.pos());
                b.writeByte(e.face());
                b.writeUtf(e.tex(), 200);
                b.writeInt(e.color());
            }
        }

        static ChunkMsg decode(FriendlyByteBuf b) {
            int cx = b.readInt();
            int cz = b.readInt();
            int n = b.readVarInt();
            List<PaintData.Entry> l = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                l.add(new PaintData.Entry(b.readLong(), b.readByte(), b.readUtf(200), b.readInt()));
            }
            return new ChunkMsg(cx, cz, l);
        }

        static void handle(ChunkMsg m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    fr.upaint.client.ClientPaint.replaceChunk(m.cx(), m.cz(), m.list())));
            ctx.get().setPacketHandled(true);
        }
    }

    public record DeltaMsg(long pos, int face, String tex, int color) {
        static void encode(DeltaMsg m, FriendlyByteBuf b) {
            b.writeLong(m.pos);
            b.writeByte(m.face);
            b.writeUtf(m.tex, 200);
            b.writeInt(m.color);
        }

        static DeltaMsg decode(FriendlyByteBuf b) {
            return new DeltaMsg(b.readLong(), b.readByte(), b.readUtf(200), b.readInt());
        }

        static void handle(DeltaMsg m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    fr.upaint.client.ClientPaint.delta(m.pos(), m.face(), m.tex(), m.color())));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> serveur : choix de texture/couleur pour l'outil en main. */
    public record SetBrushMsg(String tex, int color) {
        static void encode(SetBrushMsg m, FriendlyByteBuf b) {
            b.writeUtf(m.tex, 200);
            b.writeInt(m.color);
        }

        static SetBrushMsg decode(FriendlyByteBuf b) {
            return new SetBrushMsg(b.readUtf(200), b.readInt());
        }

        static void handle(SetBrushMsg m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sp == null) return;
                ItemStack s = sp.getMainHandItem();
                if (!(s.getItem() instanceof PaintToolItem)) s = sp.getOffhandItem();
                if (s.getItem() instanceof PaintToolItem && net.minecraft.resources.ResourceLocation.tryParse(m.tex()) != null) {
                    s.getOrCreateTag().putString("Tex", m.tex());
                    s.getOrCreateTag().putInt("Color", m.color() & 0xFFFFFF);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
