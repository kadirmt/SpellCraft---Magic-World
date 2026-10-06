package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sunucu → istemci: TEK bir büyü ışını olayı ("bu iki nokta arasında, bu renkte,
 * bu kadar tick yaşayan bir yıldırım çiz"). Görselin tamamı istemcide üretilir
 * ({@code ClientSpellBeams} + {@code LightningBolt}) — eski sunucu-partikül seli
 * (adım başına sendParticles paketi) tamamen kalktı; kast başına TEK paket gider.
 *
 * <ul>
 *   <li>{@code from}/{@code to}: dünya koordinatları (asa ucu → çarpma noktası).</li>
 *   <li>{@code color}: büyünün RGB tema rengi.</li>
 *   <li>{@code life}: tick cinsinden iz ömrü (10 ≈ 0.5 sn), solarak söner.</li>
 *   <li>{@code width}: çekirdek prizma yarıçapı (blok).</li>
 *   <li>{@code spread}: kırılma genliği; {@code <=0} → istemci mesafeye göre seçer.</li>
 * </ul>
 */
public record SpellBeamPayload(double fromX, double fromY, double fromZ,
                               double toX, double toY, double toZ,
                               int color, int life, float width, float spread)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SpellBeamPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "spell_beam"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpellBeamPayload> CODEC = new StreamCodec<>() {
        @Override
        public SpellBeamPayload decode(RegistryFriendlyByteBuf buf) {
            return new SpellBeamPayload(
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SpellBeamPayload p) {
            buf.writeDouble(p.fromX());
            buf.writeDouble(p.fromY());
            buf.writeDouble(p.fromZ());
            buf.writeDouble(p.toX());
            buf.writeDouble(p.toY());
            buf.writeDouble(p.toZ());
            buf.writeVarInt(p.color());
            buf.writeVarInt(p.life());
            buf.writeFloat(p.width());
            buf.writeFloat(p.spread());
        }
    };

    @Override
    public CustomPacketPayload.Type<SpellBeamPayload> type() {
        return TYPE;
    }
}
