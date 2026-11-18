package fr.uking;

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
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Uking.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static final int A_RECRUIT = 1, A_UPGRADE = 2, A_DEPOSIT = 3, A_WITHDRAW = 4, A_WAR = 5, A_PEACE = 6, A_CONQUER = 7, A_REFRESH = 8;

    public static void register() {
        CH.registerMessage(0, CmdMsg.class, CmdMsg::encode, CmdMsg::decode, CmdMsg::handle);
        CH.registerMessage(1, StateMsg.class, StateMsg::encode, StateMsg::decode, StateMsg::handle);
    }

    /** Client -> serveur : action depuis l'ecran du fief. */
    public record CmdMsg(int action, int fief, int arg) {
        static void encode(CmdMsg m, FriendlyByteBuf b) { b.writeVarInt(m.action); b.writeVarInt(m.fief); b.writeVarInt(m.arg); }
        static CmdMsg decode(FriendlyByteBuf b) { return new CmdMsg(b.readVarInt(), b.readVarInt(), b.readVarInt()); }

        static void handle(CmdMsg m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sp == null) return;
                Fiefs fs = Fiefs.get(sp.level());
                Fief f = fs.byId(m.fief);
                if (f == null) return;
                String err = null, ok = "";
                if (sp.blockPosition().distSqr(f.pos) > 70 * 70 && m.action != A_REFRESH) err = "Trop loin de la pierre de fief.";
                else switch (m.action) {
                    case A_RECRUIT -> { err = fs.recruit(sp, f, m.arg); ok = "Recrue prête !"; }
                    case A_UPGRADE -> { err = fs.upgrade(sp, f); ok = "Fief amélioré !"; }
                    case A_DEPOSIT -> {
                        if (!sp.getUUID().equals(f.owner)) err = "Ce n'est pas votre fief.";
                        else { int n = fs.deposit(sp, f); ok = n + " or déposés."; if (n == 0) err = "Pas de lingot d'or dans l'inventaire."; }
                    }
                    case A_WITHDRAW -> {
                        if (!sp.getUUID().equals(f.owner)) err = "Ce n'est pas votre fief.";
                        else { int n = fs.withdraw(sp, f, m.arg); ok = n + " or retirés."; if (n == 0) err = "Rien à retirer."; }
                    }
                    case A_WAR -> { err = fs.commandWar(sp, f); ok = "Guerre déclarée !"; }
                    case A_PEACE -> { err = fs.commandPeace(sp, f); ok = "Paix signée (tribut payé)."; }
                    case A_CONQUER -> { err = fs.conquer(sp, f); ok = "Fief conquis !"; }
                    default -> { }
                }
                Fief view = fs.byId(m.fief);
                if (view != null) CH.reply(fs.state(sp, view, err != null ? "!" + err : ok), ctx.get());
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record Nb(int id, String name, int level, int color, int dx, int dz, boolean war, boolean mine, int conq, boolean npc) {
        void encode(FriendlyByteBuf b) {
            b.writeVarInt(id); b.writeUtf(name); b.writeVarInt(level); b.writeVarInt(color); b.writeInt(dx); b.writeInt(dz);
            b.writeBoolean(war); b.writeBoolean(mine); b.writeVarInt(conq); b.writeBoolean(npc);
        }
        static Nb decode(FriendlyByteBuf b) {
            return new Nb(b.readVarInt(), b.readUtf(), b.readVarInt(), b.readVarInt(), b.readInt(), b.readInt(), b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readBoolean());
        }
    }

    /** Serveur -> client : contenu de l'ecran du fief. */
    public record StateMsg(int id, String name, int level, int gold, int invGold, int army, int cap, int peasants, int income, boolean own,
                           int[] counts, List<Nb> nb, String msg, int catapults, boolean atWar) {
        static void encode(StateMsg m, FriendlyByteBuf b) {
            b.writeVarInt(m.id); b.writeUtf(m.name); b.writeVarInt(m.level); b.writeVarInt(m.gold); b.writeVarInt(m.invGold);
            b.writeVarInt(m.army); b.writeVarInt(m.cap); b.writeVarInt(m.peasants); b.writeVarInt(m.income); b.writeBoolean(m.own);
            b.writeVarIntArray(m.counts);
            b.writeVarInt(m.nb.size());
            for (Nb n : m.nb) n.encode(b);
            b.writeUtf(m.msg); b.writeVarInt(m.catapults); b.writeBoolean(m.atWar);
        }

        static StateMsg decode(FriendlyByteBuf b) {
            int id = b.readVarInt(); String name = b.readUtf(); int level = b.readVarInt(), gold = b.readVarInt(), inv = b.readVarInt();
            int army = b.readVarInt(), cap = b.readVarInt(), peas = b.readVarInt(), inc = b.readVarInt();
            boolean own = b.readBoolean();
            int[] counts = b.readVarIntArray();
            int n = b.readVarInt();
            List<Nb> l = new ArrayList<>();
            for (int i = 0; i < n; i++) l.add(Nb.decode(b));
            return new StateMsg(id, name, level, gold, inv, army, cap, peas, inc, own, counts, l, b.readUtf(), b.readVarInt(), b.readBoolean());
        }

        static void handle(StateMsg m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.uking.client.ClientHooks.state(m)));
            ctx.get().setPacketHandled(true);
        }
    }
}
