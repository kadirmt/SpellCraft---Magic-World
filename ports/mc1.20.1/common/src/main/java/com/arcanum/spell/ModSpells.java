package com.arcanum.spell;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;

/**
 * Harry Potter büyü kadrosu (23 büyü). Sıra: basic → advanced → dark → unforgivable.
 * Asadaki SELECTED_SPELL bileşeni bu listeye index'ler.
 * tier: basic dahil HİÇBİR kademe baştan bilinmez — Büyü Masası'nda (veya tek
 * büyülük loot kitaplarıyla) tek tek öğrenilmesi gerekir; bir üst kademe ancak
 * bir alttakinin TAMAMI öğrenilince açılır (bkz. SpellGating/SpellTiers).
 * Mana ekonomisi: temel asa 100 mana, +2/sn yeniler (WandTier).
 */
public final class ModSpells {
    public static final List<Spell> SPELLS = new ArrayList<>();

    // ---- basic (baştan bilinir) ----
    public static final Spell EXPELLIARMUS = add("expelliarmus", 55, 20, 0xE8344E, "basic", Spells::expelliarmus);
    public static final Spell STUPEFY = add("stupefy", 30, 15, 0xE03A1C, "basic", Spells::stupefy);
    public static final Spell PETRIFICUS = add("petrificus_totalus", 100, 30, 0x3A66D6, "basic", Spells::petrificusTotalus);
    public static final Spell INCENDIO = add("incendio", 40, 18, 0xFF6A00, "basic", Spells::incendio);
    public static final Spell WINGARDIUM = add("wingardium_leviosa", 30, 15, 0xCFE8FF, "basic", Spells::wingardiumLeviosa);
    public static final Spell LUMOS = add("lumos", 10, 5, 0xEAF4FF, "basic", Spells::lumos);
    public static final Spell PROTEGO = add("protego", 40, 25, 0x9FD8FF, "basic", Spells::protego);
    public static final Spell ACCIO = add("accio", 20, 12, 0xD8C8FF, "basic", Spells::accio);
    public static final Spell EPISKEY = add("episkey", 40, 20, 0xFFD37A, "basic", Spells::episkey);
    public static final Spell FLIPENDO = add("flipendo", 25, 10, 0x6FA8FF, "basic", Spells::flipendo);
    public static final Spell DIFFINDO = add("diffindo", 35, 15, 0xE8EEF5, "basic", Spells::diffindo);
    public static final Spell RICTUSEMPRA = add("rictusempra", 30, 10, 0xFFB8DC, "basic", Spells::rictusempra);
    public static final Spell ARRESTO = add("arresto_momentum", 60, 15, 0xBFE8D8, "basic", Spells::arrestoMomentum);
    public static final Spell DEPULSO = add("depulso", 25, 12, 0x9FD8FF, "basic", Spells::depulso);
    public static final Spell EVERTE_STATUM = add("everte_statum", 30, 15, 0xFF7A3A, "basic", Spells::everteStatum);
    public static final Spell AGUAMENTI = add("aguamenti", 20, 10, 0x4AA3E0, "basic", Spells::aguamenti);
    public static final Spell HOMENUM_REVELIO = add("homenum_revelio", 60, 15, 0xFFD24A, "basic", Spells::homenumRevelio);

    // ---- advanced (kitapla öğrenilir) ----
    public static final Spell GLACIUS = add("glacius", 40, 22, 0x9FE2FF, "advanced", Spells::glacius);
    public static final Spell REDUCTO = add("reducto", 100, 35, 0x4FC3F7, "advanced", Spells::reducto);
    public static final Spell BOMBARDA = add("bombarda", 140, 45, 0xFF8C1A, "advanced", Spells::bombarda);
    public static final Spell IMMOBULUS = add("immobulus", 120, 40, 0xA8D8FF, "advanced", Spells::immobulus);
    public static final Spell CONFRINGO = add("confringo", 126, 40, 0xFF7A33, "advanced", Spells::confringo); // +6t (0.3sn, tester)
    public static final Spell PATRONUM = add("expecto_patronum", 400, 60, 0xD6F0FF, "advanced", Spells::expectoPatronum);
    public static final Spell VENTUS = add("ventus", 90, 30, 0xE8F4FF, "advanced", Spells::ventus);
    public static final Spell DURO = add("duro", 100, 28, 0x9AA3AD, "advanced", Spells::duro);

    // ---- dark ----
    public static final Spell SECTUMSEMPRA = add("sectumsempra", 300, 55, 0xC0392B, "dark", Spells::sectumsempra);
    public static final Spell FIENDFYRE = add("fiendfyre", 400, 70, 0xFF3B1F, "dark", Spells::fiendfyre);

    // ---- unforgivable (3 lanet) ----
    public static final Spell AVADA_KEDAVRA = add("avada_kedavra", 1200, 90, 0x3CB043, "unforgivable", Spells::avadaKedavra);
    public static final Spell CRUCIO = add("crucio", 600, 65, 0xFF2E2E, "unforgivable", Spells::crucio);
    public static final Spell IMPERIO = add("imperio", 650, 70, 0x8A2BE2, "unforgivable", Spells::imperio);

    // ---- SONA eklenenler (index kayması olmasın diye kademe bloklarına DEĞİL, en sona;
    //      menü gruplaması SpellTiers.allOfTier ile yapıldığından konum önemsiz) ----
    /** advanced: sağ tık basılı-tut mavi kalkan; cast time yok, 30 mana/sn; 3 lanet hariç her büyüyü durdurur. */
    public static final Spell PROTEGO_MAXIMA = add("protego_maxima", 90, 35, 0x9FD8FF, "advanced", Spells::protegoMaxima);
    /** dark: bombarda'nın ~3 katı yıkıcı patlama. */
    public static final Spell BOMBARDA_MAXIMA = add("bombarda_maxima", 260, 60, 0xFF6A1A, "dark", Spells::bombardaMaxima);
    /** basic: kilit açma tılsımı — Mühürlü Kapı'nın büyülü mührünü çözer, sıradan kapı/kapakları uzaktan açar. */
    public static final Spell ALOHOMORA = add("alohomora", 40, 10, 0xF2C94C, "basic", Spells::alohomora);
    /** dark: kara duman formu — Ölüm Yiyen uçuşu; 18 mana giriş + formdayken 21 mana/sn drenaj (UmbraFormManager).
     *  Cooldown 0: vanilla ItemCooldowns use()'u hiç çağırtmadığından cooldown'lu formda
     *  sağ-tık çıkışı imkânsızlaşıyordu ("mana bitmeden çıkamıyorum" bug'ı) — giriş bedeli
     *  zaten 18 mana + 21/sn drenaj; cast-time (10 tick) spam'i önlüyor. */
    public static final Spell UMBRAVOLO = add("umbravolo", 0, 18, 0x5B2E8A, "dark", Spells::umbravolo);
    /** advanced: Protego Diabolica — Grindelwald'ın MAVİ ATEŞ ÇEMBERİ: protego_maxima'nın
     *  kanal ikizi (aynı kalkan, aynı 30 mana/sn drenaj, cast time yok, aynı cooldown);
     *  fark: kubbe görseli yerine yere yakın mavi alev halkası, savrulan ve banda yaklaşan
     *  her canlı tutuşur (kaster asla yanmaz). Bkz. Spells.protegoDiabolica. */
    public static final Spell PROTEGO_DIABOLICA = add("protego_diabolica", 90, 35, 0x4FA8FF, "advanced", Spells::protegoDiabolica);
    /** advanced: Vulnera Sanentur — KANAL şifa büyüsü (protego_maxima deseni): sağ tık
     *  BASILI TUT → 1 sn hazırlık (mana akmaz, altın-kızıl toplanma FX'i), sonra bakılan
     *  canlıya 2.5 kalp/sn iyileştirme akışı; drenaj 33 mana/sn (maxima'nın %10 fazlası,
     *  kesirli akümülatör). manaCost=40 yalnız BAŞLANGIÇ KAPISIDIR — kanal büyülerinde
     *  upfront kesinti zaten yok (mana yalnız castSelected'de düşer, kanallar oraya girmez).
     *  Effect kaydı no-op: kanal boru hattı castSelected'i baypas eder, gerçek iş
     *  {@link Spells#vulneraSanenturTick}'te (WandItem.onUseTick çağırır). */
    public static final Spell VULNERA_SANENTUR = add("vulnera_sanentur", 90, 40, 0xE8A24A, "advanced", Spells::vulneraSanentur);

    // ---- PASİFLEŞTİRİLMİŞ büyüler (index kayması YASAK: listedeki yerleri korunur,
    //      davranışları kapatılır). Rictusempra "işlevsiz güldürme büyüsü" bulunduğu için
    //      oyundan çekildi: öğrenilemez (SpellGating), G menüsünde/tier listelerinde
    //      görünmez (SpellTiers filtreleri), loadout'ta boş slot sayılır
    //      (ArcanumPlayerData sanitize) ve castlenemez (WandItem + Spells no-op). ----
    public static final java.util.Set<String> DISABLED = java.util.Set.of("rictusempra");

    /** Bu büyü oyundan çekildi mi (kayıtlı ama pasif)? */
    public static boolean isDisabled(String id) {
        return DISABLED.contains(id);
    }

    /** Index tabanlı disabled kontrolü — geçersiz index false döner (boş slot zaten). */
    public static boolean isDisabled(int index) {
        return index >= 0 && index < SPELLS.size() && isDisabled(SPELLS.get(index).id());
    }

    private ModSpells() {}

    private static Spell add(String id, int cooldown, int mana, int color, String tier, Spell.Effect effect) {
        Spell s = new Spell(id, Component.translatable("spell.arcanum." + id), cooldown, mana, color, tier, effect);
        SPELLS.add(s);
        return s;
    }

    public static void init() {
    }
}
