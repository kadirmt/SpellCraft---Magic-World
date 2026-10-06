package com.arcanum.registry;

import com.arcanum.Arcanum;
import com.arcanum.menu.SpellTableMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Menü (container GUI) kayıtları — Forge DeferredRegister ile.
 * Architectury'nin {@code MenuRegistry.of(SpellTableMenu::new)} çağrısının
 * Forge karşılığı {@link IForgeMenuType#create}: ekstra FriendlyByteBuf verisi
 * kullanılmadığından istemci fabrikası buf'u yok sayıp aynı iki-argümanlı
 * constructor'a delege eder (javap ile doğrulandı: IForgeMenuType.create(IContainerFactory)).
 */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Arcanum.MODID);

    /** Büyü Masası GUI'si — kitap + reagent slotlu öğrenme arayüzü. */
    public static final RegistryObject<MenuType<SpellTableMenu>> SPELL_TABLE_MENU =
            MENUS.register("spell_table", () -> IForgeMenuType.create(
                    (windowId, inv, data) -> new SpellTableMenu(windowId, inv)));

    private ModMenus() {}

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
