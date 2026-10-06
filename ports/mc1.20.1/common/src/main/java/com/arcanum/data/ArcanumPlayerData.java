package com.arcanum.data;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Oyuncu başına kalıcı büyü bilgisi (öğrenilmiş büyüler + büyü dizilimi/loadout).
 * Overworld DataStorage'da saklanır — loader-bağımsız, dünya ile birlikte kaydedilir.
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

    public static ArcanumPlayerData get(MinecraftServer server) {
        // 1.20.1: SavedData.Factory + DataFixTypes parametresi HENÜZ YOK (1.20.2'de geldi) —
        // eski (loader, ctor, anahtar) imzası kullanılır. DataFixer bu yolda hiç devreye
        // girmediğinden 1.21.1'deki "DataFixTypes null → sessiz veri kaybı" tuzağı burada yok.
        return server.overworld().getDataStorage().computeIfAbsent(
                ArcanumPlayerData::load, ArcanumPlayerData::new, STORAGE_KEY);
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

    private static ArcanumPlayerData load(CompoundTag tag) {
        ArcanumPlayerData data = new ArcanumPlayerData();
        CompoundTag players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue; // bozuk anahtar — atla
            }
            byte type = players.getTagType(key);
            if (type == Tag.TAG_LIST) {
                // ESKİ FORMAT: players.<uuid> = ListTag<String> (yalnızca knownSpells).
                // Loadout alanları o dünyada yok → varsayılan (-1/0/0) kalır.
                Set<String> set = new HashSet<>();
                for (Tag t : players.getList(key, Tag.TAG_STRING)) {
                    set.add(t.getAsString());
                }
                if (!set.isEmpty()) {
                    data.knownSpells.put(id, set);
                }
            } else if (type == Tag.TAG_COMPOUND) {
                // YENİ FORMAT: players.<uuid> = CompoundTag { known, loadout, activePage, activeSlot }
                CompoundTag e = players.getCompound(key);
                Set<String> set = new HashSet<>();
                for (Tag t : e.getList("known", Tag.TAG_STRING)) {
                    set.add(t.getAsString());
                }
                if (!set.isEmpty()) {
                    data.knownSpells.put(id, set);
                }
                if (e.contains("loadout", Tag.TAG_INT_ARRAY)) {
                    data.loadouts.put(id, normalizeLoadout(e.getIntArray("loadout")));
                }
                if (e.contains("activePage", Tag.TAG_INT)) {
                    data.activePages.put(id, clampPage(e.getInt("activePage")));
                }
                if (e.contains("activeSlot", Tag.TAG_INT)) {
                    data.activeSlots.put(id, clampSlot(e.getInt("activeSlot")));
                }
                // 10. tur alanları
                if (e.contains("wizardLevel", Tag.TAG_INT)) data.wizardLevel.put(id, e.getInt("wizardLevel"));
                if (e.contains("wizardXp", Tag.TAG_INT)) data.wizardXp.put(id, e.getInt("wizardXp"));
                if (e.contains("capNodes", Tag.TAG_INT)) data.manaCapNodes.put(id, e.getInt("capNodes"));
                if (e.contains("regenNodes", Tag.TAG_INT)) data.manaRegenNodes.put(id, e.getInt("regenNodes"));
                if (e.contains("cdNodes", Tag.TAG_INT)) data.cooldownNodes.put(id, e.getInt("cdNodes"));
                if (e.contains("pwrNodes", Tag.TAG_INT)) data.powerNodes.put(id, e.getInt("pwrNodes"));
                if (e.contains("mana", Tag.TAG_INT)) data.currentMana.put(id, e.getInt("mana"));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
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
        for (UUID id : ids) {
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
        return tag;
    }

    private static void putNonZero(CompoundTag e, String key, Integer v) {
        if (v != null && v != 0) {
            e.putInt(key, v);
        }
    }
}
