package com.arcanum.registry;

import com.arcanum.Arcanum;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.alchemy.Potion;

/**
 * Özel iksirler (vanilla {@code minecraft:potion} item'ı + bu kayıtlı Potion
 * içeriğiyle kullanılır — ayrı bir Item sınıfına gerek yok). Brewing tarifleri
 * ArcanumFabric.java'da FabricBrewingRecipeRegistryBuilder ile kaydedilir.
 */
public final class ModPotions {
    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Arcanum.MODID, Registries.POTION);

    private static final int DURATION = 6000; // 5 dk
    private static final int EXSTIMULO_DURATION = 12000; // 10 dk
    private static final int MANA_HASTE_DURATION = 12000; // 10 dk (10. tur)

    /** Mana Hızlandırma İksiri — 10. tur: mana dolma hızını ×2 (skill değeriyle çarpımlı), 10 dk. */
    public static final RegistrySupplier<Potion> MANA_HASTE_POTION =
            POTIONS.register("mana_haste_potion", () -> new Potion(
                    new MobEffectInstance(ModMobEffects.holderOf(ModMobEffects.MANA_HASTE), MANA_HASTE_DURATION, 0)));

    /** Exstimulo tarifinin 1. ara aşaması (Güç İksiri + Anka tüyü) — henüz etkisiz. */
    public static final RegistrySupplier<Potion> EXSTIMULO_STAGE1 =
            POTIONS.register("exstimulo_stage1", () -> new Potion());

    /** Exstimulo tarifinin 2. ara aşaması (+ Gökgürültü Kuşu tüyü) — henüz etkisiz. */
    public static final RegistrySupplier<Potion> EXSTIMULO_STAGE2 =
            POTIONS.register("exstimulo_stage2", () -> new Potion());

    /** Exstimulo İksiri (final, + Kar Baykuşu tüyü) — 10. tur: SAF güç ×2.0, 10 dk (mana/cooldown etkisi YOK). */
    public static final RegistrySupplier<Potion> EXSTIMULO_POTION =
            POTIONS.register("exstimulo_potion", () -> new Potion(
                    new MobEffectInstance(ModMobEffects.holderOf(ModMobEffects.EXSTIMULO), EXSTIMULO_DURATION, 0)));

    /** Girding İksiri — troll derisinden, %40 dayanıklılık (Direnç II) + %20 hız (Hız I). */
    public static final RegistrySupplier<Potion> GIRDING_POTION =
            POTIONS.register("girding_potion", () -> new Potion(
                    new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, DURATION, 1),
                    new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION, 0)));

    private ModPotions() {}

    public static void init() {
        POTIONS.register();
    }

    /**
     * ModMobEffects.holderOf ile aynı gerekçe: Architectury'nin RegistrySupplier'ı
     * Potion.CODEC'in (NBT'de ItemStack'in PotionContents bileşeni) ve brewing
     * kayıt API'sinin beklediği kanonik Holder.Reference DEĞİLDİR — bir iksiri
     * envantere/sandığa koyup dünya kaydedilince "Unregistered holder" ile
     * çökebilirdi. Her zaman bu metottan geçen Holder kullanılmalı.
     */
    public static Holder<Potion> holderOf(RegistrySupplier<Potion> potion) {
        return BuiltInRegistries.POTION.wrapAsHolder(potion.get());
    }
}
