package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Sunucu → istemci: oyuncu YENİ bir büyücü seviyesine ulaştı. İstemci bunu alınca
 * sağ-üstte cilalı bir "Seviye Atladın" toast bildirimi gösterir
 * ({@code ArcanumLevelUpToast}). {@code MagicDataPayload}'dan AYRI bir olay paketi —
 * böylece periyodik senkron (mana regen) veya girişteki ilk senkron yanlışlıkla toast
 * tetiklemez.
 */
public record LevelUpPayload(int level) implements ArcanumPayload {

    public static final ResourceLocation ID = new ResourceLocation(Arcanum.MODID, "level_up");

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(level);
    }

    public static LevelUpPayload decode(FriendlyByteBuf buf) {
        return new LevelUpPayload(buf.readVarInt());
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
