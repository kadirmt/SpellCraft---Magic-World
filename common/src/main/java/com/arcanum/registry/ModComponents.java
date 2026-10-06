package com.arcanum.registry;

import com.arcanum.Arcanum;
import com.mojang.serialization.Codec;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;

/**
 * Özel DataComponent'ler (ItemStack ile otomatik kaydedilir/senkronlanır).
 */
public final class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Arcanum.MODID, Registries.DATA_COMPONENT_TYPE);

    /** Asada seçili büyünün ModSpells.SPELLS içindeki index'i. */
    public static final RegistrySupplier<DataComponentType<Integer>> SELECTED_SPELL =
            COMPONENTS.register("selected_spell", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.INT)
                    .build());

    // 10. tur: MANA component KALDIRILDI — mana artık asada değil, oyuncuda
    // (ArcanumPlayerData). Eski dünyalardaki asa MANA komponenti sessizce yok sayılır.

    /** Lumos açık mı — açıksa asa elde tutulurken çevreye ışık yayar. */
    public static final RegistrySupplier<DataComponentType<Boolean>> LUMOS_ACTIVE =
            COMPONENTS.register("lumos_active", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    /** Büyü kitabının öğrettiği büyü id'si (ModSpells id'lerinden biri). */
    public static final RegistrySupplier<DataComponentType<String>> SPELL_ID =
            COMPONENTS.register("spell_id", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    private ModComponents() {}

    public static void init() {
        COMPONENTS.register();
    }
}
