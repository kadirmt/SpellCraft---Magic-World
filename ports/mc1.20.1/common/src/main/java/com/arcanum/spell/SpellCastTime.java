package com.arcanum.spell;

/**
 * Büyü YAPMA SÜRESİ (cast time) tablosu — sağ tık basılı tutunca büyü kanallanır
 * ({@link com.arcanum.item.WandItem}), bu kadar tick dolunca ATILIR. Anlık tek-atma
 * dengesizliğini kaldırmak için her büyünün bir hazırlık süresi vardır; süre boyunca
 * kaster etrafında büyü renginde rünler görünür (karşı oyuncu kaçabilsin).
 *
 * Süreler tick cinsindendir (20 tick = 1 saniye). Listede olmayan büyüler kademe
 * ({@link Spell#tier()}) varsayılanına düşer. Sonuç her zaman en az 1 tick'tir.
 */
public final class SpellCastTime {
    private SpellCastTime() {}

    /** {@code s} büyüsünün cast time'ı (tick). Bilinmeyen id → kademe varsayılanı; min 1. */
    public static int ticks(Spell s) {
        int t = switch (s.id()) {
            // ---- unforgivable / dark (ağır lanetler uzun kanallanır) ----
            case "avada_kedavra" -> 40; // 2.0 sn (2.5 -> 2.0, -0.5)
            case "crucio" -> 30;        // 2.0 -> 1.5, -0.5
            case "imperio" -> 30;       // 2.0 -> 1.5, -0.5
            case "sectumsempra" -> 29;  // 1.7 -> 1.45, -0.25
            case "fiendfyre" -> 30;     // 2.0 -> 1.5, -0.5
            case "bombarda_maxima" -> 34; // ağır kara büyü, hazırlık ister
            case "umbravolo" -> 10;     // 0.5 sn — hızlı kaçış büyüsü (kullanıcı spesifikasyonu);
                                        // dark varsayılanı 34'e düşmesin
            // protego_maxima CAST TIME KULLANMAZ (sağ tık basılı-tut kanal, CastManager'ı baypas
            // eder — WandItem.use). Yine de güvenli varsayılan:
            case "protego_maxima" -> 1;
            // protego_diabolica da AYNI kanal boru hattını kullanır (cast time yok) —
            // aynı güvenli varsayılan:
            case "protego_diabolica" -> 1;
            // vulnera_sanentur da kanal büyüsü (1 sn hazırlık kendi onUseTick evresinde) —
            // CastManager'a hiç girmez, güvenli varsayılan:
            case "vulnera_sanentur" -> 1;

            // ---- advanced ----
            case "glacius" -> 24;
            case "reducto" -> 28;
            case "bombarda" -> 29;      // 1.7 -> 1.45, -0.25
            case "confringo" -> 29;     // 1.7 -> 1.45, -0.25
            case "immobulus" -> 26;
            case "ventus" -> 22;
            case "duro" -> 26;
            case "expecto_patronum" -> 25; // 1.5 -> 1.25, -0.25

            // ---- basic (savaş büyüleri) ----
            case "stupefy" -> 10; // 0.5 sn
            case "expelliarmus" -> 20; // 1 sn
            case "incendio" -> 16;
            case "flipendo" -> 12;
            case "diffindo" -> 14;
            case "rictusempra" -> 12;
            case "petrificus_totalus" -> 20; // tam 1 sn (kullanıcı isteği)
            case "depulso" -> 12;
            case "everte_statum" -> 16;
            case "wingardium_leviosa" -> 12;

            // ---- basic (yardımcı / utility, hızlı) ----
            case "lumos" -> 4;
            case "accio" -> 8;
            case "protego" -> 8;
            case "episkey" -> 12;
            case "arresto_momentum" -> 8;
            case "homenum_revelio" -> 12;
            case "aguamenti" -> 6;
            case "alohomora" -> 6; // hızlı utility — kilit açma savaş büyüsü değil

            // listede yoksa: kademe varsayılanı
            default -> tierDefault(s.tier());
        };
        return Math.max(1, t);
    }

    /** Tabloda yer almayan büyüler için kademe bazlı varsayılan cast time (tick). */
    private static int tierDefault(String tier) {
        return switch (tier) {
            case "advanced" -> 26;
            case "dark" -> 34;
            case "unforgivable" -> 44;
            default -> 12; // basic ve bilinmeyen kademeler
        };
    }
}
