package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sunucu → istemci: bir oyuncunun UMBRAVOLO kara duman formu durumu.
 * {@code entityId} formdaki oyuncunun entity id'si (tüm izleyen istemciler bilsin
 * diye HERKESE yayınlanır); {@code active} true = forma girdi, false = çıktı.
 * İstemci ClientUmbraForms setine işler; zırh/eldeki-eşya mixin'leri o sete bakar.
 */
public record UmbraFormPayload(int entityId, boolean active) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UmbraFormPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "umbra_form"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UmbraFormPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, UmbraFormPayload::entityId,
                    ByteBufCodecs.BOOL, UmbraFormPayload::active,
                    UmbraFormPayload::new);

    @Override
    public CustomPacketPayload.Type<UmbraFormPayload> type() {
        return TYPE;
    }
}
