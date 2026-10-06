package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * İstemci → sunucu: radyal menüden seçilen büyü index'ini bildirir.
 * Sunucu, oyuncunun elindeki asanın SELECTED_SPELL bilgisini günceller (doğrulayarak).
 * (1.20.1 portunda DataComponent yerine ModComponents facade NBT anahtarı.)
 */
public record SelectSpellPayload(int index) implements ArcanumPayload {

    public static final ResourceLocation ID = new ResourceLocation(Arcanum.MODID, "select_spell");

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(index);
    }

    public static SelectSpellPayload decode(FriendlyByteBuf buf) {
        return new SelectSpellPayload(buf.readVarInt());
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
