package com.arcanum.item;

import com.arcanum.entity.BroomEntity;
import com.arcanum.registry.ModEntities;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Uçan süpürge item'ı — sağ-tık: süpürgeyi önüne bırakır (entity).
 * Süpürgeye sağ-tıkla binilir; tekne mantığında — geri almak için binilmiyorken
 * süpürgeye vurmak yeterli (BroomEntity.hurt() anında kırıp KENDİ kademesinin
 * item'ını düşürür).
 *
 * <p>Kademe: üç item da aynı {@code ModEntities.BROOM}
 * tipini doğurur, fark yalnızca doğan entity'ye basılan {@link BroomEntity}
 * kademe byte'ıdır (0=Oakshaft 79, 1=Comet 260, 2=Cleansweep). Parametresiz
 * ctor eski davranışı korur: mevcut "broom" kaydı Cleansweep olur.
 */
public class BroomItem extends Item {

    /** Bu item'ın doğurduğu süpürgenin kademesi ({@link BroomEntity} sabitleri). */
    private final byte tier;

    /** Eski kayıt uyumluluğu — mevcut "broom" item'ı = Cleansweep (en hızlı). */
    public BroomItem(Properties properties) {
        this(BroomEntity.TIER_CLEANSWEEP, properties);
    }

    public BroomItem(byte tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            BroomEntity broom = ModEntities.BROOM.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
            if (broom == null) {
                return InteractionResult.FAIL;
            }
            broom.setTier(this.tier);
            // YALNIZCA yatay bakış yönü kullanılır — pitch (yukarı/aşağı bakış) dahil
            // edilirse, oyuncu yere koymak için aşağı baktığında süpürge zeminin
            // içine gömülüp anında "kırılıyordu" (isInWall boğulma hasarı → hurt()).
            Vec3 forward = Vec3.directionFromRotation(0.0f, player.getYRot());
            Vec3 pos = player.position().add(forward.scale(1.5)).add(0, 0.4, 0);
            broom.snapTo(pos.x, pos.y, pos.z, player.getYRot(), 0);
            level.addFreshEntity(broom);
            level.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8f, 1.1f);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        // 1.21.1 sidedSuccess: istemci SUCCESS (el sallar), sunucu CONSUME — 26.x SUCCESS'in
        // SwingSource.CLIENT'ı aynı sonucu verir.
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        String key = switch (this.tier) {
            case BroomEntity.TIER_OAKSHAFT -> "arcanum.tooltip.broom.oakshaft";
            case BroomEntity.TIER_COMET -> "arcanum.tooltip.broom.comet";
            default -> "arcanum.tooltip.broom.cleansweep";
        };
        tooltip.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("arcanum.tooltip.broom.drift")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
