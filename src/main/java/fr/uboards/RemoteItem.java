package fr.uboards;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Telecommande : clic droit sur un tableau/ecran pour la relier, puis clic droit en l'air pour ecrire. */
public class RemoteItem extends Item {
    public RemoteItem(Properties props) {
        super(props);
    }

    public static boolean isLinked(ItemStack s) {
        CompoundTag t = s.getTag();
        return t != null && t.contains("Dim") && t.contains("X");
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        BlockPos pos = ctx.getClickedPos();
        if (player == null || !(level.getBlockEntity(pos) instanceof BoardBE be)) {
            return InteractionResult.PASS;
        }
        if (!player.hasPermissions(2)) {
            if (!level.isClientSide) {
                player.sendSystemMessage(Component.literal("Réservé aux OP.").withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            CompoundTag t = ctx.getItemInHand().getOrCreateTag();
            t.putString("Dim", level.dimension().location().toString());
            t.putInt("X", pos.getX());
            t.putInt("Y", pos.getY());
            t.putInt("Z", pos.getZ());
            t.putBoolean("Screen", be.isScreen());
            t.putInt("W", be.getW());
            t.putInt("H", be.getH());
            player.sendSystemMessage(Component.literal("Télécommande reliée à "
                    + (be.isScreen() ? "l'écran " : "le tableau ") + be.getW() + "x" + be.getH()
                    + " (" + pos.getX() + " " + pos.getY() + " " + pos.getZ() + "). Clic droit en l'air pour écrire.")
                    .withStyle(ChatFormatting.GREEN));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.hasPermissions(2)) {
            if (!level.isClientSide) {
                player.sendSystemMessage(Component.literal("Réservé aux OP.").withStyle(ChatFormatting.RED));
            }
            return InteractionResultHolder.fail(stack);
        }
        if (!isLinked(stack)) {
            if (!level.isClientSide) {
                player.sendSystemMessage(Component.literal("Fais d'abord un clic droit sur un tableau ou un écran pour la relier.")
                        .withStyle(ChatFormatting.YELLOW));
            }
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            final boolean sc = stack.getTag().getBoolean("Screen");
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    fr.uboards.client.ClientHooks.openEdit(BlockPos.ZERO, true, sc, ""));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tips, TooltipFlag flag) {
        if (isLinked(stack)) {
            CompoundTag t = stack.getTag();
            tips.add(Component.literal("Reliée : " + (t.getBoolean("Screen") ? "écran " : "tableau ")
                    + t.getInt("W") + "x" + t.getInt("H") + " en " + t.getInt("X") + " " + t.getInt("Y") + " " + t.getInt("Z"))
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tips.add(Component.literal("Clic droit sur un tableau/écran pour la relier").withStyle(ChatFormatting.GRAY));
        }
    }
}
