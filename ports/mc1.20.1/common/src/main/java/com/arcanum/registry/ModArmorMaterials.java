package com.arcanum.registry;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Özel zırh malzemeleri.
 *
 * <p>1.20.1 portu: 1.21.1'deki {@code Registries.ARMOR_MATERIAL} kaydı 1.20.1'de
 * YOKTUR — {@link ArmorMaterial} bir arayüzdür ve doğrudan enum ile uygulanır
 * (bkz. research-api-diff-1.20.1.md §5). {@code ArmorItem.Type.BODY} girdisi ve
 * {@code ArmorMaterial.Layer} listesi de 1.20.1'de yoktur; zırh dokusu
 * {@link #getName()} üzerinden {@code textures/models/armor/<name>_layer_1.png}
 * kalıbıyla çözülür. Tüm denge değerleri (0 zırh, enchantment 9, 0 toughness/kb)
 * 1.21.1 ile birebir aynıdır.
 */
public enum ModArmorMaterials implements ArmorMaterial {

    /** Görünmezlik Pelerini — 0 zırh puanı, sadece efekt taşıyıcısı (kaynak: HogCraft). */
    CLOAK_OF_INVISIBILITY;

    private ModArmorMaterials() {}

    /**
     * 0 = hasar almaz (1.21.1'de de {@code Item.Properties().durability()} verilmediği
     * için pelerin kırılmazdı — davranış paritesi korunur; {@code defaultDurability}
     * yalnızca 0'dan büyük değerleri uygular).
     */
    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return 0;
    }

    /** 1.21.1'deki Map girdileriyle aynı: her slot için 0 zırh puanı. */
    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return 0;
    }

    @Override
    public int getEnchantmentValue() {
        return 9;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_GENERIC;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.EMPTY;
    }

    /**
     * Zırh katmanı doku yolu bu addan türetilir (1.21.1'deki
     * {@code ArmorMaterial.Layer(arcanum:cloak_of_invisibility)} karşılığı).
     * NOT: Pelerin giyildiğinde zırh render katmanı komple iptal edildiğinden
     * (HumanoidArmorLayerMixin — tam görünmezlik) bu doku pratikte hiç çözülmez;
     * 1.21.1'de de aynı şekilde hiç çizilmiyordu.
     */
    @Override
    public String getName() {
        return "arcanum:cloak_of_invisibility";
    }

    @Override
    public float getToughness() {
        return 0.0f;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.0f;
    }

    /** No-op — 1.20.1'de kayıt yok; Arcanum.init'teki çağrı sırası bozulmasın diye durur. */
    public static void init() {}
}
