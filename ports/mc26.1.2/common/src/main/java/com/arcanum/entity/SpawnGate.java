package com.arcanum.entity;

import java.util.function.ToDoubleFunction;

import com.arcanum.config.ArcanumConfig;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnPlacements;

/**
 * Mob doğal-spawn kapısı — kullanıcı config'indeki mob çarpanlarını uygular.
 *
 * <p>Oyuncu şikayeti "the creatures are spawning quite frequently" üzerine eklendi.
 * Etkin çarpan = {@code mobSpawnMult × <mob>SpawnMult}. Varsayılanlar: büyü malzemesi
 * (reagent) veren yaratıklar {@code 1.0}, diğerleri {@code 0.85} (−%15).
 *
 * <p>Kapı YALNIZ doğal spawn'lara uygulanır ({@link EntitySpawnReason#NATURAL} ve
 * {@link EntitySpawnReason#CHUNK_GENERATION}); yapı spawn'ları (Ölümyiyen kampı),
 * spawn yumurtası, {@code /summon}, üreme ve dönüşüm etkilenmez.
 *
 * <p>ÇİFT UYGULAMA YOK: {@link net.minecraft.world.entity.SpawnPlacements} kapısı
 * yalnız vanilla {@code NaturalSpawner} yolunda çalışır; {@link ArcanumSurfaceSpawner}
 * bu kapıdan geçmediği için oraya {@link #roll} ile AYRI ve tek seferlik uygulanır.
 */
public final class SpawnGate {

    private SpawnGate() {}

    /** Etkin çarpan: genel {@code mobSpawnMult} × mob-başına çarpan (0.0–1.0). */
    public static double multOf(ToDoubleFunction<ArcanumConfig> perMob) {
        ArcanumConfig cfg = ArcanumConfig.get();
        return cfg.mobSpawnMult * perMob.applyAsDouble(cfg);
    }

    /** Çarpana göre tek bir rastgele kabul zarı atar (tick-bazlı; worldgen determinizmi gerekmez). */
    public static boolean roll(ToDoubleFunction<ArcanumConfig> perMob, RandomSource random) {
        double mult = multOf(perMob);
        if (mult >= 1.0) {
            return true;
        }
        if (!(mult > 0.0)) {
            return false;
        }
        return random.nextDouble() < mult;
    }

    /** Doğal olmayan spawn sebeplerini geçirir, doğal olanlara çarpanı uygular. */
    public static boolean allow(ToDoubleFunction<ArcanumConfig> perMob, EntitySpawnReason reason, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }
        return roll(perMob, random);
    }

    /**
     * Mevcut bir {@code SpawnPredicate}'i config çarpanıyla sarmalar
     * (bkz. {@code ModEntities.registerSpawnPlacements}).
     */
    public static <T extends Entity> SpawnPlacements.SpawnPredicate<T> gated(
            ToDoubleFunction<ArcanumConfig> perMob, SpawnPlacements.SpawnPredicate<T> delegate) {
        return (type, level, reason, pos, random) ->
                allow(perMob, reason, random) && delegate.test(type, level, reason, pos, random);
    }
}
