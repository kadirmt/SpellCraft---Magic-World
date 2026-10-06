package com.arcanum.registry;

import com.arcanum.Arcanum;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
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

    /**
     * Architectury'nin RegistrySupplier'ı Holder arayüzünü uygular ama vanilla'nın
     * gerçek registry Holder.Reference'ı DEĞİLDİR — ne MobEffectInstance'ın NBT
     * kaydı (Holder.Reference bekler, aksi halde "Unregistered holder" ile sunucu
     * çöker) ne de LivingEntity.activeEffects haritası (Holder.equals() override
     * edilmediği için saf referans eşitliğiyle anahtarlanır) bununla çalışır.
     * Bu yüzden hem iksir kaydında (ModPotions) hem de sorgularken (WandItem,
     * Spells) HER ZAMAN bu metottan geçen kanonik Holder kullanılmalı — çağrı
     * anında (init() çoktan çalışmış olmalı) registry'nin kendi byValue
     * haritasından gerçek referansı çeker.
     */
    public static Holder<MobEffect> holderOf(RegistrySupplier<MobEffect> effect) {
        return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect.get());
    }
}
