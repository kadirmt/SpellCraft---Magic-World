package com.arcanum.forge.client.state;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import com.arcanum.spell.Spell;

/**
 * İstemci tarafı büyü önbelleği. (FORGE 1.21.1 PORTU — kök fabric
 * {@code com.arcanum.fabric.client.ClientSpellData} birebir; loader-bağımsız.)
 * <ul>
 *   <li>Bilinen-büyü kümesi (KnownSpellsPayload) — menü kilit/açık görünümü.</li>
 *   <li>Büyü dizilimi: 12 slot (3 sayfa × 4 slot) + aktif sayfa/slot
 *       (SpellLoadoutPayload) — sol-alt HUD ve menü bunu okur, cast bunu kullanır.</li>
 * </ul>
 * Bağlantı kopunca ({@link #clear()}) her ikisi de varsayılana döner.
 */
public final class ClientSpellData {
    /** Dizilim sabitleri — sunucu/HUD/menü ile ortak sözleşme. */
    public static final int PAGES = 3;
    public static final int SLOTS = 4;
    public static final int TOTAL = PAGES * SLOTS; // 12

    private static volatile Set<String> known = Set.of();

    // Büyü dizilimi (12 slot, boş = -1) + aktif sayfa/slot. setLoadout her seferinde
    // yeni bir dizi yayınlar (mutasyon yok) — volatile referans görünürlüğü yeterli.
    private static volatile int[] loadout = emptyLoadout();
    private static volatile int activePage = 0;
    private static volatile int activeSlot = 0;

    private ClientSpellData() {}

    private static int[] emptyLoadout() {
        int[] a = new int[TOTAL];
        Arrays.fill(a, -1);
        return a;
    }

    public static void set(Collection<String> ids) {
        known = Set.copyOf(ids);
    }

    public static void clear() {
        known = Set.of();
        loadout = emptyLoadout();
        activePage = 0;
        activeSlot = 0;
    }

    public static boolean knows(Spell spell) {
        return known.contains(spell.id());
    }

    /** Ham bilinen-büyü id kümesi — SpellTiers.hasAllOfTier gibi küme-tabanlı sorgular için. */
    public static Set<String> knownSet() {
        return known;
    }

    // --- Büyü dizilimi (loadout) ---

    /** S2C alıcısı çağırır: sunucudan gelen 12-slot dizilim + aktif sayfa/slot ile önbelleği tazeler. */
    public static void setLoadout(int[] newLoadout, int page, int slot) {
        int[] copy = emptyLoadout();
        if (newLoadout != null) {
            System.arraycopy(newLoadout, 0, copy, 0, Math.min(newLoadout.length, TOTAL));
        }
        loadout = copy;
        activePage = Math.floorMod(page, PAGES);
        activeSlot = Math.floorMod(slot, SLOTS);
    }

    /** Verilen sayfa+slot'taki global büyü index'i; boş ya da aralık dışı ise -1. */
    public static int loadoutSlot(int page, int slot) {
        if (page < 0 || page >= PAGES || slot < 0 || slot >= SLOTS) {
            return -1;
        }
        return loadout[page * SLOTS + slot];
    }

    public static int activePage() {
        return activePage;
    }

    public static int activeSlot() {
        return activeSlot;
    }

    /** Aktif slottaki global büyü index'i, boş ise -1. */
    public static int activeSpellIndex() {
        return loadoutSlot(activePage, activeSlot);
    }
}
