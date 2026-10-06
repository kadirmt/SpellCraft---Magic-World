package com.arcanum.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * Seçmen Şapka — kafaya giyilebilir büyülü şapka (kasu'nun modeli). Vanilla carved
 * pumpkin deseni: {@link Equipable} + attribute modifier; bilinçli olarak ArmorItem
 * DEĞİL — HumanoidArmorLayer jenerik zırh overlay'i çizmesin ve pelerin/umbra
 * zırh-gizleme mixinlerine takılmasın diye. Model vanilla CustomHeadLayer ile item
 * modelinin "head" display transform'undan çizilir (GeckoLib gerekmez).
 *
 * <p>Giyiliyken: +1 zırh (deri kask dengi), +15 mana kapasitesi
 * ({@link com.arcanum.data.ArcanumPlayerData#manaCapacity}) ve +%10 mana yenilenmesi
 * ({@link com.arcanum.spell.ManaRegen}). Craftlanamaz — yalnızca sandık lootu
 * (terk edilmiş maden ocağı + Hogsmeade binaları).
 */
public class SortingHatItem extends Item implements Equipable {
    public SortingHatItem(Properties properties) {
        super(properties);
    }

    /** Deri kask dengi +1 zırh — HEAD slot grubunda (Item.Properties.attributes ile verilir). */
    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR,
                        new AttributeModifier(
                                ResourceLocation.fromNamespaceAndPath("arcanum", "sorting_hat_armor"),
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
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    /** Sağ tık = kafaya giy (kafadakiyle takas) — vanilla Equipable yardımcı akışı. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("arcanum.tooltip.sorting_hat.mana")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("arcanum.tooltip.sorting_hat.regen")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("arcanum.tooltip.sorting_hat.flavor")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
