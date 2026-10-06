package com.arcanum.registry;

import com.arcanum.Arcanum;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Özel zırh malzemeleri.
 *
 * <p>26.x: {@code Registries.ARMOR_MATERIAL} KALDIRILDI (1.21.2) — zırh artık kayıt nesnesi değil, item
 * bileşenleridir ({@code EQUIPPABLE} + {@code ATTRIBUTE_MODIFIERS} + {@code ENCHANTABLE}). Zırh katmanı dokusu
 * {@code assets/arcanum/equipment/cloak_of_invisibility.json} equipment asset'inden çözülür.
 *
 * <p>{@code Item.Properties#humanoidArmor(...)} BİLİNÇLİ OLARAK KULLANILMAZ: o yardımcı {@code MAX_DAMAGE}
 * (dayanıklılık) + {@code REPAIRABLE} ekler → 1.21.1'de kırılmaz olan pelerin kırılabilir hâle gelirdi.
 * Bunun yerine 1.21.1 {@code ArmorItem}'ın verdiği özellikler birebir elle kurulur.
 */
public final class ModArmorMaterials {

    /** Görünmezlik Pelerini equipment asset kimliği — {@code arcanum:cloak_of_invisibility}. */
    public static final ResourceKey<EquipmentAsset> CLOAK_OF_INVISIBILITY_ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID, Arcanum.id("cloak_of_invisibility"));

    /** 1.21.1 malzemesindeki büyülenebilirlik değeri (ArmorItem#getEnchantmentValue). */
    private static final int CLOAK_ENCHANTMENT_VALUE = 9;

    private ModArmorMaterials() {}

    /**
     * Görünmezlik Pelerini — 0 zırh puanı, sadece efekt taşıyıcısı (kaynak: HogCraft).
     * 1.21.1 {@code ArmorItem(CLOAK_OF_INVISIBILITY, CHESTPLATE)} ile aynı bileşenler:
     * göğüs slotu + ARMOR_EQUIP_GENERIC sesi + equipment asset, vanilla "armor.chestplate"
     * kimlikli 0 zırh / 0 sertlik modifier'ları, büyülenebilirlik 9. Dayanıklılık YOK (kırılmaz),
     * tamir malzemesi YOK (eski {@code Ingredient.EMPTY}).
     */
    public static Item.Properties cloakOfInvisibility(Item.Properties properties) {
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(EquipmentSlot.CHEST);
        Identifier modifierId = Identifier.withDefaultNamespace("armor." + ArmorType.CHESTPLATE.getName());
        ItemAttributeModifiers modifiers = ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR,
                        new AttributeModifier(modifierId, 0.0, AttributeModifier.Operation.ADD_VALUE), group)
                .add(Attributes.ARMOR_TOUGHNESS,
                        new AttributeModifier(modifierId, 0.0, AttributeModifier.Operation.ADD_VALUE), group)
                .build();
        return properties
                .attributes(modifiers)
                .enchantable(CLOAK_ENCHANTMENT_VALUE)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST)
                        .setEquipSound(SoundEvents.ARMOR_EQUIP_GENERIC)
                        .setAsset(CLOAK_OF_INVISIBILITY_ASSET)
                        .build());
    }

    /** 26.x: kayıt edilecek bir şey kalmadı (ARMOR_MATERIAL registry'si yok). Kök çağrı sırası korunsun diye durur. */
    public static void init() {
    }
}
