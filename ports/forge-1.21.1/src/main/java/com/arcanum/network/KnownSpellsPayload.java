package com.arcanum.network;

import java.util.List;

import com.arcanum.Arcanum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sunucu → istemci: oyuncunun bildiği büyü id listesi.
 * Girişte ve her yeni büyü öğrenildiğinde gönderilir; istemci
 * radyal menüde kilitleri buna göre çizer.
 */
public record KnownSpellsPayload(List<String> spellIds) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<KnownSpellsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "known_spells"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KnownSpellsPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                    KnownSpellsPayload::spellIds,
                    KnownSpellsPayload::new);

    @Override
    public CustomPacketPayload.Type<KnownSpellsPayload> type() {
        return TYPE;
    }
}
