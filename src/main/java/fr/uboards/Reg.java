package fr.uboards;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class Reg {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Uboards.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Uboards.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Uboards.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Uboards.MODID);

    public static final RegistryObject<Block> BOARD = BLOCKS.register("board", () -> new BoardBlock(false));
    public static final RegistryObject<Block> SCREEN = BLOCKS.register("screen", () -> new BoardBlock(true));

    public static final RegistryObject<Item> BOARD_ITEM = ITEMS.register("board",
            () -> new BoardItem(BOARD.get(), new Item.Properties(), false));
    public static final RegistryObject<Item> SCREEN_ITEM = ITEMS.register("screen",
            () -> new BoardItem(SCREEN.get(), new Item.Properties(), true));
    public static final RegistryObject<Item> REMOTE = ITEMS.register("remote",
            () -> new RemoteItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<BlockEntityType<BoardBE>> BE = BES.register("board",
            () -> BlockEntityType.Builder.of(BoardBE::new, BOARD.get(), SCREEN.get()).build(null));

    private static final int[][] PRESETS = {{1, 1}, {1, 2}, {2, 1}, {2, 2}, {2, 3}, {3, 2}, {3, 3}, {4, 3}, {4, 4},
            {5, 4}, {6, 4}, {5, 8}, {8, 5}, {16, 9}};

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.literal("Tableaux & Écrans"))
                    .icon(() -> new ItemStack(BOARD_ITEM.get()))
                    .displayItems((params, out) -> {
                        for (int[] p : PRESETS) out.accept(make(false, p[0], p[1]));
                        for (int[] p : PRESETS) out.accept(make(true, p[0], p[1]));
                        out.accept(new ItemStack(REMOTE.get()));
                    }).build());

    /** Objet "Tableau" ou "Ecran" de la taille voulue (largeur x hauteur en blocs). */
    public static ItemStack make(boolean screen, int w, int h) {
        ItemStack s = new ItemStack(screen ? SCREEN_ITEM.get() : BOARD_ITEM.get());
        CompoundTag be = new CompoundTag();
        be.putInt("W", BoardBE.clamp(w));
        be.putInt("H", BoardBE.clamp(h));
        s.getOrCreateTag().put("BlockEntityTag", be);
        return s;
    }
}
