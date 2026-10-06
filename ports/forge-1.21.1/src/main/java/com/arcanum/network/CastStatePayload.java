package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sunucu → istemci: cast (büyü yapma) DURUMU. Cast artık server-authoritative
 * {@link com.arcanum.item.CastManager}'da işlendiği için, istemci cast göstergesini
 * (imleç altı minik çubuk, bkz. CastBarHud) yalnızca bu pakete göre çizer.
 *
 * <p>{@code spellIndex} = cast BAŞLADI → ilgili {@link com.arcanum.spell.ModSpells}
 * global index'i; {@code totalTicks} = {@link com.arcanum.spell.SpellCastTime#ticks}.
 * {@code spellIndex = -1} → cast BİTTİ / YOK (istemci göstergeyi temizler).
 */
public record CastStatePayload(int spellIndex, int totalTicks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CastStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "cast_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CastStatePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CastStatePayload::spellIndex,
                    ByteBufCodecs.VAR_INT, CastStatePayload::totalTicks,
                    CastStatePayload::new);

    @Override
    public CustomPacketPayload.Type<CastStatePayload> type() {
        return TYPE;
    }
}
