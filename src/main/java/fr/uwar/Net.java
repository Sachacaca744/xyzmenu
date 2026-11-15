package fr.uwar;

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
            new ResourceLocation(Uwar.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CH.registerMessage(0, InputMsg.class, InputMsg::encode, InputMsg::decode, InputMsg::handle);
        CH.registerMessage(1, CmdMsg.class, CmdMsg::encode, CmdMsg::decode, CmdMsg::handle);
        CH.registerMessage(2, LoadoutMsg.class, LoadoutMsg::encode, LoadoutMsg::decode, LoadoutMsg::handle);
    }

    static Aircraft piloted(ServerPlayer sp) {
        if (sp != null && sp.getVehicle() instanceof Aircraft a && a.getControllingPassenger() == sp) return a;
        return null;
    }

    /** Client -> serveur : etat des commandes (chaque tick). */
    public record InputMsg(float thr, boolean boost, boolean fire, boolean up, float yaw, float pitch, float impact) {
        static void encode(InputMsg m, FriendlyByteBuf b) {
            b.writeFloat(m.thr); b.writeBoolean(m.boost); b.writeBoolean(m.fire); b.writeBoolean(m.up);
            b.writeFloat(m.yaw); b.writeFloat(m.pitch); b.writeFloat(m.impact);
        }
        static InputMsg decode(FriendlyByteBuf b) {
            return new InputMsg(b.readFloat(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readFloat(), b.readFloat(), b.readFloat());
        }
        static void handle(InputMsg m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                Aircraft a = piloted(sp);
                if (a != null) a.setInput(m.thr, m.boost, m.fire, m.up, m.yaw, m.pitch, m.impact);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static final int C_WEAPON = 1, C_RELOAD = 2, C_WEAPON_PREV = 3;

    /** Client -> serveur : commandes ponctuelles. */
    public record CmdMsg(int cmd) {
        static void encode(CmdMsg m, FriendlyByteBuf b) { b.writeByte(m.cmd); }
        static CmdMsg decode(FriendlyByteBuf b) { return new CmdMsg(b.readByte()); }
        static void handle(CmdMsg m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                Aircraft a = piloted(sp);
                if (a == null) return;
                switch (m.cmd) {
                    case C_WEAPON -> a.nextWeapon(1);
                    case C_WEAPON_PREV -> a.nextWeapon(-1);
                    case C_RELOAD -> a.reloadFrom(sp);
                    default -> {
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> serveur : nouvelle configuration d'armement (interface touche 5). */
    public record LoadoutMsg(int id, int mask) {
        static void encode(LoadoutMsg m, FriendlyByteBuf b) { b.writeVarInt(m.id); b.writeVarInt(m.mask); }
        static LoadoutMsg decode(FriendlyByteBuf b) { return new LoadoutMsg(b.readVarInt(), b.readVarInt()); }
        static void handle(LoadoutMsg m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sp == null) return;
                if (sp.level().getEntity(m.id) instanceof Aircraft a && (sp.getVehicle() == a || a.distanceTo(sp) < 12)) {
                    String err = a.setLoadout(sp, m.mask);
                    if (err != null) sp.displayClientMessage(net.minecraft.network.chat.Component.literal(err), true);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
