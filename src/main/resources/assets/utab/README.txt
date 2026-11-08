Onglet creatif commun "Cailloux" (utab:main).

Pour ranger un objet dans cet onglet depuis un autre mod (Forge 1.20.1) :

  @SubscribeEvent
  public static void onTab(BuildCreativeModeTabContentsEvent event) {
      if (event.getTabKey().equals(ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation("utab", "main")))) {
          event.accept(new ItemStack(MON_ITEM.get()));
      }
  }

(classe annotee @Mod.EventBusSubscriber(bus = MOD)). Sans ce mod installe, l'evenement ne se declenche pas et rien ne casse.
Onglet creatif commun "Cailloux" (utab:main).

Pour ranger un objet dans cet onglet depuis un autre mod (Forge 1.20.1) :

  @SubscribeEvent
  public static void onTab(BuildCreativeModeTabContentsEvent event) {
      if (event.getTabKey().equals(ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation("utab", "main")))) {
          event.accept(new ItemStack(MON_ITEM.get()));
      }
  }

(classe annotee @Mod.EventBusSubscriber(bus = MOD)). Sans ce mod installe, l'evenement ne se declenche pas et rien ne casse.
Onglet creatif commun "Cailloux" (utab:main).

Pour ranger un objet dans cet onglet depuis un autre mod (Forge 1.20.1) :

  @SubscribeEvent
  public static void onTab(BuildCreativeModeTabContentsEvent event) {
      if (event.getTabKey().equals(ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation("utab", "main")))) {
          event.accept(new ItemStack(MON_ITEM.get()));
      }
  }

(classe annotee @Mod.EventBusSubscriber(bus = MOD)). Sans ce mod installe, l'evenement ne se declenche pas et rien ne casse.
