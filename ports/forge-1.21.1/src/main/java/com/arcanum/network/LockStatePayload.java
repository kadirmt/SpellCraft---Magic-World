package com.arcanum.network;

import java.util.UUID;

import com.arcanum.Arcanum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sunucu → istemci: ASA KENETLENMESİ (Priori Incantatem) durumu. Kenetlenmenin
 * HER İKİ tarafına da gönderilir (ikisi de ışın+düğüm+HUD görsün).
 *
 * <p>Işın uçları istemcide iki oyuncunun {@code wandTip}'inden (UUID ile bulunup)
 * her frame yeniden hesaplanır — mutlak koordinat taşınmaz, sadece kimlik + düğüm.
 *
 * <ul>
 *   <li>{@code a},{@code b}: iki oyuncu UUID'si (a = başlatan).</li>
 *   <li>{@code node}: çarpışma düğümü 0..1 (0 = a ucu, 1 = b ucu).</li>
 *   <li>{@code colorA},{@code colorB}: iki büyünün rengi (RGB).</li>
 *   <li>{@code phase}: 0 = başladı/güncel · 1 = bitti, kazanan A · 2 = bitti, kazanan B · 3 = iptal.</li>
 * </ul>
 *
 * <p>Sabit-alanlı ama UUID/float taşıdığı için elle-codec varyantı
 * ({@link SpellLoadoutPayload} deseni) kullanılır.
 */
public record LockStatePayload(UUID a, UUID b, float node, int colorA, int colorB, int phase)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LockStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "lock_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LockStatePayload> CODEC = new StreamCodec<>() {
        @Override
        public LockStatePayload decode(RegistryFriendlyByteBuf buf) {
            UUID a = buf.readUUID();
            UUID b = buf.readUUID();
            float node = buf.readFloat();
            int colorA = buf.readVarInt();
            int colorB = buf.readVarInt();
            int phase = buf.readVarInt();
            return new LockStatePayload(a, b, node, colorA, colorB, phase);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, LockStatePayload p) {
            buf.writeUUID(p.a());
            buf.writeUUID(p.b());
            buf.writeFloat(p.node());
            buf.writeVarInt(p.colorA());
            buf.writeVarInt(p.colorB());
            buf.writeVarInt(p.phase());
        }
    };

    @Override
    public CustomPacketPayload.Type<LockStatePayload> type() {
        return TYPE;
    }
}
