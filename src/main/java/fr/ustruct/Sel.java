package fr.ustruct;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Selection (2 coins) de chaque joueur. */
public class Sel {
    public BlockPos a, b;
    public String dim = "";

    private static final Map<UUID, Sel> MAP = new HashMap<>();

    public static Sel of(ServerPlayer p) {
        return MAP.computeIfAbsent(p.getUUID(), k -> new Sel());
    }

    public boolean complete() {
        return a != null && b != null;
    }

    public BlockPos min() {
        return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    public int[] size() {
        return new int[]{Math.abs(a.getX() - b.getX()) + 1, Math.abs(a.getY() - b.getY()) + 1, Math.abs(a.getZ() - b.getZ()) + 1};
    }

    public static void set(ServerPlayer p, boolean first, BlockPos pos) {
        Sel s = of(p);
        String d = p.level().dimension().location().toString();
        if (!d.equals(s.dim)) {
            s.a = null;
            s.b = null;
            s.dim = d;
        }
        if (first) s.a = pos.immutable();
        else s.b = pos.immutable();
        sync(p);
        if (s.complete()) {
            int[] z = s.size();
            boolean ok = z[0] <= Store.MAX && z[1] <= Store.MAX && z[2] <= Store.MAX;
            p.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Zone : " + z[0] + " x " + z[1] + " x " + z[2] + (ok ? "" : "  (TROP GRAND, max " + Store.MAX + ")"))
                    .withStyle(ok ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED), true);
        } else {
            p.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    (first ? "Coin 1" : "Coin 2") + " : " + pos.getX() + " " + pos.getY() + " " + pos.getZ())
                    .withStyle(net.minecraft.ChatFormatting.AQUA), true);
        }
    }

    public static void sync(ServerPlayer p) {
        Sel s = of(p);
        Net.CH.send(PacketDistributor.PLAYER.with(() -> p), new Net.SelMsg(s.a, s.b, s.dim));
    }
}
