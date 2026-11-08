package fr.utaxi;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class Reg {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Utaxi.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Utaxi.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Utaxi.MODID);

    public static final RegistryObject<Block> BOOTH = BLOCKS.register("taxi_booth", BoothBlock::new);
    public static final RegistryObject<Item> BOOTH_ITEM = ITEMS.register("taxi_booth",
            () -> new BlockItem(BOOTH.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<BlockEntityType<TaxiBE>> BE = BES.register("taxi_booth",
            () -> BlockEntityType.Builder.of(TaxiBE::new, BOOTH.get()).build(null));
}
