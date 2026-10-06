package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * İstemci → sunucu: kenetlenme sırasında bir SOL-TIK ("asayı it") sinyali. Gövdesiz —
 * kim ittiği {@code context.player()}'dan bilinir. Tık SAYIMI ve CPS TAVANI (18)
 * otoritesi sunucudadır ({@link com.arcanum.spell.WandLockManager}); istemci yalnızca
 * ham tık kenarını yollar (autoclicker'a karşı anti-cheat güvenli).
 */
public record LockPushPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LockPushPayload> TYPE =
            new CustomPacketPayload.Type<>(Arcanum.id("lock_push"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LockPushPayload> CODEC =
            StreamCodec.unit(new LockPushPayload());

    @Override
    public CustomPacketPayload.Type<LockPushPayload> type() {
        return TYPE;
    }
}
