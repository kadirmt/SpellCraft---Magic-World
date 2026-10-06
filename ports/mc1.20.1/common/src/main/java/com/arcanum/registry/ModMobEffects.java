package com.arcanum.registry;

import com.arcanum.Arcanum;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Özel iksir etkileri. Mana sistemi Minecraft'a yabancı olduğundan (vanilla
 * karşılığı yok) MANA_HASTE kendi etkimiz; EXSTIMULO da büyü gücü/cooldown
 * bonusu için kendi etkimiz. Girding İksiri için ayrı bir etkiye gerek yok —
 * vanilla Direnç (Resistance) + Hız (Speed) etkileri yeniden kullanılıyor.
 */
public final class ModMobEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Arcanum.MODID, Registries.MOB_EFFECT);

    /** Mana yenilenme hızı çarpanı — iksir (seviye I = amplifier 0) ×2; her ek seviye +1×
     *  (amplifier ManaRegen'de okunur; /effect ile yüksek seviye = çok hızlı). */
    public static final RegistrySupplier<MobEffect> MANA_HASTE =
            EFFECTS.register("mana_haste", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xFFD24D) {});

    /** Exstimulo İksiri işareti — büyü gücü ×1.5, cooldown ×0.8 (WandItem/Spells kontrol eder). */
    public static final RegistrySupplier<MobEffect> EXSTIMULO =
            EFFECTS.register("exstimulo", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xFF6B3D) {});

    /**
     * Imperio (Imperius Laneti) işareti — vanilla MobEffect'in kendi başına "hedeflemeyi
     * durdur" davranışı yoktur; bu yalnızca bir BAYRAKTIR. Gerçek davranış (saldırı
     * hedefinin sürekli sıfırlanması) fabric tarafında bir END_SERVER_TICK süpürmesiyle
     * uygulanır (bkz. fabric/ImperiusCurseTicker.java) — Mixin YOK, LumosLight.java'nın
     * kanıtlanmış tick-event desenini izler (Reviewer Ajan: sunucu-thread-only, güvenli).
     */
    public static final RegistrySupplier<MobEffect> IMPERIUS_CURSE =
            EFFECTS.register("imperius_curse", () -> new MobEffect(MobEffectCategory.HARMFUL, 0x8A2BE2) {});

    /**
     * Crucio (Cruciatus Laneti) işareti — IMPERIUS_CURSE gibi yalnızca BAYRAKTIR. Gerçek
     * davranış (kalp-bazlı periyodik hasar + sağ/sol tık kilidi) {@link
     * com.arcanum.spell.CrucioTracker} tick süpürmesinde + istemci girdi kilidinde uygulanır.
     * Kurban bu etki üzerindeyken asa castleyemez (WandItem.use) ve tıklayamaz (MouseHandlerMixin).
     */
    public static final RegistrySupplier<MobEffect> CRUCIO =
            EFFECTS.register("crucio", () -> new MobEffect(MobEffectCategory.HARMFUL, 0xFF2E2E) {});

    private ModMobEffects() {}

    public static void init() {
        EFFECTS.register();
    }

    // NOT (1.20.1 portu): 1.21.1'deki holderOf köprüsü SİLİNDİ — 1.20.1'de
    // MobEffectInstance / hasEffect / getEffect / removeEffect DOĞRUDAN MobEffect
    // alır (javap ile doğrulandı); "Unregistered holder" çökmesi 1.20.1'de olmaz
    // (NBT registry id ile yazar). Tüm çağrı yerleri `ModMobEffects.X.get()`
    // kullanır (bkz. f3-core-contracts.md §3; istenirse com.arcanum.util.EffectHelper).
}
