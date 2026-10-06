package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Sunucu → istemci: bir oyuncunun UMBRAVOLO kara duman formu durumu.
 * {@code entityId} formdaki oyuncunun entity id'si (tüm izleyen istemciler bilsin
 * diye HERKESE yayınlanır); {@code active} true = forma girdi, false = çıktı.
 * İstemci ClientUmbraForms setine işler; zırh/eldeki-eşya/elytra/kafa render
 * mixin'leri o sete bakar.
 *
 * <p>1.20.1 PORT: {@code CustomPacketPayload}+{@code StreamCodec} yerine
 * {@link ArcanumPayload} elle encode/decode kalıbı (f3-network-contract).
 */
public record UmbraFormPayload(int entityId, boolean active) implements ArcanumPayload {

    public static final ResourceLocation ID = new ResourceLocation(Arcanum.MODID, "umbra_form");

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeBoolean(active);
    }

    public static UmbraFormPayload decode(FriendlyByteBuf buf) {
        return new UmbraFormPayload(buf.readVarInt(), buf.readBoolean());
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
