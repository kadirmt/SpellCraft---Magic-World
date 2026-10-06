package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * İstemci → sunucu: radyal menüden seçilen büyü index'ini bildirir.
 * Sunucu, oyuncunun elindeki asanın SELECTED_SPELL bileşenini günceller (doğrulayarak).
 */
public record SelectSpellPayload(int index) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SelectSpellPayload> TYPE =
            new CustomPacketPayload.Type<>(Arcanum.id("select_spell"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectSpellPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, SelectSpellPayload::index, SelectSpellPayload::new);

    @Override
    public CustomPacketPayload.Type<SelectSpellPayload> type() {
        return TYPE;
    }
}
