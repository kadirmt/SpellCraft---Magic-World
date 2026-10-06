package com.arcanum.network;

import java.util.UUID;

import com.arcanum.Arcanum;
import net.minecraft.network.FriendlyByteBuf;
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
 */
public record LockStatePayload(UUID a, UUID b, float node, int colorA, int colorB, int phase)
        implements ArcanumPayload {

    public static final ResourceLocation ID = new ResourceLocation(Arcanum.MODID, "lock_state");

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(a);
        buf.writeUUID(b);
        buf.writeFloat(node);
        buf.writeVarInt(colorA);
        buf.writeVarInt(colorB);
        buf.writeVarInt(phase);
    }

    public static LockStatePayload decode(FriendlyByteBuf buf) {
        UUID a = buf.readUUID();
        UUID b = buf.readUUID();
        float node = buf.readFloat();
        int colorA = buf.readVarInt();
        int colorB = buf.readVarInt();
        int phase = buf.readVarInt();
        return new LockStatePayload(a, b, node, colorA, colorB, phase);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
