package fr.upaint;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PaintToolItem extends Item {
    public enum Kind { BRUSH, ROLLER, BUCKET, ERASER, PIPETTE }

    public static final String DEF_TEX = "minecraft:block/white_concrete";
    private final Kind kind;

    public PaintToolItem(Kind kind, Properties props) {
        super(props);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public static String texOf(ItemStack s) {
        return s.hasTag() && s.getTag().contains("Tex") ? s.getTag().getString("Tex") : DEF_TEX;
    }

    public static int colorOf(ItemStack s) {
        return s.hasTag() && s.getTag().contains("Color") ? s.getTag().getInt("Color") & 0xFFFFFF : 0xFFFFFF;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide && (kind == Kind.BRUSH || kind == Kind.ROLLER || kind == Kind.BUCKET)) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    fr.upaint.client.ClientHooks.openBrush(texOf(stack), colorOf(stack)));
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel sl)) return InteractionResult.PASS;
        BlockPos pos = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();
        if (!player.mayUseItemAt(pos, face, stack)) return InteractionResult.FAIL;

        PaintData.Paint paint = new PaintData.Paint(texOf(stack), colorOf(stack));
        switch (kind) {
            case BRUSH -> PaintData.set(sl, pos, face, paint);
            case ROLLER -> {
                for (BlockPos p : plane(sl, pos, face, 1, false, 9)) {
                    PaintData.set(sl, p, face, paint);
                }
            }
            case BUCKET -> {
                for (BlockPos p : plane(sl, pos, face, 64, true, 256)) {
                    PaintData.set(sl, p, face, paint);
                }
            }
            case ERASER -> {
                if (player.isShiftKeyDown()) {
                    PaintData.clearBlock(sl, pos);
                } else {
                    PaintData.remove(sl, pos, face);
                }
            }
            case PIPETTE -> {
                PaintData.Paint got = PaintData.get(sl).get(pos, face.get3DDataValue());
                if (got == null) {
                    player.displayClientMessage(Component.literal("Rien de peint ici.").withStyle(ChatFormatting.GRAY), true);
                    return InteractionResult.CONSUME;
                }
                boolean done = false;
                for (InteractionHand h : InteractionHand.values()) {
                    ItemStack o = player.getItemInHand(h);
                    if (o.getItem() instanceof PaintToolItem t && t.kind != Kind.PIPETTE && t.kind != Kind.ERASER) {
                        o.getOrCreateTag().putString("Tex", got.tex());
                        o.getOrCreateTag().putInt("Color", got.color());
                        done = true;
                    }
                }
                player.displayClientMessage(Component.literal(done
                        ? "Peinture copiée !" : "Tiens un pinceau/rouleau/seau dans l'autre main.")
                        .withStyle(done ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);
            }
        }
        return InteractionResult.CONSUME;
    }

    /** Blocs coplanaires, face exposee. radius carre autour (rouleau) ou flood-fill meme bloc (seau). */
    private static List<BlockPos> plane(ServerLevel level, BlockPos start, Direction face, int radius, boolean flood, int limit) {
        List<BlockPos> out = new java.util.ArrayList<>();
        BlockState ref = level.getBlockState(start);
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start);
        Direction[] dirs = Direction.values();
        while (!q.isEmpty() && out.size() < limit) {
            BlockPos p = q.poll();
            BlockState st = level.getBlockState(p);
            if (st.isAir()) continue;
            if (flood && st.getBlock() != ref.getBlock()) continue;
            if (level.getBlockState(p.relative(face)).canOcclude()) continue;
            out.add(p);
            for (Direction d : dirs) {
                if (d.getAxis() == face.getAxis()) continue;
                BlockPos n = p.relative(d);
                if (seen.contains(n)) continue;
                if (!flood) {
                    BlockPos delta = n.subtract(start);
                    if (Math.abs(delta.getX()) > radius || Math.abs(delta.getY()) > radius || Math.abs(delta.getZ()) > radius) continue;
                }
                seen.add(n);
                q.add(n);
            }
        }
        return out;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        String t = switch (kind) {
            case BRUSH -> "Clic droit sur une face : peint la face. Clic dans le vide : choisir texture/couleur.";
            case ROLLER -> "Peint 3x3 faces d'un coup. Clic dans le vide : choisir texture/couleur.";
            case BUCKET -> "Remplit toute la surface du même bloc. Clic dans le vide : choisir.";
            case ERASER -> "Efface la face (Maj : tout le bloc).";
            case PIPETTE -> "Copie la peinture d'une face dans l'outil de l'autre main.";
        };
        tip.add(Component.literal(t).withStyle(ChatFormatting.GRAY));
        if (kind != Kind.ERASER && kind != Kind.PIPETTE) {
            tip.add(Component.literal(texOf(s) + String.format("  #%06X", colorOf(s))).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
