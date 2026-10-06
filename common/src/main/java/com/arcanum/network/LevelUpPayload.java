package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sunucu → istemci: oyuncu YENİ bir büyücü seviyesine ulaştı. İstemci bunu alınca
 * sağ-üstte cilalı bir "Seviye Atladın" toast bildirimi gösterir
 * ({@code ArcanumLevelUpToast}). {@code MagicDataPayload}'dan AYRI bir olay paketi —
 * böylece periyodik senkron (mana regen) veya girişteki ilk senkron yanlışlıkla toast
 * tetiklemez.
 */
public record LevelUpPayload(int level) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LevelUpPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "level_up"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LevelUpPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, LevelUpPayload::level, LevelUpPayload::new);

    @Override
    public CustomPacketPayload.Type<LevelUpPayload> type() {
        return TYPE;
    }
}
