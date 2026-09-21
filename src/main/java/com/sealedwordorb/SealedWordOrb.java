package com.sealedwordorb;

import com.sealedwordorb.item.SealedWordOrbItem;
import com.sealedwordorb.menu.OrbEditorMenu;
import com.sealedwordorb.network.OrbNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(SealedWordOrb.MOD_ID)
public final class SealedWordOrb {
    public static final String MOD_ID = "sealedwordorb";
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final RegistryObject<Item> ORB = ITEMS.register("sealed_word_orb",
            () -> new SealedWordOrbItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<MenuType<OrbEditorMenu>> ORB_EDITOR_MENU = MENUS.register("orb_editor",
            () -> IForgeMenuType.create(OrbEditorMenu::new));
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("sealedwordorb", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.sealedwordorb"))
            .icon(() -> ORB.get().getDefaultInstance())
            .displayItems((parameters, output) -> output.accept(ORB.get()))
            .build());

    public SealedWordOrb(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
        ITEMS.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        OrbNetwork.register();
    }
}
