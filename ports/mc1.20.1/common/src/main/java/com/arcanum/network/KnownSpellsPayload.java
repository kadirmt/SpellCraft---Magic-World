package com.arcanum.network;

import java.util.ArrayList;
import java.util.List;

import com.arcanum.Arcanum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Sunucu → istemci: oyuncunun bildiği büyü id listesi.
 * Girişte ve her yeni büyü öğrenildiğinde gönderilir; istemci
 * radyal menüde kilitleri buna göre çizer.
 */
public record KnownSpellsPayload(List<String> spellIds) implements ArcanumPayload {

    public static final ResourceLocation ID = new ResourceLocation(Arcanum.MODID, "known_spells");

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(spellIds.size());
        for (String id : spellIds) {
            buf.writeUtf(id);
        }
    }

    public static KnownSpellsPayload decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ids.add(buf.readUtf());
        }
        return new KnownSpellsPayload(ids);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
