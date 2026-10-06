package com.arcanum.registry;

import com.arcanum.Arcanum;
import com.arcanum.platform.registry.DeferredRegister;
import com.arcanum.platform.registry.RegistrySupplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.alchemy.Potion;

/**
 * Özel iksirler (vanilla {@code minecraft:potion} item'ı + bu kayıtlı Potion
 * içeriğiyle kullanılır — ayrı bir Item sınıfına gerek yok). Brewing tarifleri
 * loader köprüsünde kaydedilir (Fabric: FabricBrewingRecipeRegistryBuilder; Forge: karşılığı).
 *
 * <p>26.x: {@code Potion} adı ZORUNLU → kayıt yolu verilir (çeviri anahtarı
 * {@code item.minecraft.potion.effect.<ad>} 1.21.1'deki gibi kayıt yolundan türer). Efekt holder'ları
 * fabrika içinde {@code .holder()} ile alınır — MOB_EFFECT iki loader'da da POTION'dan önce kaydedilir.
 */
public final class ModPotions {
    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Arcanum.MODID, Registries.POTION);

    private static final int DURATION = 6000; // 5 dk
    private static final int EXSTIMULO_DURATION = 12000; // 10 dk
    private static final int MANA_HASTE_DURATION = 12000; // 10 dk (10. tur)

    /** Mana Hızlandırma İksiri — 10. tur: mana dolma hızını ×2 (skill değeriyle çarpımlı), 10 dk. */
    public static final RegistrySupplier<Potion> MANA_HASTE_POTION =
            POTIONS.register("mana_haste_potion", () -> new Potion("mana_haste_potion",
                    new MobEffectInstance(ModMobEffects.MANA_HASTE.holder(), MANA_HASTE_DURATION, 0)));

    /** Exstimulo tarifinin 1. ara aşaması (Güç İksiri + Anka tüyü) — henüz etkisiz. */
    public static final RegistrySupplier<Potion> EXSTIMULO_STAGE1 =
            POTIONS.register("exstimulo_stage1", () -> new Potion("exstimulo_stage1"));

    /** Exstimulo tarifinin 2. ara aşaması (+ Gökgürültü Kuşu tüyü) — henüz etkisiz. */
    public static final RegistrySupplier<Potion> EXSTIMULO_STAGE2 =
            POTIONS.register("exstimulo_stage2", () -> new Potion("exstimulo_stage2"));

    /** Exstimulo İksiri (final, + Kar Baykuşu tüyü) — 10. tur: SAF güç ×2.0, 10 dk (mana/cooldown etkisi YOK). */
    public static final RegistrySupplier<Potion> EXSTIMULO_POTION =
            POTIONS.register("exstimulo_potion", () -> new Potion("exstimulo_potion",
                    new MobEffectInstance(ModMobEffects.EXSTIMULO.holder(), EXSTIMULO_DURATION, 0)));

    /** Girding İksiri — troll derisinden, %40 dayanıklılık (Direnç II) + %20 hız (Hız I). */
    public static final RegistrySupplier<Potion> GIRDING_POTION =
            POTIONS.register("girding_potion", () -> new Potion("girding_potion",
                    new MobEffectInstance(MobEffects.RESISTANCE, DURATION, 1),
                    new MobEffectInstance(MobEffects.SPEED, DURATION, 0)));

    private ModPotions() {}

    public static void init() {
        POTIONS.register();
    }

    /**
     * ModMobEffects.holderOf ile aynı gerekçe: Potion.CODEC (NBT'de ItemStack'in PotionContents
     * bileşeni) ve brewing kayıt API'si kanonik Holder.Reference bekler. 26.x shim'inde
     * {@link RegistrySupplier#holder()} zaten bunu döndürür; yardımcı mevcut çağrı noktaları için korunur.
     */
    public static Holder<Potion> holderOf(RegistrySupplier<Potion> potion) {
        return potion.holder();
    }
}
