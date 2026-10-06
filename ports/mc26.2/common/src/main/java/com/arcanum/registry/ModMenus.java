package com.arcanum.registry;

import com.arcanum.Arcanum;
import com.arcanum.menu.SpellTableMenu;
import com.arcanum.platform.Platform;
import com.arcanum.platform.registry.DeferredRegister;
import com.arcanum.platform.registry.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

/**
 * Menü (container GUI) kayıtları. Ortak {@code platform.registry} shim'i ile loader-bağımsız.
 * MenuType'ın kurucusu vanilla'da private (ve {@code MenuType$MenuSupplier} paket-özel) olduğundan
 * doğrudan {@code new MenuType<>(...)} çağrılamaz — Platform SPI'nin
 * {@link com.arcanum.platform.PlatformBackend#createMenuType} fabrikası kullanılır
 * (FeatureFlags.VANILLA_SET; eski Architectury {@code MenuRegistry.of} ile aynı).
 */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Arcanum.MODID, Registries.MENU);

    /** Büyü Masası GUI'si — kitap + reagent slotlu öğrenme arayüzü. */
    public static final RegistrySupplier<MenuType<SpellTableMenu>> SPELL_TABLE_MENU =
            MENUS.register("spell_table", () -> Platform.get().createMenuType(SpellTableMenu::new));

    private ModMenus() {}

    public static void init() {
        MENUS.register();
    }
}
