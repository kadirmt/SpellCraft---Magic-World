package com.arcanum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.20.1 PORT: {@code CustomPacketPayload}/{@code StreamCodec} 1.20.1'de yok —
 * her Arcanum paketi bu arayüzü uygular: elle {@link #encode(FriendlyByteBuf)}
 * + sınıf başına {@code static decode(FriendlyByteBuf)} + sabit kanal id'si
 * ({@code ID} alanı, {@link #id()} onu döner).
 *
 * <p>Gönderim {@link ArcanumNetwork} yardımcıları üzerinden Architectury
 * {@code NetworkManager} ile yapılır (FriendlyByteBuf tabanlı) — böylece hem
 * Fabric hem Forge modülü SIFIR ek ağ koduyla çalışır.
 */
public interface ArcanumPayload {

    /** Paketin kanal id'si (sınıftaki sabit {@code ID}). */
    ResourceLocation id();

    /** Paket alanlarını buffer'a yazar (decode ile AYNI sıra/tipte olmalı). */
    void encode(FriendlyByteBuf buf);
}
