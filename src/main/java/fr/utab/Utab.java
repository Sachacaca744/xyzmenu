package fr.utab;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Onglet commun "utab:main". Les autres mods y ajoutent leurs objets avec BuildCreativeModeTabContentsEvent
 * (tab key = utab:main). Cet onglet ne contient rien tout seul.
 */
@Mod(Utab.MODID)
public class Utab {
    public static final String MODID = "utab";
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.literal("Cailloux"))
                    .icon(() -> new ItemStack(Items.FLINT))
                    .build());

    public Utab() {
        TABS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
