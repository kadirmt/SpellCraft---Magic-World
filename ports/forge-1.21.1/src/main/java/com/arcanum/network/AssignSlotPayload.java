package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * İstemci → sunucu: büyü menüsünden (G) bir slota büyü atar/temizler.
 * {@code spellIndex} -1 → slotu temizle; ≥0 → o global büyü index'ini ata.
 * Sunucu TÜM doğrulamayı kendi yapar: page/slot aralığı + (atama ise) büyünün
 * gerçekten bilindiği ({@code SpellGating.knows}). İstemciden yalnızca istek gelir.
 */
public record AssignSlotPayload(int page, int slot, int spellIndex) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AssignSlotPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "assign_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AssignSlotPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, AssignSlotPayload::page,
                    ByteBufCodecs.VAR_INT, AssignSlotPayload::slot,
                    ByteBufCodecs.VAR_INT, AssignSlotPayload::spellIndex,
                    AssignSlotPayload::new);

    @Override
    public CustomPacketPayload.Type<AssignSlotPayload> type() {
        return TYPE;
    }
}
