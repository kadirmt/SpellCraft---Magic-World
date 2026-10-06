package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.FriendlyByteBuf;
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
 *
 * <p>1.20.1 PORT: {@code CustomPacketPayload}/{@code StreamCodec} yok — diğer paketler
 * gibi {@link ArcanumPayload} (elle encode + statik decode + sabit kanal id).
 */
public record SpellBeamPayload(double fromX, double fromY, double fromZ,
                               double toX, double toY, double toZ,
                               int color, int life, float width, float spread)
        implements ArcanumPayload {

    public static final ResourceLocation ID = new ResourceLocation(Arcanum.MODID, "spell_beam");

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(fromX);
        buf.writeDouble(fromY);
        buf.writeDouble(fromZ);
        buf.writeDouble(toX);
        buf.writeDouble(toY);
        buf.writeDouble(toZ);
        buf.writeVarInt(color);
        buf.writeVarInt(life);
        buf.writeFloat(width);
        buf.writeFloat(spread);
    }

    public static SpellBeamPayload decode(FriendlyByteBuf buf) {
        return new SpellBeamPayload(
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
