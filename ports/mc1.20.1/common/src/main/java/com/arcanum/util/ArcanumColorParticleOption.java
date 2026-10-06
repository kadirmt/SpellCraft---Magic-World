package com.arcanum.util;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 1.20.1 için renkli partikül option'ı — vanilla {@code ColorParticleOption}
 * 1.20.5'te eklendiği için 1.20.1'de YOKTUR; bu sınıf birebir eşdeğeridir.
 *
 * Renk ARGB (0xAARRGGBB) taşınır; SpellFx alpha'yı içeride ekler (0xFF000000 | rgb).
 * İstemci provider'ları ({@code GlowParticle} vb.) {@link #getRed()}/{@link #getGreen()}/
 * {@link #getBlue()} float erişimcilerini vanilla ColorParticleOption ile aynı adla bulur —
 * provider kodunda yalnızca import + tip adı değişir.
 *
 * Kayıt kalıbı (1.20.1'de ParticleType ctor'u Deserializer alır, streamCodec YOKTUR;
 * javap ile doğrulandı: {@code protected ParticleType(boolean, ParticleOptions$Deserializer)}):
 * bkz. {@code com.arcanum.registry.ModParticles#colorType()}.
 */
public record ArcanumColorParticleOption(ParticleType<ArcanumColorParticleOption> type, int argb)
        implements ParticleOptions {

    /** Komut ayrıştırma + ağ okuma — 1.20.1'de ParticleType kaydı için ZORUNLU. */
    @SuppressWarnings("deprecation")
    public static final ParticleOptions.Deserializer<ArcanumColorParticleOption> DESERIALIZER =
            new ParticleOptions.Deserializer<ArcanumColorParticleOption>() {
                @Override
                public ArcanumColorParticleOption fromCommand(
                        ParticleType<ArcanumColorParticleOption> type, StringReader reader)
                        throws CommandSyntaxException {
                    reader.expect(' ');
                    return new ArcanumColorParticleOption(type, reader.readInt());
                }

                @Override
                public ArcanumColorParticleOption fromNetwork(
                        ParticleType<ArcanumColorParticleOption> type, FriendlyByteBuf buf) {
                    return new ArcanumColorParticleOption(type, buf.readInt());
                }
            };

    /** Diske yazım codec'i (dünya kayıt/komut kaynaklı partikül verisi için). */
    public static Codec<ArcanumColorParticleOption> codec(ParticleType<ArcanumColorParticleOption> type) {
        return Codec.INT.xmap(argb -> new ArcanumColorParticleOption(type, argb), o -> o.argb);
    }

    /** Vanilla {@code ColorParticleOption.create(type, argb)} ile aynı imza düzeni. */
    public static ArcanumColorParticleOption create(ParticleType<ArcanumColorParticleOption> type, int argb) {
        return new ArcanumColorParticleOption(type, argb);
    }

    @Override
    public ParticleType<ArcanumColorParticleOption> getType() {
        return this.type;
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeInt(this.argb);
    }

    @Override
    public String writeToString() {
        return BuiltInRegistries.PARTICLE_TYPE.getKey(this.type) + " " + this.argb;
    }

    // ---- vanilla ColorParticleOption ile aynı float erişimciler (istemci provider'ları için) ----

    public float getRed() {
        return ((this.argb >> 16) & 0xFF) / 255.0F;
    }

    public float getGreen() {
        return ((this.argb >> 8) & 0xFF) / 255.0F;
    }

    public float getBlue() {
        return (this.argb & 0xFF) / 255.0F;
    }

    public float getAlpha() {
        return ((this.argb >>> 24) & 0xFF) / 255.0F;
    }
}
