package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** İstemci → sunucu: skill puanlarını sıfırla (ücretsiz respec). Gövdesiz. */
public record RespecPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RespecPayload> TYPE =
            new CustomPacketPayload.Type<>(Arcanum.id("respec"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RespecPayload> CODEC =
            StreamCodec.unit(new RespecPayload());

    @Override
    public CustomPacketPayload.Type<RespecPayload> type() {
        return TYPE;
    }
}
