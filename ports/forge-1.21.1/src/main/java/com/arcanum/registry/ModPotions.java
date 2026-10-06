package com.arcanum.registry;

import com.arcanum.Arcanum;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Özel iksirler (vanilla {@code minecraft:potion} item'ı + bu kayıtlı Potion
 * içeriğiyle kullanılır — ayrı bir Item sınıfına gerek yok). Brewing tarifleri
 * loader katmanında (ArcanumForge, BrewingRecipeRegistry/potion mix) kaydedilir.
 *
 * <p>Supplier gövdesindeki {@code ModMobEffects.holderOf(...)} güvenlidir:
 * vanilla kayıt sırasında MOB_EFFECT, POTION'dan önce doldurulur (Forge da
 * BLOCK/ITEM istisnası dışında vanilla sırayı izler).
 */
public final class ModPotions {
    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, Arcanum.MODID);

    private static final int DURATION = 6000; // 5 dk
    private static final int EXSTIMULO_DURATION = 12000; // 10 dk
    private static final int MANA_HASTE_DURATION = 12000; // 10 dk (10. tur)

    /** Mana Hızlandırma İksiri — 10. tur: mana dolma hızını ×2 (skill değeriyle çarpımlı), 10 dk. */
    public static final RegistryObject<Potion> MANA_HASTE_POTION =
            POTIONS.register("mana_haste_potion", () -> new Potion(
                    new MobEffectInstance(ModMobEffects.holderOf(ModMobEffects.MANA_HASTE), MANA_HASTE_DURATION, 0)));

    /** Exstimulo tarifinin 1. ara aşaması (Güç İksiri + Anka tüyü) — henüz etkisiz. */
    public static final RegistryObject<Potion> EXSTIMULO_STAGE1 =
            POTIONS.register("exstimulo_stage1", () -> new Potion());

    /** Exstimulo tarifinin 2. ara aşaması (+ Gökgürültü Kuşu tüyü) — henüz etkisiz. */
    public static final RegistryObject<Potion> EXSTIMULO_STAGE2 =
            POTIONS.register("exstimulo_stage2", () -> new Potion());

    /** Exstimulo İksiri (final, + Kar Baykuşu tüyü) — 10. tur: SAF güç ×2.0, 10 dk (mana/cooldown etkisi YOK). */
    public static final RegistryObject<Potion> EXSTIMULO_POTION =
            POTIONS.register("exstimulo_potion", () -> new Potion(
                    new MobEffectInstance(ModMobEffects.holderOf(ModMobEffects.EXSTIMULO), EXSTIMULO_DURATION, 0)));

    /** Girding İksiri — troll derisinden, %40 dayanıklılık (Direnç II) + %20 hız (Hız I). */
    public static final RegistryObject<Potion> GIRDING_POTION =
            POTIONS.register("girding_potion", () -> new Potion(
                    new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, DURATION, 1),
                    new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION, 0)));

    private ModPotions() {}

    public static void register(IEventBus bus) {
        POTIONS.register(bus);
    }

    /**
     * ModMobEffects.holderOf ile aynı gerekçe: Potion.CODEC'in (NBT'de
     * ItemStack'in PotionContents bileşeni) ve brewing kayıt API'sinin
     * beklediği kanonik Holder.Reference gerekir — bir iksiri envantere/sandığa
     * koyup dünya kaydedilince "Unregistered holder" ile çökmemek için her
     * zaman bu metottan geçen Holder kullanılmalı.
     */
    public static Holder<Potion> holderOf(RegistryObject<Potion> potion) {
        return BuiltInRegistries.POTION.wrapAsHolder(potion.get());
    }
}
