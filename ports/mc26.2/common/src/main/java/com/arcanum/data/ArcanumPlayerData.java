package com.arcanum.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.arcanum.Arcanum;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;

/**
 * Oyuncu başına kalıcı büyü bilgisi (öğrenilmiş büyüler + büyü dizilimi/loadout).
 * Sunucu-global SavedDataStorage'da saklanır ({@code <dünya>/data/arcanum/arcanum_player_data.dat})
 * — loader-bağımsız, dünya ile birlikte kaydedilir. 1.21.1 dünyasındaki eski
 * {@code <dünya>/data/arcanum_player_data.dat} ilk erişimde bir kez içe aktarılır (bkz. {@link #get}).
 * İstemciye senkron {@code KnownSpellsPayload} / {@code SpellLoadoutPayload} ile Fabric
 * tarafında yapılır.
 *
 * <p>Loadout: her oyuncuya 3 SAYFA × 4 SLOT = 12 slotluk dizilim. Her slot ya bir global
 * büyü index'i ({@code ModSpells.SPELLS} içindeki 0..N) ya da boş ({@code -1}). Aktif
 * slot {@code activePage}×4 + {@code activeSlot} ile belirlenir; sağ-tık bu slottaki
 * büyüyü kast eder.
 */
public class ArcanumPlayerData extends SavedData {
    private static final String STORAGE_KEY = "arcanum_player_data";

    /** Loadout boyut sabitleri — payload/HUD/menü ile BİREBİR aynı olmalı. */
    public static final int PAGES = 3;
    public static final int SLOTS = 4;
    public static final int TOTAL = PAGES * SLOTS; // 12

    private final Map<UUID, Set<String>> knownSpells = new HashMap<>();
    /** UUID → int[12] loadout (her eleman global büyü index'i veya -1=boş). */
    private final Map<UUID, int[]> loadouts = new HashMap<>();
    /** UUID → aktif sayfa (0..2). Yoksa 0. */
    private final Map<UUID, Integer> activePages = new HashMap<>();
    /** UUID → aktif slot (0..3). Yoksa 0. */
    private final Map<UUID, Integer> activeSlots = new HashMap<>();

    // ---- 10. tur: büyücü level + skill ağacı + oyuncu-bazlı mana ----
    private final Map<UUID, Integer> wizardLevel = new HashMap<>();    // 0..20
    private final Map<UUID, Integer> wizardXp = new HashMap<>();       // birikimli (mevcut level içi)
    private final Map<UUID, Integer> manaCapNodes = new HashMap<>();   // 0..7
    private final Map<UUID, Integer> manaRegenNodes = new HashMap<>(); // 0..7
    private final Map<UUID, Integer> cooldownNodes = new HashMap<>();  // 0..10
    private final Map<UUID, Integer> powerNodes = new HashMap<>();     // 0..7
    private final Map<UUID, Integer> currentMana = new HashMap<>();    // anlık mana (tam sayı)

    // ---- 26.x göç güvencesi (1.21.1'de yok; bkz. retryPendingMigration) ----
    /** Eski 1.21.1 dosyası okunamadığı için göç bekliyor. Yalnız true iken diske yazılır
     *  ({@code legacyMigrationPending}) → normal dosyaların iç biçimi 1.21.1 ile aynı kalır. */
    private boolean legacyPending;
    /** Bu sunucu oturumunda göç (yeniden) denendi mi — diske YAZILMAZ; her get()'te deneme olmasın. */
    private boolean legacyRecoveryTried;
    private static final String LEGACY_PENDING_KEY = "legacyMigrationPending";
    private static final int LEGACY_READ_ATTEMPTS = 3;
    private static final long LEGACY_RETRY_DELAY_MS = 150L;

    /**
     * Dosyadaki iç NBT biçimi 1.21.1 ile BİREBİR aynıdır: {@code data} anahtarının altına
     * {@code {players:{<uuid>:{...}}}} yazılır ({@link #load}/{@link #toTag}).
     */
    public static final Codec<ArcanumPlayerData> CODEC =
            CompoundTag.CODEC.xmap(ArcanumPlayerData::load, ArcanumPlayerData::toTag);

    /**
     * 26.1: kimlik Identifier → dosya yolu {@code <depo>/<namespace>/<path>.dat}
     * (SavedDataStorage#getDataFile). Sunucu-global depo = {@code <dünya>/data} →
     * {@code <dünya>/data/arcanum/arcanum_player_data.dat}.
     *
     * <p>DataFixTypes null OLAMAZ: diskte dosya varken readTagFromDisk null tipte
     * NPE atar, yükleme sessizce başarısız olur ve tüm öğrenilmiş büyüler silinir.
     * SAVED_DATA_MAP_INDEX'in tek fixer'ı (MapIdFix) çok eski sürüm (1925) içindir;
     * dosya güncel DataVersion ile yazıldığından update no-op'tur — sabit güvenlidir.
     */
    public static final SavedDataType<ArcanumPlayerData> TYPE = new SavedDataType<>(
            Arcanum.id(STORAGE_KEY), ArcanumPlayerData::new, CODEC,
            DataFixTypes.SAVED_DATA_MAP_INDEX);

    public static ArcanumPlayerData get(MinecraftServer server) {
        // 26.1 primer: global veri overworld yerine SUNUCU deposunda tutulmalı.
        SavedDataStorage storage = server.getDataStorage();
        ArcanumPlayerData data = storage.get(TYPE);
        if (data != null) {
            // Önceki bir açılışta eski dosya okunamadıysa (bayrak diske yazıldı) göçü bu
            // oturumda BİR KEZ yeniden dene — boş veri kalıcı olarak "göç edildi" sayılmasın.
            if (data.legacyPending && !data.legacyRecoveryTried) {
                data.legacyRecoveryTried = true;
                retryPendingMigration(server, data);
            }
            return data;
        }
        // Önbellekte/diskte yeni biçim yok → (ilk kez) eski dünya göçünü dene.
        data = loadMissing(server);
        data.legacyRecoveryTried = true; // bu oturumda göç zaten denendi
        storage.set(TYPE, data); // önbelleğe al + dirty → sonraki kayıtta yazılır
        return data;
    }

    // ---- Eski dünya göçü (1.21.1 → 26.x, ARCHITECTURE §5) ----

    /**
     * {@link SavedDataStorage#get} null döndüğünde çağrılır (tek sefer; sonrası önbellekten).
     * <ol>
     *   <li>Yeni dosya diskte VARSA ama okunamadıysa (bozuk) → vanilla davranışı gibi boş
     *       veriyle devam edilir, ancak ezilmeden önce {@code .corrupt-<zaman>} yedeği alınır.</li>
     *   <li>Yeni dosya YOKSA → eski konumlar sırayla aranır; bulunan ilk dosya aynı
     *       {@link #load} mantığıyla okunur, yeni yola HEMEN (senkron) yazılır, ardından eski
     *       dosya {@code .migrated} uzantısıyla yeniden adlandırılır (SİLİNMEZ). Yazma
     *       başarısızsa eski dosyaya dokunulmaz.</li>
     *   <li>Eski dosya birkaç denemede de OKUNAMAZSA → boş veriyle devam edilir, eski dosyaya
     *       dokunulmaz ({@code .unreadable-<zaman>} kopyası alınır) ve {@link #legacyPending}
     *       bayrağı diske yazılır; sonraki açılışta {@link #retryPendingMigration} göçü
     *       yeniden dener ve bu arada oluşan veriyle birleştirir.</li>
     * </ol>
     */
    private static ArcanumPlayerData loadMissing(MinecraftServer server) {
        Path dataDir = server.getWorldPath(LevelResource.DATA);
        Path newFile = TYPE.id().withSuffix(".dat").resolveAgainst(dataDir);
        if (Files.exists(newFile)) {
            backupCorrupt(newFile);
            return new ArcanumPlayerData();
        }
        Path root = server.getWorldPath(LevelResource.ROOT);
        for (Path legacy : legacyCandidates(dataDir, root)) {
            if (!Files.isRegularFile(legacy)) {
                continue;
            }
            ArcanumPlayerData migrated;
            try {
                migrated = readLegacy(legacy);
            } catch (IOException | RuntimeException e) {
                // Geçici sebepler (antivirüs / OneDrive kilidi / yarım kopya) olabilir: boş veriyle
                // devam edilir AMA "göç bekliyor" bayrağı diske yazılır → sonraki açılışta
                // retryPendingMigration göçü yeniden dener. Eski dosyaya dokunulmaz; ek güvence
                // olarak bir kopyası alınır.
                Path copy = backupUnreadable(legacy);
                Arcanum.LOGGER.error("[Arcanum] Eski 1.21.1 oyuncu verisi {} deneme sonunda okunamadı ({}) — "
                                + "şimdilik BOŞ veriyle devam ediliyor. Eski dosya yerinde KORUNUYOR (kopya: {}); "
                                + "göç bir sonraki sunucu açılışında OTOMATİK yeniden denenecek.",
                        LEGACY_READ_ATTEMPTS, legacy, copy == null ? "alınamadı" : copy.getFileName(), e);
                ArcanumPlayerData pending = new ArcanumPlayerData();
                pending.legacyPending = true;
                return pending;
            }
            try {
                writeNow(migrated, newFile);
            } catch (IOException | RuntimeException e) {
                Arcanum.LOGGER.error("[Arcanum] Göç edilen oyuncu verisi {} yoluna yazılamadı — eski dosya korunuyor.",
                        newFile, e);
                return migrated; // bellekte geçerli; normal kayıt döngüsü yine yazmayı dener
            }
            Path done = markMigrated(legacy);
            Arcanum.LOGGER.info("[Arcanum] 1.21.1 oyuncu verisi içe aktarıldı: {} → {} ({} oyuncu); eski dosya {}",
                    legacy, newFile, migrated.playerCount(), done.getFileName());
            return migrated;
        }
        return new ArcanumPlayerData();
    }

    /**
     * Önceki açılışta okunamayan eski dosyanın göçünü yeniden dener ({@link #legacyPending}).
     * Bu arada oyuncular oynamış olabileceğinden (mana/level vb. yeni kayıtlar) eski veri
     * ÜZERİNE YAZILMAZ, {@link #mergeLegacy} ile birleştirilir: oyuncu başına daha ileri
     * ilerleme (level, sonra XP) korunur, öğrenilmiş büyüler birleşimdir.
     */
    private static void retryPendingMigration(MinecraftServer server, ArcanumPlayerData data) {
        Path dataDir = server.getWorldPath(LevelResource.DATA);
        Path newFile = TYPE.id().withSuffix(".dat").resolveAgainst(dataDir);
        Path root = server.getWorldPath(LevelResource.ROOT);
        Path legacy = null;
        for (Path candidate : legacyCandidates(dataDir, root)) {
            if (Files.isRegularFile(candidate)) {
                legacy = candidate;
                break;
            }
        }
        if (legacy == null) {
            Arcanum.LOGGER.warn("[Arcanum] Bekleyen 1.21.1 oyuncu verisi göçü var ama eski dosya artık bulunamadı — "
                    + "bekleme bayrağı kaldırıldı (.unreadable-* kopyası varsa elle geri yüklenebilir).");
            data.legacyPending = false;
            data.setDirty();
            return;
        }
        ArcanumPlayerData old;
        try {
            old = readLegacy(legacy);
        } catch (IOException | RuntimeException e) {
            Arcanum.LOGGER.error("[Arcanum] Eski 1.21.1 oyuncu verisi {} yine okunamadı — mevcut veriyle devam ediliyor; "
                    + "eski dosya korunuyor, göç bir sonraki açılışta tekrar denenecek.", legacy, e);
            return;
        }
        // Birleştirmeden önce mevcut yeni dosyanın yedeği (en iyi çaba).
        if (Files.isRegularFile(newFile)) {
            Path pre = newFile.resolveSibling(newFile.getFileName() + ".pre-recovery-" + System.currentTimeMillis());
            try {
                Files.copy(newFile, pre, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException | RuntimeException e) {
                Arcanum.LOGGER.warn("[Arcanum] Göç kurtarma öncesi yedek alınamadı ({}): {}", newFile, e.toString());
            }
        }
        int merged = data.mergeLegacy(old);
        data.legacyPending = false;
        data.setDirty();
        try {
            writeNow(data, newFile);
        } catch (IOException | RuntimeException e) {
            Arcanum.LOGGER.error("[Arcanum] Kurtarılan oyuncu verisi {} yoluna hemen yazılamadı — bellekte geçerli, "
                    + "normal kayıt döngüsü yazacak; eski dosya korunuyor.", newFile, e);
            return;
        }
        Path done = markMigrated(legacy);
        Arcanum.LOGGER.info("[Arcanum] Bekleyen 1.21.1 oyuncu verisi göçü tamamlandı: {} → {} ({} oyuncu birleştirildi); eski dosya {}",
                legacy, newFile, merged, done.getFileName());
    }

    /** Eski dosyayı {@code .migrated} (çakışırsa {@code .migrated-<zaman>}) olarak yeniden adlandırır (SİLMEZ). */
    private static Path markMigrated(Path legacy) {
        Path done = legacy.resolveSibling(legacy.getFileName() + ".migrated");
        try {
            if (Files.exists(done)) {
                done = legacy.resolveSibling(legacy.getFileName() + ".migrated-" + System.currentTimeMillis());
            }
            Files.move(legacy, done);
        } catch (IOException | RuntimeException e) {
            // Yeni dosya yazıldı; bir sonraki açılışta yeni dosya bulunduğu için tekrar göç OLMAZ.
            Arcanum.LOGGER.warn("[Arcanum] Eski oyuncu verisi yeniden adlandırılamadı ({}): {}", legacy, e.toString());
        }
        return done;
    }

    /** Eski dosyayı birkaç kez (kısa aralıkla) okumayı dener — geçici dosya kilitlerine karşı. */
    private static ArcanumPlayerData readLegacy(Path legacy) throws IOException {
        IOException lastIo = null;
        RuntimeException lastRt = null;
        for (int attempt = 1; attempt <= LEGACY_READ_ATTEMPTS; attempt++) {
            try {
                return load(unwrapLegacy(readNbtFile(legacy)));
            } catch (IOException e) {
                lastIo = e;
                lastRt = null;
            } catch (RuntimeException e) {
                lastRt = e;
                lastIo = null;
            }
            if (attempt < LEGACY_READ_ATTEMPTS) {
                Arcanum.LOGGER.warn("[Arcanum] Eski oyuncu verisi okunamadı ({}), deneme {}/{} — yeniden denenecek.",
                        legacy, attempt, LEGACY_READ_ATTEMPTS);
                try {
                    Thread.sleep(LEGACY_RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (lastRt != null) {
            throw lastRt;
        }
        throw lastIo;
    }

    /** Okunamayan eski dosyanın kopyasını alır (orijinale DOKUNMAZ). @return kopya yolu veya null. */
    private static Path backupUnreadable(Path legacy) {
        Path copy = legacy.resolveSibling(legacy.getFileName() + ".unreadable-" + System.currentTimeMillis());
        try {
            Files.copy(legacy, copy, StandardCopyOption.REPLACE_EXISTING);
            return copy;
        } catch (IOException | RuntimeException e) {
            Arcanum.LOGGER.warn("[Arcanum] Okunamayan eski oyuncu verisinin kopyası alınamadı ({}): {}", legacy, e.toString());
            return null;
        }
    }

    /**
     * Gecikmeli göç birleşimi: eski verideki her oyuncu için, mevcut kayıt yoksa ya da eski
     * kayıt daha ileriyse (level, eşitse XP) eski kaydın TÜM alanları alınır; öğrenilmiş
     * büyüler her durumda iki tarafın birleşimidir. Yalnız mevcut veride olan oyunculara
     * dokunulmaz. @return eski veriden gelen oyuncu sayısı.
     */
    private int mergeLegacy(ArcanumPlayerData old) {
        Set<UUID> current = allIds();
        Set<UUID> oldIds = old.allIds();
        for (UUID id : oldIds) {
            Set<String> union = new HashSet<>(knownSpells.getOrDefault(id, Set.of()));
            union.addAll(old.knownSpells.getOrDefault(id, Set.of()));
            if (!current.contains(id) || old.isAheadOf(this, id)) {
                int[] lo = old.loadouts.get(id);
                copyEntry(loadouts, id, lo == null ? null : lo.clone());
                copyEntry(activePages, id, old.activePages.get(id));
                copyEntry(activeSlots, id, old.activeSlots.get(id));
                copyEntry(wizardLevel, id, old.wizardLevel.get(id));
                copyEntry(wizardXp, id, old.wizardXp.get(id));
                copyEntry(manaCapNodes, id, old.manaCapNodes.get(id));
                copyEntry(manaRegenNodes, id, old.manaRegenNodes.get(id));
                copyEntry(cooldownNodes, id, old.cooldownNodes.get(id));
                copyEntry(powerNodes, id, old.powerNodes.get(id));
                copyEntry(currentMana, id, old.currentMana.get(id));
            }
            if (union.isEmpty()) {
                knownSpells.remove(id);
            } else {
                knownSpells.put(id, union);
            }
        }
        return oldIds.size();
    }

    /** Bu verideki oyuncu ilerlemesi {@code other}'dakinden ileri mi (level, eşitse XP)? */
    private boolean isAheadOf(ArcanumPlayerData other, UUID id) {
        int lvl = wizardLevel.getOrDefault(id, 0);
        int otherLvl = other.wizardLevel.getOrDefault(id, 0);
        if (lvl != otherLvl) {
            return lvl > otherLvl;
        }
        return wizardXp.getOrDefault(id, 0) > other.wizardXp.getOrDefault(id, 0);
    }

    private static <V> void copyEntry(Map<UUID, V> map, UUID id, V value) {
        if (value == null) {
            map.remove(id);
        } else {
            map.put(id, value);
        }
    }

    /**
     * Eski konum adayları (öncelik sırasıyla). 1.21.1 overworld deposu {@code <dünya>/data} idi;
     * 26.1 vanilla dosya düzelticisi (DimensionStorageFileFix) yalnız bilinen vanilla dosyalarını
     * taşır, mod dosyası yerinde kalır → 1. aday. Diğerleri savunma amaçlıdır (dosyayı
     * overworld boyut klasörüne taşıyan bir araç/sürüm olursa).
     */
    private static List<Path> legacyCandidates(Path dataDir, Path root) {
        String name = STORAGE_KEY + ".dat";
        Path overworldData = root.resolve("dimensions").resolve("minecraft").resolve("overworld").resolve("data");
        return List.of(
                dataDir.resolve(name),
                overworldData.resolve(name),
                overworldData.resolve("minecraft").resolve(name));
    }

    /** Vanilla gibi: gzip ise sıkıştırılmış, değilse düz NBT okur. */
    private static CompoundTag readNbtFile(Path file) throws IOException {
        try {
            return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException gzipFail) {
            CompoundTag plain = NbtIo.read(file);
            if (plain == null) {
                throw gzipFail;
            }
            return plain;
        }
    }

    /** 1.21.1 SavedData dosyası {@code {data:{players:...}, DataVersion:N}} biçimindedir. */
    private static CompoundTag unwrapLegacy(CompoundTag root) {
        if (root.get("data") instanceof CompoundTag inner) {
            return inner;
        }
        return root; // savunma: sarmalayıcısız (yalnız players içeren) dosya
    }

    /** Vanilla SavedDataStorage yazım biçimiyle aynı: {@code {data:<codec>, DataVersion}}, gzip. */
    private static void writeNow(ArcanumPlayerData data, Path file) throws IOException {
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("data", data.toTag());
        NbtUtils.addCurrentDataVersion(wrapper);
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        NbtIo.writeCompressed(wrapper, file);
    }

    /** Okunamayan yeni-biçim dosyayı ezilmeden önce yedekler (vanilla boş veriyle üzerine yazar). */
    private static void backupCorrupt(Path file) {
        Path backup = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            Arcanum.LOGGER.error("[Arcanum] Oyuncu verisi okunamadı ({}) — boş veriyle devam ediliyor; yedek: {}",
                    file, backup.getFileName());
        } catch (IOException | RuntimeException e) {
            Arcanum.LOGGER.error("[Arcanum] Oyuncu verisi okunamadı ({}) ve yedeklenemedi: {}", file, e.toString());
        }
    }

    /** Göç logu için: kayıtlı oyuncu sayısı (toTag ile aynı birleşim). */
    private int playerCount() {
        return allIds().size();
    }

    // ---- Bilinen büyüler (mevcut API) ----

    public boolean knows(Player player, String spellId) {
        Set<String> set = knownSpells.get(player.getUUID());
        return set != null && set.contains(spellId);
    }

    /** @return true → yeni öğrenildi, false → zaten biliniyordu. */
    public boolean learn(Player player, String spellId) {
        boolean added = knownSpells
                .computeIfAbsent(player.getUUID(), k -> new HashSet<>())
                .add(spellId);
        if (added) {
            setDirty();
        }
        return added;
    }

    public Set<String> knownOf(Player player) {
        return Set.copyOf(knownSpells.getOrDefault(player.getUUID(), Set.of()));
    }

    /** Bir oyuncunun TÜM öğrenilmiş büyülerini siler (hile/test komutu için). */
    public void forgetAll(Player player) {
        if (knownSpells.remove(player.getUUID()) != null) {
            setDirty();
        }
    }

    // ---- Loadout (dizilim) API — client/cast bunlara güvenir ----

    /** @return 12 uzunlukta KOPYA; hiç atanmamışsa hepsi -1. Pasifleştirilmiş
     *  (DISABLED) büyüye işaret eden slotlar -1 (boş) olarak DÖNER — istemci HUD/menü
     *  senkronu bu kopyayı aldığından disabled büyü her yerde boş slot görünür;
     *  kalıcı NBT'ye dokunulmaz (büyü ileride geri açılırsa slotlar geri gelir). */
    public int[] getLoadout(Player player) {
        int[] out = new int[TOTAL];
        int[] arr = loadouts.get(player.getUUID());
        if (arr != null && arr.length == TOTAL) {
            System.arraycopy(arr, 0, out, 0, TOTAL);
            for (int i = 0; i < TOTAL; i++) {
                if (com.arcanum.spell.ModSpells.isDisabled(out[i])) {
                    out[i] = -1;
                }
            }
        } else {
            Arrays.fill(out, -1);
        }
        return out;
    }

    /**
     * Slota büyü atar. {@code spellIndex} -1 → slotu temizle. page/slot aralık dışıysa
     * hiçbir şey yapmaz (sessiz). Bilinen-büyü doğrulaması ÇAĞIRAN tarafındadır
     * (bkz. AssignSlot receiver'ı).
     */
    public void setSlot(Player player, int page, int slot, int spellIndex) {
        if (page < 0 || page >= PAGES || slot < 0 || slot >= SLOTS) {
            return;
        }
        int[] arr = loadouts.computeIfAbsent(player.getUUID(), k -> newEmptyLoadout());
        // Pasifleştirilmiş büyü slota ATANAMAZ — temizleme olarak işlenir (bayat istemciye karşı).
        arr[page * SLOTS + slot] = com.arcanum.spell.ModSpells.isDisabled(spellIndex) ? -1 : spellIndex;
        setDirty();
    }

    public int getActivePage(Player player) {
        Integer p = activePages.get(player.getUUID());
        return p == null ? 0 : clampPage(p);
    }

    public int getActiveSlot(Player player) {
        Integer s = activeSlots.get(player.getUUID());
        return s == null ? 0 : clampSlot(s);
    }

    /** Aktif sayfa+slotu ayarlar (clamp: page 0..2, slot 0..3). */
    public void setActive(Player player, int page, int slot) {
        activePages.put(player.getUUID(), clampPage(page));
        activeSlots.put(player.getUUID(), clampSlot(slot));
        setDirty();
    }

    /** @return aktif slottaki global büyü index'i; boş/atanmamışsa -1. */
    public int activeSpellIndex(Player player) {
        int[] arr = loadouts.get(player.getUUID());
        if (arr == null || arr.length != TOTAL) {
            return -1;
        }
        int idx = getActivePage(player) * SLOTS + getActiveSlot(player);
        if (idx < 0 || idx >= TOTAL) {
            return -1;
        }
        // Pasifleştirilmiş büyü aktif slottaysa BOŞ slot sayılır (castlenemez).
        return com.arcanum.spell.ModSpells.isDisabled(arr[idx]) ? -1 : arr[idx];
    }

    // ---- 10. tur: büyücü level + skill ağacı + mana (formüller + state) ----

    /** Varsayılan seviye tavanı — istemci gösteriminin senkron-öncesi fallback'i. */
    public static final int MAX_LEVEL = 15;

    /** Aktif seviye tavanı — config {@code maxWizardLevel} (varsayılan {@link #MAX_LEVEL}). */
    public static int maxLevel() {
        return com.arcanum.config.ArcanumConfig.get().maxWizardLevel;
    }

    /** Seviye başına skill puanı — config {@code skillPointsPerLevel} (varsayılan 1). */
    public static int pointsPerLevel() {
        return com.arcanum.config.ArcanumConfig.get().skillPointsPerLevel;
    }
    public static final double BASE_MANA_CAP = 70.0;
    /** 1 mana / (BASE_REGEN_DIVISOR / effMult) tick. */
    public static final int BASE_REGEN_DIVISOR = 15;

    private static final int[] CAP_ADDS = {10, 15, 20, 20, 20, 20, 20};                 // 7 düğüm, +125 → cap 195 (ilk düğüm +10)
    private static final double[] REGEN_ADDS = {0.05, 0.10, 0.15, 0.20, 0.20, 0.20, 0.20}; // 7 düğüm, +1.10 → 2.10
    private static final double[] POWER_ADDS = {0.05, 0.10, 0.15, 0.20, 0.20, 0.20, 0.20}; // 7 düğüm, +1.10 → 2.10 (regen ile aynı desen)

    public static int xpToNext(int level) {
        // 15 seviyeye göre yeniden kalibre: L0→80 ... L14→780, toplam ~6450.
        return 80 + level * 50;
    }

    public int getLevel(Player p) {
        return wizardLevel.getOrDefault(p.getUUID(), 0);
    }

    public int getXp(Player p) {
        return wizardXp.getOrDefault(p.getUUID(), 0);
    }

    /** section: 0=kapasite 1=regen 2=cooldown 3=güç. */
    public int nodes(Player p, int section) {
        UUID u = p.getUUID();
        return switch (section) {
            case 0 -> manaCapNodes.getOrDefault(u, 0);
            case 1 -> manaRegenNodes.getOrDefault(u, 0);
            case 2 -> cooldownNodes.getOrDefault(u, 0);
            case 3 -> powerNodes.getOrDefault(u, 0);
            default -> 0;
        };
    }

    private static int maxNodes(int section) {
        return section == 2 ? 10 : 7;
    }

    public int spentPoints(Player p) {
        UUID u = p.getUUID();
        return manaCapNodes.getOrDefault(u, 0) + manaRegenNodes.getOrDefault(u, 0)
                + cooldownNodes.getOrDefault(u, 0) + powerNodes.getOrDefault(u, 0);
    }

    public int unspentPoints(Player p) {
        // Toplam puan = seviye × config puan/seviye. Config sonradan düşürülürse
        // toplam, harcanmışın altına inebilir → negatif GÖSTERME (0 dön); harcanmış
        // düğümlere dokunulmaz, yalnızca yeni satın alım yapılamaz.
        return Math.max(0, getLevel(p) * pointsPerLevel() - spentPoints(p));
    }

    // ---- Türetilmiş çarpanlar ----

    public double manaCapacity(Player p) {
        double bonus = 0;
        int n = manaCapNodes.getOrDefault(p.getUUID(), 0);
        for (int i = 0; i < n && i < CAP_ADDS.length; i++) {
            bonus += CAP_ADDS[i];
        }
        // Seçmen Şapka kafadayken +15 mana kapasitesi (bkz. SortingHatItem).
        // HUD manaCap'i syncMagicData ile bu değerden akar; ekipman değişimi
        // senkronu ManaRegen.tick'teki kapasite-değişim izleyicisinde.
        if (com.arcanum.item.SortingHatItem.isWearing(p)) {
            bonus += 15;
        }
        return BASE_MANA_CAP + bonus;
    }

    public double manaRegenMult(Player p) {
        double m = 1.27; // taban dolma hızı (tester: default regen +%5 → 1.21×1.05≈1.27)
        int n = manaRegenNodes.getOrDefault(p.getUUID(), 0);
        for (int i = 0; i < n && i < REGEN_ADDS.length; i++) {
            m += REGEN_ADDS[i];
        }
        return m;
    }

    public double cooldownMult(Player p) {
        int n = cooldownNodes.getOrDefault(p.getUUID(), 0);
        return Math.max(0.55, 1.0 - 0.045 * n); // düğüm başına −%4.5, 10 düğüm → −%45 (taban 0.55)
    }

    public double powerMult(Player p) {
        double m = 1.0;
        int n = powerNodes.getOrDefault(p.getUUID(), 0);
        for (int i = 0; i < n && i < POWER_ADDS.length; i++) {
            m += POWER_ADDS[i];
        }
        return m;
    }

    /** WandLockManager.powerOf / Spells.power'a katılacak +bonus (mult-1). */
    public double powerBonus(Player p) {
        return powerMult(p) - 1.0;
    }

    // ---- Mana state ----

    public int getManaCap(Player p) {
        return (int) Math.round(manaCapacity(p));
    }

    public int getMana(Player p) {
        int cap = getManaCap(p);
        int stored = currentMana.getOrDefault(p.getUUID(), cap); // yeni oyuncu full başlar
        if (stored > cap) {
            // Kapasite DÜŞTÜ (ör. Seçmen Şapka çıkarıldı) → saklanan manayı yeni
            // cap'e KALICI kırp; yoksa şapka tekrar takılınca "bedava mana" geri gelirdi.
            currentMana.put(p.getUUID(), cap);
            setDirty();
            return cap;
        }
        return stored;
    }

    /** @return true = yeterli mana vardı ve düşüldü. */
    public boolean spendMana(Player p, int amount) {
        int have = getMana(p);
        if (have < amount) {
            return false;
        }
        currentMana.put(p.getUUID(), have - amount);
        setDirty();
        return true;
    }

    public void setMana(Player p, int value) {
        int cap = getManaCap(p);
        currentMana.put(p.getUUID(), Math.max(0, Math.min(cap, value)));
        setDirty();
    }

    // ---- XP / level-up ----

    /** @return level atlandıysa yeni level, atlanmadıysa -1. */
    public int addXp(Player p, int amount) {
        if (amount <= 0) {
            return -1;
        }
        UUID u = p.getUUID();
        int max = maxLevel(); // config tavanı (varsayılan 15 → davranış birebir aynı)
        int lvl = wizardLevel.getOrDefault(u, 0);
        if (lvl >= max) {
            return -1;
        }
        int xp = wizardXp.getOrDefault(u, 0) + amount;
        boolean leveled = false;
        while (lvl < max && xp >= xpToNext(lvl)) {
            xp -= xpToNext(lvl);
            lvl++;
            leveled = true;
        }
        if (lvl >= max) {
            xp = 0;
        }
        wizardXp.put(u, xp);
        wizardLevel.put(u, lvl);
        setDirty();
        return leveled ? lvl : -1;
    }

    /** @return true = puan harcandı. */
    public boolean trySpendPoint(Player p, int section) {
        if (section < 0 || section > 3 || unspentPoints(p) <= 0) {
            return false;
        }
        UUID u = p.getUUID();
        int cur = nodes(p, section);
        if (cur >= maxNodes(section)) {
            return false;
        }
        switch (section) {
            case 0 -> manaCapNodes.put(u, cur + 1);
            case 1 -> manaRegenNodes.put(u, cur + 1);
            case 2 -> cooldownNodes.put(u, cur + 1);
            case 3 -> powerNodes.put(u, cur + 1);
            default -> { return false; }
        }
        setDirty();
        return true;
    }

    /** ÜCRETSİZ reset — tüm düğümleri sıfırla, puanlar iade (level korunur). */
    public void respec(Player p) {
        UUID u = p.getUUID();
        manaCapNodes.remove(u);
        manaRegenNodes.remove(u);
        cooldownNodes.remove(u);
        powerNodes.remove(u);
        setMana(p, getMana(p)); // kapasite düşebilir → clamp
        setDirty();
    }

    private static int[] newEmptyLoadout() {
        int[] arr = new int[TOTAL];
        Arrays.fill(arr, -1);
        return arr;
    }

    private static int clampPage(int page) {
        return Math.max(0, Math.min(PAGES - 1, page));
    }

    private static int clampSlot(int slot) {
        return Math.max(0, Math.min(SLOTS - 1, slot));
    }

    /** Diski okurken gelen diziyi tam 12 uzunluğa normalize eder (eksik → -1, fazla → kırp). */
    private static int[] normalizeLoadout(int[] raw) {
        int[] arr = newEmptyLoadout();
        if (raw != null) {
            System.arraycopy(raw, 0, arr, 0, Math.min(raw.length, TOTAL));
        }
        return arr;
    }

    // ---- NBT ----
    // 26.1: SavedData#save(CompoundTag, Provider) ve SavedData.Factory YOK → CODEC
    // (CompoundTag.CODEC.xmap(load, toTag)). İç biçim ve okuma kuralları 1.21.1 ile aynı:
    //  - 1.21.1 getTagType(key)==TAG_LIST/TAG_COMPOUND  → instanceof ListTag/CompoundTag
    //  - 1.21.1 getList(k, TAG_STRING) (yalnız string listesi) → yalnız StringTag öğeleri
    //  - 1.21.1 contains(k, TAG_INT) (TAM tip denetimi)  → instanceof IntTag (bkz. intOf);
    //    26.1 getInt()/getIntOr() her NumericTag'i kabul ettiği için KULLANILMADI.
    //  - "anahtar yok" ile "0" ayrımı korunur: anahtar yoksa map'e HİÇ konmaz (ör. mana
    //    yoksa yeni oyuncu gibi full başlar; mana=0 ise 0 kalır).

    private static ArcanumPlayerData load(CompoundTag tag) {
        ArcanumPlayerData data = new ArcanumPlayerData();
        data.legacyPending = tag.getBooleanOr(LEGACY_PENDING_KEY, false);
        CompoundTag players = tag.getCompoundOrEmpty("players");
        for (String key : players.keySet()) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue; // bozuk anahtar — atla
            }
            Tag entry = players.get(key);
            if (entry instanceof ListTag oldList) {
                // ESKİ FORMAT: players.<uuid> = ListTag<String> (yalnızca knownSpells).
                // Loadout alanları o dünyada yok → varsayılan (-1/0/0) kalır.
                Set<String> set = readStrings(oldList);
                if (!set.isEmpty()) {
                    data.knownSpells.put(id, set);
                }
            } else if (entry instanceof CompoundTag e) {
                // YENİ FORMAT: players.<uuid> = CompoundTag { known, loadout, activePage, activeSlot }
                Set<String> set = readStrings(e.getListOrEmpty("known"));
                if (!set.isEmpty()) {
                    data.knownSpells.put(id, set);
                }
                int[] rawLoadout = e.getIntArray("loadout").orElse(null); // yalnız IntArrayTag
                if (rawLoadout != null) {
                    data.loadouts.put(id, normalizeLoadout(rawLoadout));
                }
                Integer page = intOf(e, "activePage");
                if (page != null) {
                    data.activePages.put(id, clampPage(page));
                }
                Integer slot = intOf(e, "activeSlot");
                if (slot != null) {
                    data.activeSlots.put(id, clampSlot(slot));
                }
                // 10. tur alanları
                putIfPresent(data.wizardLevel, id, intOf(e, "wizardLevel"));
                putIfPresent(data.wizardXp, id, intOf(e, "wizardXp"));
                putIfPresent(data.manaCapNodes, id, intOf(e, "capNodes"));
                putIfPresent(data.manaRegenNodes, id, intOf(e, "regenNodes"));
                putIfPresent(data.cooldownNodes, id, intOf(e, "cdNodes"));
                putIfPresent(data.powerNodes, id, intOf(e, "pwrNodes"));
                putIfPresent(data.currentMana, id, intOf(e, "mana"));
            }
        }
        return data;
    }

    /** 1.21.1 {@code getList(key, TAG_STRING)} + {@code getAsString()} eşdeğeri: yalnız string öğeler. */
    private static Set<String> readStrings(ListTag list) {
        Set<String> set = new HashSet<>();
        for (Tag t : list) {
            if (t instanceof StringTag(String s)) {
                set.add(s);
            }
        }
        return set;
    }

    /** 1.21.1 {@code contains(key, TAG_INT) ? getInt(key) : yok} eşdeğeri — TAM IntTag tipi. */
    private static Integer intOf(CompoundTag e, String key) {
        return e.get(key) instanceof IntTag(int v) ? v : null;
    }

    private static void putIfPresent(Map<UUID, Integer> map, UUID id, Integer v) {
        if (v != null) {
            map.put(id, v);
        }
    }

    /** Kayıtlı herhangi bir alanı olan tüm oyuncular (1.21.1 save() birleşimiyle aynı sıra). */
    private Set<UUID> allIds() {
        Set<UUID> ids = new LinkedHashSet<>();
        ids.addAll(knownSpells.keySet());
        ids.addAll(loadouts.keySet());
        ids.addAll(activePages.keySet());
        ids.addAll(activeSlots.keySet());
        ids.addAll(wizardLevel.keySet());
        ids.addAll(wizardXp.keySet());
        ids.addAll(manaCapNodes.keySet());
        ids.addAll(manaRegenNodes.keySet());
        ids.addAll(cooldownNodes.keySet());
        ids.addAll(powerNodes.keySet());
        ids.addAll(currentMana.keySet());
        return ids;
    }

    /** 1.21.1 {@code save(CompoundTag, Provider)} gövdesi — yeni bir kök tag'e yazar. */
    private CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        CompoundTag players = new CompoundTag();
        for (UUID id : allIds()) {
            CompoundTag e = new CompoundTag();
            Set<String> known = knownSpells.get(id);
            if (known != null && !known.isEmpty()) {
                ListTag list = new ListTag();
                for (String s : known) {
                    list.add(StringTag.valueOf(s));
                }
                e.put("known", list);
            }
            int[] arr = loadouts.get(id);
            if (arr != null && arr.length == TOTAL) {
                e.putIntArray("loadout", arr);
            }
            Integer page = activePages.get(id);
            if (page != null && page != 0) {
                e.putInt("activePage", clampPage(page));
            }
            Integer slot = activeSlots.get(id);
            if (slot != null && slot != 0) {
                e.putInt("activeSlot", clampSlot(slot));
            }
            // 10. tur alanları (0 ise atla; mana 0 olsa da yaz → aksi halde full sanılır)
            putNonZero(e, "wizardLevel", wizardLevel.get(id));
            putNonZero(e, "wizardXp", wizardXp.get(id));
            putNonZero(e, "capNodes", manaCapNodes.get(id));
            putNonZero(e, "regenNodes", manaRegenNodes.get(id));
            putNonZero(e, "cdNodes", cooldownNodes.get(id));
            putNonZero(e, "pwrNodes", powerNodes.get(id));
            Integer mana = currentMana.get(id);
            if (mana != null) {
                e.putInt("mana", mana);
            }
            if (!e.isEmpty()) {
                players.put(id.toString(), e);
            }
        }
        tag.put("players", players);
        if (legacyPending) {
            tag.putBoolean(LEGACY_PENDING_KEY, true);
        }
        return tag;
    }

    private static void putNonZero(CompoundTag e, String key, Integer v) {
        if (v != null && v != 0) {
            e.putInt(key, v);
        }
    }
}
