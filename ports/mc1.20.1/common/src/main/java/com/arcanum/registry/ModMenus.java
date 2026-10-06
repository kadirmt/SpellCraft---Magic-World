package com.arcanum.registry;

import com.arcanum.Arcanum;
import com.arcanum.menu.SpellTableMenu;
import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

/**
 * Menü (container GUI) kayıtları. Architectury DeferredRegister ile loader-bağımsız.
 * MenuType'ın kurucusu vanilla'da private olduğundan doğrudan {@code new MenuType<>(...)}
 * çağrılamaz — Architectury'nin {@link MenuRegistry#of} yardımcı fabrikası kullanılır
 * (bkz. dev.architectury.registry.menu.MenuRegistry, javap ile doğrulandı).
 */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Arcanum.MODID, Registries.MENU);

    /** Büyü Masası GUI'si — kitap + reagent slotlu öğrenme arayüzü. */
    public static final RegistrySupplier<MenuType<SpellTableMenu>> SPELL_TABLE_MENU =
            MENUS.register("spell_table", () -> MenuRegistry.of(SpellTableMenu::new));

    private ModMenus() {}

    public static void init() {
        MENUS.register();
    }
}
