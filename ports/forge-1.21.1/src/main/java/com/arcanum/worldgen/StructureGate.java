package com.arcanum.worldgen;

/**
 * Yapı/feature seyreltme kapısı — kullanıcı config'indeki yapı çarpanlarını
 * DETERMİNİSTİK biçimde uygular.
 *
 * <p>Oyuncu şikayeti "everything spawns so often" üzerine eklendi. Kapı
 * {@code Math.random()} ya da tick-bazlı rastgelelik KULLANMAZ: karar yalnız
 * (dünya seed'i, chunk koordinatı, yapıya özel tuz) üçlüsünün SplitMix64
 * karışımından türetilir. Böylece aynı seed + aynı ayar her zaman aynı dünyayı
 * üretir; chunk'lar farklı sıralarda üretilse bile sonuç değişmez.
 *
 * <p>NOT: config değişince (ör. {@code /arcanum reloadconfig}) yalnız HENÜZ
 * ÜRETİLMEMİŞ chunk'lar etkilenir; mevcut arazi olduğu gibi kalır.
 */
public final class StructureGate {

    private StructureGate() {}

    /**
     * Bu chunk'ta yapı üretilsin mi?
     *
     * @param seed   dünya seed'i (structure: {@code context.seed()}, feature: {@code level.getSeed()})
     * @param chunkX chunk X koordinatı
     * @param chunkZ chunk Z koordinatı
     * @param salt   yapıya özel sabit tuz (farklı yapılar aynı chunk'ta aynı kararı vermesin)
     * @param chance 0.0–1.0 kabul olasılığı; {@code >= 1.0} her zaman geçer, {@code <= 0.0} hiç geçmez
     */
    public static boolean allow(long seed, int chunkX, int chunkZ, long salt, double chance) {
        if (!(chance > 0.0)) {
            return false; // 0.0 ya da NaN -> kapalı
        }
        if (chance >= 1.0) {
            return true;  // varsayılan: JSON'daki doğal sıklık, hiç eleme yok
        }
        long h = seed ^ (salt * 0x9E3779B97F4A7C15L);
        h = mix(h + chunkX * 0xBF58476D1CE4E5B9L);
        h = mix(h + chunkZ * 0x94D049BB133111EBL);
        double u = (h >>> 11) * 0x1.0p-53; // [0.0, 1.0)
        return u < chance;
    }

    /** SplitMix64 son karıştırma adımı — komşu chunk'lar arasında korelasyon bırakmaz. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
