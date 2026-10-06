package com.arcanum.spell;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PER-SPELL (büyü başına) malzeme (reagent) haritası. Eskiden reagent seçimi
 * KADEME bazlıydı (tüm basic büyüler aynı 2 reagent'i kabul ediyordu) — bu
 * "sakat" bulunduğu için her büyü artık kendi SABİT reagent'ini ister
 * (bazıları eldeki item'lardan craftlanabilen combo reagent'lardır).
 *
 * <p>Item id'leri {@code arcanum:xxx} tam registry formunda tutulur; bu sınıf
 * ModItems'a derleme-zamanı bağımlı DEĞİLDİR (yalnızca id string'leriyle çalışır,
 * SpellGating ile aynı desen).
 */
public final class SpellReagents {
    private SpellReagents() {}

    /** spellId → gerekli reagent item id'si (arcanum:xxx). ModSpells.SPELLS ile birebir. */
    private static final Map<String, String> BY_SPELL = new LinkedHashMap<>();

    /** Haritada geçen TÜM 12 reagent item id'si (isReagent + creative/GUI için). */
    public static final String[] ALL_REAGENTS = {
            "arcanum:practice_chalk",
            "arcanum:spell_diagram",
            "arcanum:owl_charm",
            "arcanum:enchanted_honeycomb",
            "arcanum:crushed_amethyst",
            "arcanum:mooncalf_dust",
            "arcanum:storm_vial",
            "arcanum:acromantula_silk",
            "arcanum:phoenix_quill",
            "arcanum:basilisk_fang_shard",
            "arcanum:dementor_essence",
            "arcanum:horcrux_fragment",
    };

    static {
        // basic
        BY_SPELL.put("expelliarmus", "arcanum:practice_chalk");
        BY_SPELL.put("stupefy", "arcanum:spell_diagram");
        BY_SPELL.put("petrificus_totalus", "arcanum:owl_charm");
        BY_SPELL.put("incendio", "arcanum:enchanted_honeycomb");
        BY_SPELL.put("wingardium_leviosa", "arcanum:owl_charm");
        BY_SPELL.put("lumos", "arcanum:practice_chalk");
        BY_SPELL.put("protego", "arcanum:spell_diagram");
        BY_SPELL.put("accio", "arcanum:owl_charm");
        BY_SPELL.put("episkey", "arcanum:enchanted_honeycomb");
        BY_SPELL.put("flipendo", "arcanum:practice_chalk");
        BY_SPELL.put("diffindo", "arcanum:spell_diagram");
        // rictusempra PASİFLEŞTİRİLDİ (ModSpells.DISABLED) — reagent kaydı kaldırıldı.
        BY_SPELL.put("arresto_momentum", "arcanum:owl_charm");
        BY_SPELL.put("depulso", "arcanum:practice_chalk");
        BY_SPELL.put("everte_statum", "arcanum:spell_diagram");
        BY_SPELL.put("aguamenti", "arcanum:enchanted_honeycomb");
        BY_SPELL.put("homenum_revelio", "arcanum:owl_charm");
        // advanced
        BY_SPELL.put("glacius", "arcanum:crushed_amethyst");
        BY_SPELL.put("reducto", "arcanum:mooncalf_dust");
        BY_SPELL.put("bombarda", "arcanum:storm_vial");
        BY_SPELL.put("immobulus", "arcanum:acromantula_silk");
        BY_SPELL.put("confringo", "arcanum:phoenix_quill");
        BY_SPELL.put("expecto_patronum", "arcanum:mooncalf_dust");
        BY_SPELL.put("ventus", "arcanum:storm_vial");
        BY_SPELL.put("duro", "arcanum:crushed_amethyst");
        // dark
        BY_SPELL.put("sectumsempra", "arcanum:basilisk_fang_shard");
        BY_SPELL.put("fiendfyre", "arcanum:dementor_essence");
        // unforgivable
        BY_SPELL.put("avada_kedavra", "arcanum:horcrux_fragment");
        BY_SPELL.put("crucio", "arcanum:horcrux_fragment");
        BY_SPELL.put("imperio", "arcanum:horcrux_fragment");
        // sona eklenen büyüler
        BY_SPELL.put("protego_maxima", "arcanum:crushed_amethyst"); // advanced
        BY_SPELL.put("bombarda_maxima", "arcanum:dementor_essence"); // dark
        BY_SPELL.put("alohomora", "arcanum:practice_chalk"); // basic
        BY_SPELL.put("umbravolo", "arcanum:dementor_essence"); // dark — dementor dumanı tematik
        BY_SPELL.put("protego_diabolica", "arcanum:phoenix_quill"); // advanced — ateş temalı (mavi alev çemberi)
        BY_SPELL.put("vulnera_sanentur", "arcanum:phoenix_quill"); // advanced — anka tüyü: yeniden doğuş/şifa tematik
    }

    /**
     * Verilen büyü için gereken reagent item id'si (arcanum:xxx), bilinmeyen büyü
     * için null (gerçek UI'dan asla gelmemeli — çağıran null'ı fail sayabilir).
     */
    public static String reagentFor(String spellId) {
        return BY_SPELL.get(spellId);
    }

    /** Verilen item id'si (arcanum:xxx) haritadaki herhangi bir reagent değeri mi. */
    public static boolean isReagent(String itemId) {
        for (String id : ALL_REAGENTS) {
            if (id.equals(itemId)) {
                return true;
            }
        }
        return false;
    }
}
