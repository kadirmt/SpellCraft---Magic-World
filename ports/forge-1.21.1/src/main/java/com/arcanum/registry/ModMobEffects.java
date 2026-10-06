package com.arcanum.registry;

import com.arcanum.Arcanum;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Özel iksir etkileri. Mana sistemi Minecraft'a yabancı olduğundan (vanilla
 * karşılığı yok) MANA_HASTE kendi etkimiz; EXSTIMULO da büyü gücü/cooldown
 * bonusu için kendi etkimiz. Girding İksiri için ayrı bir etkiye gerek yok —
 * vanilla Direnç (Resistance) + Hız (Speed) etkileri yeniden kullanılıyor.
 */
public final class ModMobEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Arcanum.MODID);

    /** Mana yenilenme hızı çarpanı — iksir (seviye I = amplifier 0) ×2; her ek seviye +1×
     *  (amplifier ManaRegen'de okunur; /effect ile yüksek seviye = çok hızlı). */
    public static final RegistryObject<MobEffect> MANA_HASTE =
            EFFECTS.register("mana_haste", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xFFD24D) {});

    /** Exstimulo İksiri işareti — büyü gücü ×1.5, cooldown ×0.8 (WandItem/Spells kontrol eder). */
    public static final RegistryObject<MobEffect> EXSTIMULO =
            EFFECTS.register("exstimulo", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xFF6B3D) {});

    /**
     * Imperio (Imperius Laneti) işareti — vanilla MobEffect'in kendi başına "hedeflemeyi
     * durdur" davranışı yoktur; bu yalnızca bir BAYRAKTIR. Gerçek davranış (saldırı
     * hedefinin sürekli sıfırlanması) loader tarafında bir sunucu tick süpürmesiyle
     * uygulanır (bkz. ImperiusCurseTracker) — Mixin YOK, LumosLight.java'nın
     * kanıtlanmış tick-event desenini izler (Reviewer Ajan: sunucu-thread-only, güvenli).
     */
    public static final RegistryObject<MobEffect> IMPERIUS_CURSE =
            EFFECTS.register("imperius_curse", () -> new MobEffect(MobEffectCategory.HARMFUL, 0x8A2BE2) {});

    /**
     * Crucio (Cruciatus Laneti) işareti — IMPERIUS_CURSE gibi yalnızca BAYRAKTIR. Gerçek
     * davranış (kalp-bazlı periyodik hasar + sağ/sol tık kilidi) {@link
     * com.arcanum.spell.CrucioTracker} tick süpürmesinde + istemci girdi kilidinde uygulanır.
     * Kurban bu etki üzerindeyken asa castleyemez (WandItem.use) ve tıklayamaz (MouseHandlerMixin).
     */
    public static final RegistryObject<MobEffect> CRUCIO =
            EFFECTS.register("crucio", () -> new MobEffect(MobEffectCategory.HARMFUL, 0xFF2E2E) {});

    private ModMobEffects() {}

    public static void register(IEventBus bus) {
        EFFECTS.register(bus);
    }

    /**
     * Fabric halindeki gerekçenin Forge karşılığı: MobEffectInstance'ın NBT
     * kaydı ve LivingEntity.activeEffects haritası (Holder.equals() override
     * edilmediği için saf referans eşitliğiyle anahtarlanır) registry'nin
     * KANONİK Holder.Reference'ını bekler — aksi halde "Unregistered holder"
     * ile sunucu çöker. RegistryObject.getHolder() da Forge'da kanonik
     * referansı verir ama Optional sarmalı; davranış birebir korunması için
     * kökle aynı wrapAsHolder yolu kullanıldı. Hem iksir kaydında (ModPotions)
     * hem sorgularken (WandItem, Spells) HER ZAMAN bu metottan geçen Holder
     * kullanılmalı — çağrı anında kayıt tamamlanmış olmalı (RegisterEvent sonrası).
     */
    public static Holder<MobEffect> holderOf(RegistryObject<MobEffect> effect) {
        return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect.get());
    }
}
