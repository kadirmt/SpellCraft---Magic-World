package com.arcanum.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.arcanum.Arcanum;
import com.arcanum.platform.Platform;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Oyuncuya dönük Arcanum yapılandırması — {@code config/arcanum.json}.
 *
 * <p>Loader API'sine doğrudan dokunmaz: dosya {@code Platform.get().configDir()}
 * ({@code <oyun>/config}; Fabric {@code FabricLoader#getConfigDir}, Forge
 * {@code FMLPaths.CONFIGDIR}) altındaki {@code arcanum.json}'dan okunur. 1.21.1'deki göreli
 * {@code config/arcanum.json} yolu ile normal kurulumlarda AYNI dosyadır (oyun dizini cwd).
 * Platform henüz başlatılmadıysa (olmamalı) eski göreli yola düşülür.
 * JSON okuma/yazma GSON ile yapılır (her iki loader'da da çalışma zamanında mevcut).
 *
 * <p>Davranış sözleşmesi:
 * <ul>
 *   <li>Dosya yoksa → varsayılanlarla oluşturulur (pretty-print).</li>
 *   <li>Dosyada eksik anahtar varsa → eksikler varsayılan değerini korur ve dosya
 *       TAM şemayla yeniden yazılır (ileri-uyumluluk: mod güncellenince yeni
 *       ayarlar dosyaya kendiliğinden eklenir).</li>
 *   <li>Bozuk JSON → uyarı loglanır, varsayılanlar kullanılır, OYUN ÇÖKMEZ.
 *       Kullanıcının dosyası ÜZERİNE YAZILMAZ (yazım hatasını düzeltebilsin diye).</li>
 *   <li>{@code /arcanum reloadconfig} komutu {@link #reload()} çağırarak dosyayı
 *       oyunu kapatmadan yeniden okur. DİKKAT: bu bir SUNUCU komutudur ve yalnız
 *       çağrıldığı JVM'in örneğini tazeler. Tek oyunculu/LAN'da istemci ile entegre
 *       sunucu aynı JVM'dedir, her şey anında etkilenir; ADANMIŞ SUNUCUDA ise salt
 *       görsel/istemci-yerel alanlar ({@code manaHudScale}, {@code manaHudOpacity},
 *       {@code manaHudRequiresWand}) için oyuncunun kendi istemcisinde
 *       {@code /arcanumclient reloadconfig} çalıştırılmalıdır.</li>
 * </ul>
 *
 * <p>Alan javadoc'ları aynı zamanda kullanıcı belgeleridir — her ayarın ne yaptığı
 * aşağıda açıklanır.
 */
public final class ArcanumConfig {

    // ===================== KULLANICI AYARLARI =====================

    /**
     * TÜM büyü hasarlarının genel çarpanı. Modun iç dengesinin (asa kademesi,
     * skill ağacı Güç bonusu, Exstimulo iksiri, tur-bazlı denge katsayıları)
     * ÜSTÜNE uygulanır. {@code 1.0} = varsayılan denge, {@code 0.5} = yarı hasar,
     * {@code 2.0} = çift hasar.
     */
    public double damageMult = 1.0;

    /**
     * Mana yenilenme hızı çarpanı. Skill ağacı regen bonusu ve Mana Hızlandırma
     * iksiri etkisinin ÜSTÜNE uygulanır. {@code 1.0} = varsayılan hız,
     * {@code 2.0} = iki kat hızlı mana, {@code 0.5} = yarı hızda mana.
     */
    public double manaRegenMult = 1.0;

    /**
     * Büyü menzili çarpanı. Modun iç global menzil katsayısının (×1.5) ÜSTÜNE
     * uygulanır. {@code 1.0} = varsayılan menzil, {@code 1.5} = %50 daha uzak,
     * {@code 0.5} = yarı menzil.
     */
    public double spellRangeMult = 1.0;

    /**
     * Yüzey spawner'ı (oyuncu yakınında Troll / Kurt Adam / Acromantula /
     * Ölüm Yiyen ve reagent kuşlarını AKTİF doğuran garanti sistem) açık mı?
     * {@code false} yapılırsa yalnızca vanilla/biyom doğal spawn'ları kalır —
     * "çevremde çok mob var" diyenler için kapatma anahtarı.
     */
    public boolean surfaceSpawnerEnabled = true;

    /**
     * Yüzey spawner'ının her turda (~5 sn'de bir) oyuncu başına spawn DENEME
     * olasılığı ({@code 0.0}–{@code 1.0}). Varsayılan {@code 0.35} = turların
     * %35'inde denenir. Düşürmek mob sıklığını azaltır, yükseltmek artırır;
     * {@code 0.0} pratikte spawner'ı kapatır.
     */
    public double surfaceSpawnerChance = 0.35;

    // ---------------- YAPI SIKLIĞI (worldgen) ----------------
    // Oyuncu isteği: "make all structures and mob spawns configurable — everything
    // spawns so often". Her alan 0.0–1.0 arası bir OLASILIK ÇARPANIDIR: 1.0 = modun
    // kendi doğal sıklığı (JSON'daki değer), 0.5 = yarı yarıya seyrek, 0.0 = yapı
    // HİÇ oluşmaz. Seyreltme DETERMİNİSTİKTİR (dünya seed'i + chunk koordinatı
    // hash'i, bkz. StructureGate) — aynı seed + aynı ayar hep aynı dünyayı verir.
    // DİKKAT: yalnız YENİ üretilen chunk'ları etkiler; keşfedilmiş arazi değişmez.

    /**
     * Genel yapı sıklığı çarpanı — aşağıdaki yapı-başına çarpanların ÜSTÜNE uygulanır.
     * {@code 1.0} = varsayılan, {@code 0.5} = tüm Arcanum yapıları yarı sıklıkta,
     * {@code 0.0} = Arcanum yapılarının tamamı kapalı.
     */
    public double structureSpawnChance = 1.0;

    /** Hogsmeade büyücü köyü oluşma olasılığı çarpanı ({@code 0.0}–{@code 1.0}). */
    public double hogsmeadeSpawnChance = 1.0;

    /** Azkaban hapishane kulesi oluşma olasılığı çarpanı ({@code 0.0}–{@code 1.0}). */
    public double azkabanSpawnChance = 1.0;

    /** Unutulmuş Oda (yer altı Basilisk zindanı) oluşma olasılığı çarpanı ({@code 0.0}–{@code 1.0}). */
    public double forgottenChamberSpawnChance = 1.0;

    /**
     * Ölümyiyen Kampı oluşma olasılığı çarpanı ({@code 0.0}–{@code 1.0}).
     * NOT: kampın TABAN sıklığı bu sürümde ayrıca %40 seyreltildi (placed feature
     * {@code rarity_filter} 220 → 367); {@code 1.0} artık o YENİ tabanı ifade eder.
     */
    public double deathEaterCampSpawnChance = 1.0;

    /** Arcane Ruin (kadim taş çemberi) oluşma olasılığı çarpanı ({@code 0.0}–{@code 1.0}). */
    public double arcaneRuinSpawnChance = 1.0;

    // ---------------- MOB SPAWN ÇARPANLARI ----------------
    // Oyuncu isteği: "the creatures are spawning quite frequently". Her alan DOĞAL
    // spawn denemelerinin kabul oranıdır (0.0–1.0): 1.0 = tam sıklık, 0.85 = %15 daha
    // seyrek, 0.0 = o yaratık doğal olarak hiç doğmaz. Yapı spawn'ları (kamp
    // Ölümyiyenleri), spawn yumurtası, /summon, üreme ve dönüşüm ETKİLENMEZ.
    // VARSAYILAN DENGE: büyü malzemesi (reagent) veren yaratıklar 1.0'da BIRAKILDI —
    // oyuncunun büyü ilerlemesi tıkanmasın; reagent vermeyenler 0.85 (−%15).

    /**
     * TÜM Arcanum yaratıklarının doğal spawn'ı için genel çarpan — mob-başına
     * çarpanların ÜSTÜNE uygulanır. {@code 1.0} = varsayılan, {@code 0.5} = hepsi
     * yarı sıklıkta, {@code 0.0} = Arcanum yaratıkları doğal olarak hiç doğmaz.
     */
    public double mobSpawnMult = 1.0;

    /** Ölümyiyen — REAGENT ({@code dementor_essence}) verir, varsayılan {@code 1.0}. */
    public double deathEaterSpawnMult = 1.0;

    /** Ruh Emici — REAGENT ({@code dementor_essence}) verir, varsayılan {@code 1.0}. */
    public double dementorSpawnMult = 1.0;

    /** Anka — REAGENT ({@code phoenix_quill}) TEK kaynağı, varsayılan {@code 1.0}. */
    public double phoenixSpawnMult = 1.0;

    /** Gökgürültü Kuşu — REAGENT ({@code storm_vial}) verir, varsayılan {@code 1.0}. */
    public double thunderbirdSpawnMult = 1.0;

    /** Kar Baykuşu — REAGENT ({@code owl_charm}) TEK kaynağı, varsayılan {@code 1.0}. */
    public double snowyOwlSpawnMult = 1.0;

    /** Mooncalf — REAGENT ({@code mooncalf_dust}) TEK kaynağı, varsayılan {@code 1.0}. */
    public double mooncalfSpawnMult = 1.0;

    /** Troll — reagent vermez, varsayılan {@code 0.85} (−%15). */
    public double trollSpawnMult = 0.85;

    /** Kurtadam — reagent vermez, varsayılan {@code 0.85} (−%15). */
    public double werewolfSpawnMult = 0.85;

    /** Acromantula — ipeği vanilla örümcekten de üretilebilir, varsayılan {@code 0.85} (−%15). */
    public double acromantulaSpawnMult = 0.85;

    /** Grindylow — reagent vermez, varsayılan {@code 0.85} (−%15). */
    public double grindylowSpawnMult = 0.85;

    /** Bowtruckle — reagent vermez, varsayılan {@code 0.85} (−%15). */
    public double bowtruckleSpawnMult = 0.85;

    /** Thestral — reagent vermez, varsayılan {@code 0.85} (−%15). */
    public double thestralSpawnMult = 0.85;

    /** Tek Boynuzlu At — reagent vermez, varsayılan {@code 0.85} (−%15). */
    public double unicornSpawnMult = 0.85;

    /** Hipogrif — reagent vermez, varsayılan {@code 0.85} (−%15). */
    public double hippogriffSpawnMult = 0.85;

    /** Kneazle — reagent vermez, varsayılan {@code 0.85} (−%15). */
    public double kneazleSpawnMult = 0.85;

    /**
     * Expecto Patronum koruyucusunun etki süresi (tick; 20 tick = 1 saniye).
     * Varsayılan {@code 1200} = 60 saniye. Patronus koruması/etkileri bu süre
     * boyunca sürer; kısaltmak büyüyü zayıflatır, uzatmak güçlendirir.
     */
    public int patronusDurationTicks = 280; // ~14 sn (tester: "10-15 sn takilsin")

    /**
     * Büyücü seviye tavanı. Varsayılan {@code 15} = modun standart dengesi.
     * Yükseltmek daha uzun ilerleme (ve {@code skillPointsPerLevel} ile birlikte
     * daha çok skill puanı) sağlar; düşürmek mevcut oyuncuların seviyesini
     * SİLMEZ, yalnızca yeni seviye kazanımını durdurur.
     */
    public int maxWizardLevel = 15;

    /**
     * Seviye başına kazanılan skill (pasif) puanı. Varsayılan {@code 1} =
     * modun standart dengesi. Toplam puan = seviye × bu değer; düşürüldüğünde
     * harcanmış düğümlere DOKUNULMAZ, yalnızca yeni satın alım yapılamaz.
     */
    public int skillPointsPerLevel = 1;

    // ---------- Mana barı (HUD) — SALT GÖRSEL, yalnız İSTEMCİ tarafını ilgilendirir ----------
    // NOT: bu üç ayar sunucuyla senkronlanmaz — her oyuncu kendi tercihi olarak belirler.
    // (Adanmış sunucuda oynarken de istemcinin KENDİ config/arcanum.json'ı okunur.)

    /**
     * Sol-alt mana barının çizim ölçeği. {@code 1.0} = varsayılan (bugünkü) boyut,
     * {@code 2.0} = iki kat büyük, {@code 0.5} = yarı boy. Bar ölçekten bağımsız olarak
     * SOL-ALT köşeye yapışık kalır; büyü slotları da barın yeni yüksekliğine göre
     * yukarı kayar. Güvenli aralık {@code 0.5}–{@code 2.5}.
     */
    public double manaHudScale = 1.0;

    /**
     * Sol-alt mana barının genel saydamlığı. {@code 1.0} = tam opak (bugünkü görünüm),
     * {@code 0.5} = yarı saydam, {@code 0.05} = neredeyse görünmez. Çerçeve, dolum,
     * düşük-mana pulse'ı ve yazı birlikte soluklaşır. Güvenli aralık {@code 0.05}–{@code 1.0}.
     */
    public double manaHudOpacity = 1.0;

    /**
     * Mana barı YALNIZCA elde (ana el veya yan el) asa varken mi görünsün?
     * {@code true} = VARSAYILAN davranış: asa yokken sol alt tamamen boş kalır.
     * {@code false} = mana barı her zaman görünür (asa elde olmasa da).
     * Büyü slotları bundan BAĞIMSIZ olarak her hâlükârda yalnız asa eldeyken çizilir.
     */
    public boolean manaHudRequiresWand = true;

    // ===================== ALTYAPI =====================

    /** Config dosya adı: {@code <oyun>/config/arcanum.json}. */
    private static final String FILE_NAME = "arcanum.json";

    /**
     * Config dosyası: {@code Platform.get().configDir()/arcanum.json}. Tembel çözülür —
     * sınıf Platform.init'ten önce yüklense bile (statik alan yok) yol doğru dizinden alınır.
     */
    private static Path file() {
        if (Platform.isInitialized()) {
            return Platform.get().configDir().resolve(FILE_NAME);
        }
        // Platform.init öncesi (normal akışta olmaz): 1.21.1 ile aynı göreli yol.
        return Paths.get("config", FILE_NAME);
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Tembel yüklenen tekil örnek ({@link #get()} ilk çağrıda okur). */
    private static volatile ArcanumConfig instance;

    /**
     * Aktif yapılandırmayı döner; ilk çağrıda {@code config/arcanum.json} okunur
     * (yoksa varsayılanlarla oluşturulur). Sonraki çağrılar bellekteki örneği döner —
     * dosya değişikliğini almak için {@link #reload()} (ya da oyun içinde
     * {@code /arcanum reloadconfig}) gerekir.
     */
    public static ArcanumConfig get() {
        ArcanumConfig cfg = instance;
        if (cfg == null) {
            synchronized (ArcanumConfig.class) {
                cfg = instance;
                if (cfg == null) {
                    cfg = load();
                    instance = cfg;
                }
            }
        }
        return cfg;
    }

    /** Dosyayı diskteki halinden yeniden okur ({@code /arcanum reloadconfig}). */
    public static void reload() {
        synchronized (ArcanumConfig.class) {
            instance = load();
        }
        Arcanum.LOGGER.info("[Arcanum] config/arcanum.json yeniden yüklendi.");
    }

    /**
     * Dosyayı okur; yoksa varsayılanları yazar. Eksik anahtar → varsayılan korunur
     * ve dosya tam şemayla yeniden yazılır. Bozuk JSON → uyarı + varsayılanlar
     * (dosyaya dokunulmaz), asla exception fırlatmaz.
     */
    private static ArcanumConfig load() {
        Path path = file();
        if (!Files.exists(path)) {
            ArcanumConfig defaults = new ArcanumConfig();
            save(defaults);
            Arcanum.LOGGER.info("[Arcanum] config/arcanum.json bulunamadı — varsayılanlarla oluşturuldu.");
            return defaults;
        }
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
            ArcanumConfig cfg = GSON.fromJson(json, ArcanumConfig.class);
            if (cfg == null) {
                cfg = new ArcanumConfig(); // dosya içeriği "null" ise
            }
            cfg.sanitize();
            // İleri-uyumluluk: dosyada eksik anahtar varsa (eski sürümden kalan config)
            // tam şemayla yeniden yaz — eksikler varsayılan değerleriyle eklenir.
            JsonObject full = GSON.toJsonTree(cfg).getAsJsonObject();
            if (!json.keySet().containsAll(full.keySet())) {
                save(cfg);
                Arcanum.LOGGER.info("[Arcanum] config/arcanum.json eksik anahtarlar varsayılanlarla tamamlandı.");
            }
            return cfg;
        } catch (IOException | RuntimeException e) {
            // JsonSyntaxException / IllegalStateException (kök obje değilse) vb. hepsi burada:
            // oyunu ASLA çökertme, kullanıcının dosyasını da ezme (yazım hatasını düzeltebilsin).
            Arcanum.LOGGER.warn(
                    "[Arcanum] config/arcanum.json okunamadı (bozuk JSON?) — varsayılanlar kullanılıyor. "
                            + "Dosya üzerine YAZILMADI; düzeltin ya da silip yeniden oluşturtun. Hata: {}",
                    e.toString());
            return new ArcanumConfig();
        }
    }

    /** Örneği {@code config/arcanum.json}'a pretty-print olarak yazar (klasörü de oluşturur). */
    private static void save(ArcanumConfig cfg) {
        Path path = file();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, GSON.toJson(cfg) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Arcanum.LOGGER.warn("[Arcanum] config/arcanum.json yazılamadı: {}", e.toString());
        }
    }

    /**
     * Saçma değerleri sessizce güvenli aralığa çeker (NaN/Infinity/negatif çarpan
     * oyunu bozmasın): çarpanlar sonlu ve ≥ 0 olmalı, {@code surfaceSpawnerChance}
     * 0–1 aralığına kıstırılır, {@code patronusDurationTicks} en az 1 tick olur; mana barı
     * ölçek/saydamlık değerleri kendi görsel aralıklarına çekilir.
     */
    private void sanitize() {
        if (!Double.isFinite(damageMult) || damageMult < 0.0) {
            Arcanum.LOGGER.warn("[Arcanum] config: damageMult geçersiz ({}) — 1.0 yapıldı.", damageMult);
            damageMult = 1.0;
        }
        if (!Double.isFinite(manaRegenMult) || manaRegenMult < 0.0) {
            Arcanum.LOGGER.warn("[Arcanum] config: manaRegenMult geçersiz ({}) — 1.0 yapıldı.", manaRegenMult);
            manaRegenMult = 1.0;
        }
        if (!Double.isFinite(spellRangeMult) || spellRangeMult < 0.0) {
            Arcanum.LOGGER.warn("[Arcanum] config: spellRangeMult geçersiz ({}) — 1.0 yapıldı.", spellRangeMult);
            spellRangeMult = 1.0;
        }
        if (!Double.isFinite(surfaceSpawnerChance)) {
            Arcanum.LOGGER.warn("[Arcanum] config: surfaceSpawnerChance geçersiz — 0.35 yapıldı.");
            surfaceSpawnerChance = 0.35;
        } else {
            surfaceSpawnerChance = Math.max(0.0, Math.min(1.0, surfaceSpawnerChance));
        }
        // Yapı sıklığı + mob spawn çarpanları: hepsi 0.0–1.0 olasılık.
        // 1.0 üstü değerler spawn'ı ARTIRMAZ (kapı yalnız eleyebilir) — bu yüzden
        // kullanıcıya sessizce yanlış izlenim vermemek için 1.0'a kıstırılır.
        structureSpawnChance = clampChance("structureSpawnChance", structureSpawnChance, 1.0);
        hogsmeadeSpawnChance = clampChance("hogsmeadeSpawnChance", hogsmeadeSpawnChance, 1.0);
        azkabanSpawnChance = clampChance("azkabanSpawnChance", azkabanSpawnChance, 1.0);
        forgottenChamberSpawnChance = clampChance("forgottenChamberSpawnChance", forgottenChamberSpawnChance, 1.0);
        deathEaterCampSpawnChance = clampChance("deathEaterCampSpawnChance", deathEaterCampSpawnChance, 1.0);
        arcaneRuinSpawnChance = clampChance("arcaneRuinSpawnChance", arcaneRuinSpawnChance, 1.0);
        mobSpawnMult = clampChance("mobSpawnMult", mobSpawnMult, 1.0);
        deathEaterSpawnMult = clampChance("deathEaterSpawnMult", deathEaterSpawnMult, 1.0);
        dementorSpawnMult = clampChance("dementorSpawnMult", dementorSpawnMult, 1.0);
        phoenixSpawnMult = clampChance("phoenixSpawnMult", phoenixSpawnMult, 1.0);
        thunderbirdSpawnMult = clampChance("thunderbirdSpawnMult", thunderbirdSpawnMult, 1.0);
        snowyOwlSpawnMult = clampChance("snowyOwlSpawnMult", snowyOwlSpawnMult, 1.0);
        mooncalfSpawnMult = clampChance("mooncalfSpawnMult", mooncalfSpawnMult, 1.0);
        trollSpawnMult = clampChance("trollSpawnMult", trollSpawnMult, 0.85);
        werewolfSpawnMult = clampChance("werewolfSpawnMult", werewolfSpawnMult, 0.85);
        acromantulaSpawnMult = clampChance("acromantulaSpawnMult", acromantulaSpawnMult, 0.85);
        grindylowSpawnMult = clampChance("grindylowSpawnMult", grindylowSpawnMult, 0.85);
        bowtruckleSpawnMult = clampChance("bowtruckleSpawnMult", bowtruckleSpawnMult, 0.85);
        thestralSpawnMult = clampChance("thestralSpawnMult", thestralSpawnMult, 0.85);
        unicornSpawnMult = clampChance("unicornSpawnMult", unicornSpawnMult, 0.85);
        hippogriffSpawnMult = clampChance("hippogriffSpawnMult", hippogriffSpawnMult, 0.85);
        kneazleSpawnMult = clampChance("kneazleSpawnMult", kneazleSpawnMult, 0.85);
        if (patronusDurationTicks < 1) {
            Arcanum.LOGGER.warn("[Arcanum] config: patronusDurationTicks geçersiz ({}) — 1200 yapıldı.",
                    patronusDurationTicks);
            patronusDurationTicks = 1200;
        }
        if (maxWizardLevel < 1) {
            Arcanum.LOGGER.warn("[Arcanum] config: maxWizardLevel geçersiz ({}) — 1 yapıldı.", maxWizardLevel);
            maxWizardLevel = 1;
        } else if (maxWizardLevel > 1000) {
            // XP toplamı int aritmetiğinde taşmasın diye makul üst tavan.
            Arcanum.LOGGER.warn("[Arcanum] config: maxWizardLevel çok büyük ({}) — 1000 yapıldı.", maxWizardLevel);
            maxWizardLevel = 1000;
        }
        if (skillPointsPerLevel < 1) {
            Arcanum.LOGGER.warn("[Arcanum] config: skillPointsPerLevel geçersiz ({}) — 1 yapıldı.",
                    skillPointsPerLevel);
            skillPointsPerLevel = 1;
        } else if (skillPointsPerLevel > 100) {
            Arcanum.LOGGER.warn("[Arcanum] config: skillPointsPerLevel çok büyük ({}) — 100 yapıldı.",
                    skillPointsPerLevel);
            skillPointsPerLevel = 100;
        }
        // Mana barı (HUD): ölçek 0.5–2.5, saydamlık 0.05–1.0 aralığına kıstırılır.
        // Alt sınırlar 0 DEĞİL: 0 ölçek/alfa barı "kayıp" gösterip oyuncuyu şaşırtırdı;
        // barı büsbütün kaldırmanın doğru yolu asa şartı (ya da vanilla F1) olmalı.
        if (!Double.isFinite(manaHudScale)) {
            Arcanum.LOGGER.warn("[Arcanum] config: manaHudScale geçersiz — 1.0 yapıldı.");
            manaHudScale = 1.0;
        } else {
            manaHudScale = Math.max(0.5, Math.min(2.5, manaHudScale));
        }
        if (!Double.isFinite(manaHudOpacity)) {
            Arcanum.LOGGER.warn("[Arcanum] config: manaHudOpacity geçersiz — 1.0 yapıldı.");
            manaHudOpacity = 1.0;
        } else {
            manaHudOpacity = Math.max(0.05, Math.min(1.0, manaHudOpacity));
        }
    }

    /**
     * 0.0–1.0 aralığına kıstırır (yapı/mob olasılık çarpanları). NaN/Infinity ise
     * varsayılana döner. Her düzeltme mevcut config uyarı kalıbıyla loglanır.
     */
    private static double clampChance(String name, double value, double fallback) {
        if (!Double.isFinite(value)) {
            Arcanum.LOGGER.warn("[Arcanum] config: {} geçersiz (sayı değil) — {} yapıldı.", name, fallback);
            return fallback;
        }
        if (value < 0.0 || value > 1.0) {
            double fixed = Math.max(0.0, Math.min(1.0, value));
            Arcanum.LOGGER.warn("[Arcanum] config: {} aralık dışı ({}) — {} yapıldı.", name, value, fixed);
            return fixed;
        }
        return value;
    }
}
