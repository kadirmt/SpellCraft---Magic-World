package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * İstemci → sunucu: skill ağacında bir bölüme ({@code track}: 0=kapasite 1=regen 2=cooldown
 * 3=güç) puan harca. Doğrulama (puan var mı, bölüm dolu mu) SUNUCUDA yapılır.
 */
public record SpendPointPayload(int track) implements ArcanumPayload {

    public static final ResourceLocation ID = new ResourceLocation(Arcanum.MODID, "spend_point");

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(track);
    }

    public static SpendPointPayload decode(FriendlyByteBuf buf) {
        return new SpendPointPayload(buf.readVarInt());
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
