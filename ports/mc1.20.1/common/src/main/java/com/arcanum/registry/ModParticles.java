package com.arcanum.registry;

import com.arcanum.Arcanum;
import com.arcanum.util.ArcanumColorParticleOption;
import com.mojang.serialization.Codec;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;

/**
 * Partikül tipleri (loader-bağımsız). Tip kaydı common'da; istemci PROVIDER'ları
 * Fabric tarafında ({@code ArcanumFabricClient}) kaydedilir.
 *
 * Renkli tipler {@link ArcanumColorParticleOption} taşır — vanilla ColorParticleOption
 * 1.20.5'te geldiği için 1.20.1'de kendi option sınıfımız kullanılır; her büyü kendi
 * tema rengiyle aynı partikül tipini kullanır (SpellFx üzerinden).
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Arcanum.MODID, Registries.PARTICLE_TYPE);

    /** Büyü kıvılcımı — beyaz-sıcak yıldız çakımı (çok kareli, rastgele sprite). */
    public static final RegistrySupplier<SimpleParticleType> SPELL_SPARK =
            PARTICLES.register("spell_spark", () -> new SimpleParticleType(false) {});

    /** Yumuşak ışıma küresi — patlama/çarpma/aura için (renkli). */
    public static final RegistrySupplier<ParticleType<ArcanumColorParticleOption>> SPELL_GLOW =
            PARTICLES.register("spell_glow", ModParticles::colorType);

    /** İnce iz partikülü — ışın gövdesi için (renkli, kısa ömürlü). */
    public static final RegistrySupplier<ParticleType<ArcanumColorParticleOption>> SPELL_TRAIL =
            PARTICLES.register("spell_trail", ModParticles::colorType);

    /** Kalıcı ışın parçası — büyü ışını ~2 sn havada asılı kalır (renkli, hareketsiz). */
    public static final RegistrySupplier<ParticleType<ArcanumColorParticleOption>> SPELL_BEAM =
            PARTICLES.register("spell_beam", ModParticles::colorType);

    /** Rün glifi — kast çemberleri ve öğrenme ritüeli için (renkli, süzülür). */
    public static final RegistrySupplier<ParticleType<ArcanumColorParticleOption>> MAGIC_RUNE =
            PARTICLES.register("magic_rune", ModParticles::colorType);

    /** Arcanewood yaprak zerresi — biyomda süzülen sihirli toz. */
    public static final RegistrySupplier<SimpleParticleType> LEAF_MOTE =
            PARTICLES.register("leaf_mote", () -> new SimpleParticleType(false) {});

    /**
     * 1.20.1 kayıt kalıbı: ParticleType ctor'u {@code (boolean, Deserializer)} alır
     * (javap ile doğrulandı), {@code streamCodec()} yoktur; ağ ser/de işi
     * DESERIALIZER + writeToNetwork ikilisinde, disk ser/de işi {@code codec()}'tedir.
     */
    private static ParticleType<ArcanumColorParticleOption> colorType() {
        return new ParticleType<ArcanumColorParticleOption>(false, ArcanumColorParticleOption.DESERIALIZER) {
            @Override
            public Codec<ArcanumColorParticleOption> codec() {
                return ArcanumColorParticleOption.codec(this);
            }
        };
    }

    private ModParticles() {}

    public static void init() {
        PARTICLES.register();
    }
}
