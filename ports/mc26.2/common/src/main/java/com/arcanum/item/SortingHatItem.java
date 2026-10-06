package com.arcanum.item;

import java.util.function.Consumer;

import com.arcanum.Arcanum;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Seçmen Şapka — kafaya giyilebilir büyülü şapka (kasu'nun modeli). Vanilla carved
 * pumpkin deseni: giyilebilirlik + attribute modifier; bilinçli olarak zırh DEĞİL —
 * HumanoidArmorLayer jenerik zırh overlay'i çizmesin ve pelerin/umbra
 * zırh-gizleme mixinlerine takılmasın diye. Model vanilla CustomHeadLayer ile item
 * modelinin "head" display transform'undan çizilir (GeckoLib gerekmez).
 *
 * <p>26.x: {@code Equipable} arayüzü KALDIRILDI → {@code DataComponents.EQUIPPABLE} bileşeni
 * (kurucuda eklenir). Equipment asset'i YOK → {@code HumanoidArmorLayer.shouldRender} false →
 * {@code LivingEntityRenderer} şapkayı kafa item'ı olarak ({@code ItemDisplayContext.HEAD}) çizer
 * (1.21.1 davranışı). Sağ tık = kafaya giy/takas: vanilla {@code Item#use} bileşen {@code swappable}
 * olduğunda {@code Equippable#swapWithEquipmentSlot} çağırır (eski override'ın yaptığı iş).
 * {@code dispensable=false}: 1.21.1'de dağıtıcı davranışı yalnız ArmorItem/Elytra'ya kayıtlıydı,
 * şapka dağıtıcıdan fırlatılıyordu (giydirilmiyordu) — parite.
 *
 * <p>Giyiliyken: +1 zırh (deri kask dengi), +15 mana kapasitesi
 * ({@link com.arcanum.data.ArcanumPlayerData#manaCapacity}) ve +%10 mana yenilenmesi
 * ({@link com.arcanum.spell.ManaRegen}). Craftlanamaz — yalnızca sandık lootu
 * (terk edilmiş maden ocağı + Hogsmeade binaları).
 */
public class SortingHatItem extends Item {
    public SortingHatItem(Properties properties) {
        super(properties.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
                .setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
                .setDispensable(false)
                .build()));
    }

    /** Deri kask dengi +1 zırh — HEAD slot grubunda (Item.Properties.attributes ile verilir). */
    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR,
                        new AttributeModifier(
                                Arcanum.id("sorting_hat_armor"),
                                1.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.HEAD)
                .build();
    }

    /**
     * Verilen varlık kafasında Seçmen Şapka taşıyor mu? Mana kancaları
     * (ArcanumPlayerData.manaCapacity + ManaRegen.tick) bu koşulu kullanır.
     */
    public static boolean isWearing(LivingEntity e) {
        return e != null
                && e.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof SortingHatItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("arcanum.tooltip.sorting_hat.mana")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.accept(Component.translatable("arcanum.tooltip.sorting_hat.regen")
                .withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("arcanum.tooltip.sorting_hat.flavor")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
