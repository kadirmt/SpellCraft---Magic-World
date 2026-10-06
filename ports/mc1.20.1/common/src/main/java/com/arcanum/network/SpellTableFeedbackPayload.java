package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Sunucu → istemci: Büyü Masası öğrenme denemesinin sonucu (başarı/başarısızlık
 * mesajı). Vanilla actionbar/chat mesajları GUI açıkken görünmez — ekranın
 * opak arka planı üstlerine çiziliyor. SpellTableScreen bu payload'u dinleyip
 * kendi içinde bir bildirim olarak gösterir.
 *
 * <p>(1.20.1 portu: {@code ComponentSerialization.STREAM_CODEC} yok —
 * {@link FriendlyByteBuf#writeComponent}/{@link FriendlyByteBuf#readComponent}
 * JSON tabanlı vanilla yazımı kullanılır, kayıpsız.)
 */
public record SpellTableFeedbackPayload(boolean success, Component message) implements ArcanumPayload {

    public static final ResourceLocation ID = new ResourceLocation(Arcanum.MODID, "spell_table_feedback");

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(success);
        buf.writeComponent(message);
    }

    public static SpellTableFeedbackPayload decode(FriendlyByteBuf buf) {
        boolean success = buf.readBoolean();
        Component message = buf.readComponent();
        return new SpellTableFeedbackPayload(success, message);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
