package com.arcanum.util;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;

/**
 * 1.20.1 efekt/iksir erişim yardımcıları — 1.21.1'deki
 * {@code ModMobEffects.holderOf(...)} / {@code ModPotions.holderOf(...)} köprülerinin yerine.
 *
 * 1.20.1'de {@code MobEffectInstance}, {@code LivingEntity.hasEffect/getEffect/removeEffect}
 * ve {@code Potion} ctor'u DOĞRUDAN {@link MobEffect} alır (javap ile doğrulandı) —
 * Holder köprüsüne gerek yok; "Unregistered holder" çökmesi 1.20.1'de konu dışıdır
 * (MobEffectInstance NBT'si registry id ile yazar).
 *
 * Tercih edilen kalıp doğrudan {@code SUPPLIER.get()} çağrısıdır:
 * <pre>
 *   new MobEffectInstance(ModMobEffects.MANA_HASTE.get(), DURATION, 0);
 *   player.hasEffect(ModMobEffects.CRUCIO.get());
 * </pre>
 * Bu sınıf, holderOf çağrı şekliyle birebir eşlenen mekanik bul-değiştir isteyen
 * çağrı yerleri için tek kanonik erişim noktası sunar
 * ({@code holderOf(X)} → {@code EffectHelper.effectOf(X)} / {@code potionOf(X)}).
 */
public final class EffectHelper {

    private EffectHelper() {}

    /** 1.21.1 {@code ModMobEffects.holderOf(effect)} karşılığı — 1.20.1'de düz MobEffect. */
    public static MobEffect effectOf(RegistrySupplier<MobEffect> effect) {
        return effect.get();
    }

    /** 1.21.1 {@code ModPotions.holderOf(potion)} karşılığı — 1.20.1'de düz Potion. */
    public static Potion potionOf(RegistrySupplier<Potion> potion) {
        return potion.get();
    }

    /** Kısayol: {@code new MobEffectInstance(effect.get(), duration, amplifier)}. */
    public static MobEffectInstance instance(RegistrySupplier<MobEffect> effect, int duration, int amplifier) {
        return new MobEffectInstance(effect.get(), duration, amplifier);
    }
}
