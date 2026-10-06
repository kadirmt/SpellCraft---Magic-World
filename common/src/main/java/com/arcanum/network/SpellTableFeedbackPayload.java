package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sunucu → istemci: Büyü Masası öğrenme denemesinin sonucu (başarı/başarısızlık
 * mesajı). Vanilla actionbar/chat mesajları GUI açıkken görünmez — ekranın
 * opak arka planı üstlerine çiziliyor. SpellTableScreen bu payload'u dinleyip
 * kendi içinde bir bildirim olarak gösterir.
 */
public record SpellTableFeedbackPayload(boolean success, Component message) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SpellTableFeedbackPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "spell_table_feedback"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpellTableFeedbackPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, SpellTableFeedbackPayload::success,
                    ComponentSerialization.STREAM_CODEC, SpellTableFeedbackPayload::message,
                    SpellTableFeedbackPayload::new);

    @Override
    public CustomPacketPayload.Type<SpellTableFeedbackPayload> type() {
        return TYPE;
    }
}
