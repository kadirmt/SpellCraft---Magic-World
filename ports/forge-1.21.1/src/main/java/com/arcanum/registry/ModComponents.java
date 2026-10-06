package com.arcanum.registry;

import com.arcanum.Arcanum;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Özel DataComponent'ler (ItemStack ile otomatik kaydedilir/senkronlanır).
 * Forge DeferredRegister, Registries.DATA_COMPONENT_TYPE key'i ile vanilla
 * registry'ye kaydeder (javap ile doğrulandı: create(ResourceKey, String)).
 */
public final class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Arcanum.MODID);

    /** Asada seçili büyünün ModSpells.SPELLS içindeki index'i. */
    public static final RegistryObject<DataComponentType<Integer>> SELECTED_SPELL =
            COMPONENTS.register("selected_spell", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.INT)
                    .build());

    // 10. tur: MANA component KALDIRILDI — mana artık asada değil, oyuncuda
    // (ArcanumPlayerData). Eski dünyalardaki asa MANA komponenti sessizce yok sayılır.

    /** Lumos açık mı — açıksa asa elde tutulurken çevreye ışık yayar. */
    public static final RegistryObject<DataComponentType<Boolean>> LUMOS_ACTIVE =
            COMPONENTS.register("lumos_active", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    /** Büyü kitabının öğrettiği büyü id'si (ModSpells id'lerinden biri). */
    public static final RegistryObject<DataComponentType<String>> SPELL_ID =
            COMPONENTS.register("spell_id", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    private ModComponents() {}

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }
}
