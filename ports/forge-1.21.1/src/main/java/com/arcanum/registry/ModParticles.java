package com.arcanum.registry;

import com.arcanum.Arcanum;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Partikül tipleri. Tip kaydı burada; istemci PROVIDER'ları Forge tarafında
 * (client katmanı, RegisterParticleProvidersEvent) kaydedilir.
 *
 * Renkli tipler vanilla {@link ColorParticleOption} taşır — her büyü kendi tema
 * rengiyle aynı partikül tipini kullanır (SpellFx üzerinden).
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, Arcanum.MODID);

    /** Büyü kıvılcımı — beyaz-sıcak yıldız çakımı (çok kareli, rastgele sprite). */
    public static final RegistryObject<SimpleParticleType> SPELL_SPARK =
            PARTICLES.register("spell_spark", () -> new SimpleParticleType(false) {});

    /** Yumuşak ışıma küresi — patlama/çarpma/aura için (renkli). */
    public static final RegistryObject<ParticleType<ColorParticleOption>> SPELL_GLOW =
            PARTICLES.register("spell_glow", ModParticles::colorType);

    /** İnce iz partikülü — ışın gövdesi için (renkli, kısa ömürlü). */
    public static final RegistryObject<ParticleType<ColorParticleOption>> SPELL_TRAIL =
            PARTICLES.register("spell_trail", ModParticles::colorType);

    /** Kalıcı ışın parçası — büyü ışını ~2 sn havada asılı kalır (renkli, hareketsiz). */
    public static final RegistryObject<ParticleType<ColorParticleOption>> SPELL_BEAM =
            PARTICLES.register("spell_beam", ModParticles::colorType);

    /** Rün glifi — kast çemberleri ve öğrenme ritüeli için (renkli, süzülür). */
    public static final RegistryObject<ParticleType<ColorParticleOption>> MAGIC_RUNE =
            PARTICLES.register("magic_rune", ModParticles::colorType);

    /** Arcanewood yaprak zerresi — biyomda süzülen sihirli toz. */
    public static final RegistryObject<SimpleParticleType> LEAF_MOTE =
            PARTICLES.register("leaf_mote", () -> new SimpleParticleType(false) {});

    private static ParticleType<ColorParticleOption> colorType() {
        return new ParticleType<ColorParticleOption>(false) {
            @Override
            public MapCodec<ColorParticleOption> codec() {
                return ColorParticleOption.codec(this);
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, ColorParticleOption> streamCodec() {
                return ColorParticleOption.streamCodec(this);
            }
        };
    }

    private ModParticles() {}

    public static void register(IEventBus bus) {
        PARTICLES.register(bus);
    }
}
