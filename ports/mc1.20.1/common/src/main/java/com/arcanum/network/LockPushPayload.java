package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * İstemci → sunucu: kenetlenme sırasında bir SOL-TIK ("asayı it") sinyali. Gövdesiz —
 * kim ittiği paket bağlamındaki oyuncudan bilinir. Tık SAYIMI ve CPS TAVANI (18)
 * otoritesi sunucudadır ({@link com.arcanum.spell.WandLockManager}); istemci yalnızca
 * ham tık kenarını yollar (autoclicker'a karşı anti-cheat güvenli).
 */
public record LockPushPayload() implements ArcanumPayload {

    public static final ResourceLocation ID = new ResourceLocation(Arcanum.MODID, "lock_push");

    @Override
    public void encode(FriendlyByteBuf buf) {
        // gövdesiz paket — hiçbir alan yazılmaz
    }

    public static LockPushPayload decode(FriendlyByteBuf buf) {
        return new LockPushPayload();
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
