package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** İstemci → sunucu: skill puanlarını sıfırla (ücretsiz respec). Gövdesiz. */
public record RespecPayload() implements ArcanumPayload {

    public static final ResourceLocation ID = new ResourceLocation(Arcanum.MODID, "respec");

    @Override
    public void encode(FriendlyByteBuf buf) {
        // gövdesiz paket — hiçbir alan yazılmaz
    }

    public static RespecPayload decode(FriendlyByteBuf buf) {
        return new RespecPayload();
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
