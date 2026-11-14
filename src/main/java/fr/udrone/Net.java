package fr.udrone;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class Net {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Udrone.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CH.registerMessage(0, ControlMsg.class, ControlMsg::encode, ControlMsg::decode, ControlMsg::handle);
        CH.registerMessage(1, InputMsg.class, InputMsg::encode, InputMsg::decode, InputMsg::handle);
        CH.registerMessage(2, CmdMsg.class, CmdMsg::encode, CmdMsg::decode, CmdMsg::handle);
        CH.registerMessage(3, StationMsg.class, StationMsg::encode, StationMsg::decode, StationMsg::handle);
        CH.registerMessage(4, StationCmd.class, StationCmd::encode, StationCmd::decode, StationCmd::handle);
    }

    /** Serveur -> client : id du drone pilote (-1 = fin de pilotage). */
    public record ControlMsg(int id) {
        static void encode(ControlMsg m, FriendlyByteBuf b) { b.writeInt(m.id); }
        static ControlMsg decode(FriendlyByteBuf b) { return new ControlMsg(b.readInt()); }
        static void handle(ControlMsg m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.udrone.client.ClientCtl.set(m.id())));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> serveur : commandes de vol (chaque tick). */
    public record InputMsg(float f, float s, float u, float yaw, float pitch, boolean fire) {
        static void encode(InputMsg m, FriendlyByteBuf b) {
            b.writeFloat(m.f); b.writeFloat(m.s); b.writeFloat(m.u); b.writeFloat(m.yaw); b.writeFloat(m.pitch); b.writeBoolean(m.fire);
        }
        static InputMsg decode(FriendlyByteBuf b) {
            return new InputMsg(b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readBoolean());
        }
        static void handle(InputMsg m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sp == null) return;
                Drone d = Pilot.CTRL.get(sp.getUUID());
                if (d != null && !d.isRemoved()) d.setInput(m.f, m.s, m.u, m.yaw, m.pitch, m.fire);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static final int C_EXIT = 0, C_WEAPON = 1, C_RTB = 2, C_NEXT = 3;

    /** Client -> serveur : commandes ponctuelles. */
    public record CmdMsg(int cmd) {
        static void encode(CmdMsg m, FriendlyByteBuf b) { b.writeByte(m.cmd); }
        static CmdMsg decode(FriendlyByteBuf b) { return new CmdMsg(b.readByte()); }
        static void handle(CmdMsg m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sp == null) return;
                Drone d = Pilot.CTRL.get(sp.getUUID());
                if (d == null || d.isRemoved()) return;
                switch (m.cmd) {
                    case C_EXIT -> d.release();
                    case C_WEAPON -> d.nextWeapon();
                    case C_RTB -> d.toggleRtb(sp);
                    case C_NEXT -> Pilot.next(sp, d);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record Entry(UUID id, int kind, boolean live, float x, float y, float z, float yaw, int batt, float hp,
                        int state, int ammo, int sel, float heat, float sig, boolean piloted) {
    }

    /** Serveur -> client : donnees de la station. */
    public record StationMsg(BlockPos pos, boolean open, List<Entry> list) {
        static void encode(StationMsg m, FriendlyByteBuf b) {
            b.writeBlockPos(m.pos);
            b.writeBoolean(m.open);
            b.writeByte(m.list.size());
            for (Entry e : m.list) {
                b.writeUUID(e.id); b.writeByte(e.kind); b.writeBoolean(e.live);
                b.writeFloat(e.x); b.writeFloat(e.y); b.writeFloat(e.z); b.writeFloat(e.yaw);
                b.writeInt(e.batt); b.writeFloat(e.hp); b.writeByte(e.state); b.writeInt(e.ammo); b.writeByte(e.sel);
                b.writeFloat(e.heat); b.writeFloat(e.sig); b.writeBoolean(e.piloted);
            }
        }
        static StationMsg decode(FriendlyByteBuf b) {
            BlockPos p = b.readBlockPos();
            boolean open = b.readBoolean();
            int n = b.readByte();
            List<Entry> l = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                l.add(new Entry(b.readUUID(), b.readByte(), b.readBoolean(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(),
                        b.readInt(), b.readFloat(), b.readByte(), b.readInt(), b.readByte(), b.readFloat(), b.readFloat(), b.readBoolean()));
            }
            return new StationMsg(p, open, l);
        }
        static void handle(StationMsg m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.udrone.client.ClientHooks.station(m)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static final int S_REFRESH = 0, S_TAKE = 1, S_RTB = 2, S_UNLINK = 3, S_HOVER = 4;

    /** Client -> serveur : commandes de la station. */
    public record StationCmd(BlockPos pos, UUID id, int cmd) {
        static void encode(StationCmd m, FriendlyByteBuf b) { b.writeBlockPos(m.pos); b.writeUUID(m.id); b.writeByte(m.cmd); }
        static StationCmd decode(FriendlyByteBuf b) { return new StationCmd(b.readBlockPos(), b.readUUID(), b.readByte()); }
        static void handle(StationCmd m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sp == null) return;
                ServerLevel sl = sp.serverLevel();
                if (sp.distanceToSqr(m.pos.getX() + 0.5, m.pos.getY() + 0.5, m.pos.getZ() + 0.5) > 64) return;
                if (!(sl.getBlockEntity(m.pos) instanceof StationBE be)) return;
                if (m.cmd == S_REFRESH) {
                    StationBlock.sendData(sp, m.pos, be, false);
                    return;
                }
                if (!be.drones.containsKey(m.id)) return;
                Entity e = sl.getEntity(m.id);
                switch (m.cmd) {
                    case S_TAKE -> {
                        if (e instanceof Drone d) {
                            d.station = m.pos;
                            d.takeControl(sp);
                        }
                    }
                    case S_RTB -> {
                        if (e instanceof Drone d) {
                            if (d.state() == Drone.RTB && !d.forcedRtb) d.toggleRtb(sp);
                            else if (d.state() == Drone.FLYING || d.state() == Drone.LOITER) {
                                d.station = m.pos;
                                d.toggleRtb(sp);
                            }
                        }
                    }
                    case S_HOVER -> {
                        if (e instanceof Drone d) d.release();
                    }
                    case S_UNLINK -> {
                        be.drones.remove(m.id);
                        be.setChanged();
                        if (e instanceof Drone d && m.pos.equals(d.station)) d.station = null;
                    }
                    default -> {
                    }
                }
                StationBlock.sendData(sp, m.pos, be, false);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
