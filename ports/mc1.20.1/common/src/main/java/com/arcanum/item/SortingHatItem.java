package com.arcanum.item;

import java.util.List;
import java.util.UUID;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
 *
 * <p>1.20.1 farkları (javap ile doğrulandı): Equipable extends Vanishable;
 * getEquipSound düz {@link SoundEvent} döner; Item.Properties.attributes YOK →
 * {@link #getDefaultAttributeModifiers(EquipmentSlot)} override (UUID'li
 * AttributeModifier, Operation.ADDITION); appendHoverText ikinci parametresi
 * {@code @Nullable Level}.
 */
public class SortingHatItem extends Item implements Equipable {
    /** Sabit UUID — 1.20.1 attribute modifier kimliği (yeniden hesaplanmasın diye sabit). */
    private static final UUID ARMOR_MODIFIER_UUID =
            UUID.fromString("b1f8b7d4-3e0a-4f5c-9c1e-7a2d6f4e8a15");

    private static final Multimap<Attribute, AttributeModifier> HEAD_MODIFIERS =
            ImmutableMultimap.of(Attributes.ARMOR,
                    new AttributeModifier(ARMOR_MODIFIER_UUID, "sorting_hat_armor",
                            1.0, AttributeModifier.Operation.ADDITION));

    public SortingHatItem(Properties properties) {
        super(properties);
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
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    /** Deri kask dengi +1 zırh — yalnız HEAD slotunda (1.20.1 Multimap idiomu). */
    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD ? HEAD_MODIFIERS : super.getDefaultAttributeModifiers(slot);
    }

    /** Sağ tık = kafaya giy (kafadakiyle takas) — vanilla Equipable yardımcı akışı. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("arcanum.tooltip.sorting_hat.mana")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("arcanum.tooltip.sorting_hat.regen")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("arcanum.tooltip.sorting_hat.flavor")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
