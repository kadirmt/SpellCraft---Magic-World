package com.arcanum.fabric.client;

/**
 * İstemci tarafı BÜYÜCÜ verisi önbelleği ({@link ClientSpellData} deseni). Sunucudan
 * {@code MagicDataPayload} ile senkronlanır; ManaHud (hep görünür) + skill ağacı paneli
 * buradan okur.
 */
public final class ClientMagicData {
    private ClientMagicData() {}

    private static volatile int level;
    private static volatile int xp;
    private static volatile int xpToNext;
    private static volatile int mana;
    private static volatile int manaCap;
    private static volatile int[] nodes = new int[]{0, 0, 0, 0};
    // Config'den senkronlanan seviye tavanı + seviye başına puan (senkron-öncesi varsayılanlar).
    private static volatile int maxLevel = com.arcanum.data.ArcanumPlayerData.MAX_LEVEL;
    private static volatile int pointsPerLevel = 1;

    public static void set(int lvl, int x, int xN, int m, int cap, int[] n,
                           int maxLvl, int ppl) {
        level = lvl;
        xp = x;
        xpToNext = xN;
        mana = m;
        manaCap = cap;
        nodes = (n != null && n.length >= 4) ? n.clone() : new int[]{0, 0, 0, 0};
        maxLevel = Math.max(1, maxLvl);
        pointsPerLevel = Math.max(1, ppl);
    }

    public static void clear() {
        level = xp = xpToNext = mana = manaCap = 0;
        nodes = new int[]{0, 0, 0, 0};
        maxLevel = com.arcanum.data.ArcanumPlayerData.MAX_LEVEL;
        pointsPerLevel = 1;
    }

    public static int level() { return level; }
    public static int xp() { return xp; }
    public static int xpToNext() { return xpToNext; }
    public static int mana() { return mana; }
    public static int manaCap() { return manaCap; }
    public static int maxLevel() { return maxLevel; }

    public static int nodes(int s) {
        return (s >= 0 && s < 4) ? nodes[s] : 0;
    }

    public static int spent() {
        return nodes[0] + nodes[1] + nodes[2] + nodes[3];
    }

    public static int unspent() {
        // Sunucu hesabıyla birebir: toplam = level × puan/seviye, negatifse 0.
        return Math.max(0, level * pointsPerLevel - spent());
    }
}
