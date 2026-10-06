package com.arcanum.spell;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Kademe (tier) yardımcıları — "basic tamamlandı mı" gibi türetilmiş bilgiyi
 * TEK bir yerden hesaplar. Kalıcı ekstra bir bayrak TUTMAZ: her şey
 * {@code ArcanumPlayerData}'nın zaten sakladığı bilinen-büyü kümesinden ve
 * {@code ModSpells.SPELLS}'ten türetilir — senkron bozulması imkânsızdır
 * (bkz. Architect Spec §2).
 */
public final class SpellTiers {
    /** Kanonik kademe sırası — basic en alt, unforgivable en üst. */
    public static final String[] TIER_ORDER = {"basic", "advanced", "dark", "unforgivable"};

    private SpellTiers() {}

    /** Verilen kademedeki tüm büyüler (ModSpells.SPELLS sırası korunur).
     *  DISABLED büyüler listelenmez — G menüsü ve tüm UI listeleri buradan
     *  beslendiği için pasifleştirilmiş büyü otomatik görünmez olur. */
    public static List<Spell> allOfTier(String tier) {
        List<Spell> out = new ArrayList<>();
        for (Spell s : ModSpells.SPELLS) {
            if (tier.equals(s.tier()) && !ModSpells.isDisabled(s.id())) {
                out.add(s);
            }
        }
        return out;
    }

    /** @return tier'daki HER büyü known kümesinde mi (boş kademe de "true" sayılır).
     *  DISABLED büyüler ATLANIR — yoksa öğrenilemez hale gelen pasif büyü (rictusempra)
     *  basic kademesini sonsuza dek "eksik" bırakır ve advanced KALICI kilitlenirdi. */
    public static boolean hasAllOfTier(Set<String> known, String tier) {
        for (Spell s : ModSpells.SPELLS) {
            if (tier.equals(s.tier()) && !ModSpells.isDisabled(s.id()) && !known.contains(s.id())) {
                return false;
            }
        }
        return true;
    }

    /** @return tier'ın hemen altındaki kademe, basic için null (ön koşulu yok). */
    public static String prevTier(String tier) {
        int idx = indexOf(tier);
        return idx <= 0 ? null : TIER_ORDER[idx - 1];
    }

    /** @return tier'ın hemen üstündeki kademe, unforgivable için null. */
    public static String nextTier(String tier) {
        int idx = indexOf(tier);
        return (idx < 0 || idx >= TIER_ORDER.length - 1) ? null : TIER_ORDER[idx + 1];
    }

    private static int indexOf(String tier) {
        for (int i = 0; i < TIER_ORDER.length; i++) {
            if (TIER_ORDER[i].equals(tier)) {
                return i;
            }
        }
        return -1;
    }
}
