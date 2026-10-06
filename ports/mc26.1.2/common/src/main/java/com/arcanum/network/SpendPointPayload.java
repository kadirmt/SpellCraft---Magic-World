package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * İstemci → sunucu: skill ağacında bir bölüme ({@code track}: 0=kapasite 1=regen 2=cooldown
 * 3=güç) puan harca. Doğrulama (puan var mı, bölüm dolu mu) SUNUCUDA yapılır.
 */
public record SpendPointPayload(int track) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SpendPointPayload> TYPE =
            new CustomPacketPayload.Type<>(Arcanum.id("spend_point"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpendPointPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, SpendPointPayload::track, SpendPointPayload::new);

    @Override
    public CustomPacketPayload.Type<SpendPointPayload> type() {
        return TYPE;
    }
}
