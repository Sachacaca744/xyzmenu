package fr.ustruct.client;

import net.minecraft.core.BlockPos;

public class ClientSel {
    public static BlockPos a, b;
    public static String dim = "";

    public static void set(BlockPos pa, BlockPos pb, String d) {
        a = pa;
        b = pb;
        dim = d;
    }

    public static String sizeText() {
        if (a == null || b == null) return "Aucune zone (baguette : clic gauche = coin 1, clic droit = coin 2)";
        return "Zone : " + (Math.abs(a.getX() - b.getX()) + 1) + " x " + (Math.abs(a.getY() - b.getY()) + 1) + " x " + (Math.abs(a.getZ() - b.getZ()) + 1);
    }
}
