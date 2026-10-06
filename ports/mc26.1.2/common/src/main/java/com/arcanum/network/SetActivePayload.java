package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * İstemci → sunucu: aktif dizilim slotunu (sayfa+slot) değiştirir
 * (+/- ile sayfa, Shift+tekerlek ile slot gezinme). Sunucu clamp'ler (0..2 / 0..3).
 */
public record SetActivePayload(int page, int slot) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetActivePayload> TYPE =
            new CustomPacketPayload.Type<>(Arcanum.id("set_active"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetActivePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SetActivePayload::page,
                    ByteBufCodecs.VAR_INT, SetActivePayload::slot,
                    SetActivePayload::new);

    @Override
    public CustomPacketPayload.Type<SetActivePayload> type() {
        return TYPE;
    }
}
