package com.arcanum.util;

import com.arcanum.Arcanum;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.fixes.References;

/**
 * Arcanum varlık kimliklerini vanilla DataFixerUpper şemasına tanıtır (eski dünya göçü).
 *
 * <p>KÖK NEDEN (G4 düzeltme turu 2, denetim tur-1 MUSTFIX): vanilla {@code References.ENTITY} tipi
 * {@code and(ENTITY_EQUIPMENT, optionalFields("CustomName", taggedChoiceLazy("id", namespacedString(), entityTypes)))}
 * (V1460/V1458). DFU 10 {@code TaggedChoice.TaggedChoiceType} bilinmeyen anahtarda {@code "Unsupported key"} hatası
 * verir; {@code DataFixerUpper#update} de {@code readAndWrite} hatasında GİRDİYİ DEĞİŞTİRMEDEN döndürür. Bir chunk'ın
 * {@code Entities} listesinde tek bir {@code arcanum:*} varlık bulunması, listenin tamamının okunamamasına → o chunk'taki
 * BÜTÜN varlıkların (vanilla inek/zombi dahil) 3955→4790 düzeltmelerinin (ör. {@code generic.*} öznitelik öneki
 * kaldırma, 1.21.5 ekipman biçimi) atlanmasına yol açıyordu → yükte "Unknown registry key ... minecraft:generic.*"
 * ve öznitelik/ekipman kaybı (ölçüm: zenciborna, 20 chunk / 36 decode hatası, hatalı chunk'ların 6/6'sında Arcanum
 * varlığı, Arcanum'suz 40 chunk temiz).
 *
 * <p>Çözüm: {@code V1460#registerEntities} (V1460 sonrası TÜM şemaların varlık haritasını sıfırdan kuran tek kök —
 * sonraki şemalar {@code super.registerEntities} ile bu haritayı devralır) dönüşüne Arcanum kimlikleri eklenir
 * ({@code mixin.datafix.V1460EntitiesMixin}). Şablonlar vanilla eşdeğerleriyle aynı: canlılar/tekne/süpürge/patronus
 * {@code registerSimple} (vanilla {@code V1460.registerMob} = {@code registerSimple}; ekipman ENTITY_EQUIPMENT'ta,
 * seçimden bağımsız), sandıklı tekne vanilla {@code V4067.registerChestBoat} ile aynı {@code Items} listesi. Arcanum
 * varlıklarının kök 1.21.1 NBT'sinde başka eşya alanı yok ({@code addAdditionalSaveData}: Tier, Saddled, Variant, …).
 * Arcanum blok varlığı yok (BLOCK_ENTITY seçimi etkilenmez).
 */
public final class ArcanumDataFixerEntities {
    private ArcanumDataFixerEntities() {}

    /** {@code DSL.remainder} şablonlu kimlikler (kök 1.21.1 + 1.20.1 ile aynı 21 kimlik). */
    private static final List<String> SIMPLE = List.of(
            "acromantula", "arcanewood_boat", "basilisk", "bowtruckle", "broom", "death_eater", "dementor",
            "diabolica_dragon", "grindylow", "hippogriff", "kneazle", "mooncalf", "patronus", "phoenix", "snowy_owl",
            "thestral", "thunderbird", "troll", "unicorn", "werewolf", "wizard_trader");
    /** Vanilla sandıklı tekne şablonlu kimlik. */
    private static final String CHEST_BOAT = "arcanewood_chest_boat";

    /** {@code V1460#registerEntities} RETURN kancasından çağrılır (şema kurulumu; kayıtlara DOKUNMAZ). */
    public static void register(Schema schema, Map<String, Supplier<TypeTemplate>> map) {
        for (String path : SIMPLE) {
            String id = Arcanum.MODID + ":" + path;
            if (!map.containsKey(id)) {
                schema.registerSimple(map, id);
            }
        }
        String chest = Arcanum.MODID + ":" + CHEST_BOAT;
        if (!map.containsKey(chest)) {
            schema.register(map, chest, name -> DSL.optionalFields("Items", DSL.list(References.ITEM_STACK.in(schema))));
        }
    }

    /**
     * Kayıtlar bağlandıktan sonra (commonSetup): şemaya tanıtılan liste ile kayıtlı Arcanum varlık tipleri aynı mı?
     * Yeni bir varlık eklenip bu listeye yazılmazsa eski-dünya göçü o chunk'larda yine bozulur → yüksek sesle logla.
     */
    public static void verifyAgainstRegistry() {
        Set<String> registered = new TreeSet<>();
        for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            if (id.getNamespace().equals(Arcanum.MODID)) {
                registered.add(id.getPath());
            }
        }
        Set<String> known = new TreeSet<>(SIMPLE);
        known.add(CHEST_BOAT);
        if (!registered.equals(known)) {
            Set<String> missing = new TreeSet<>(registered);
            missing.removeAll(known);
            Set<String> stale = new TreeSet<>(known);
            stale.removeAll(registered);
            Arcanum.LOGGER.error("[Arcanum] DataFixer varlık listesi kayıtla uyuşmuyor: şemada eksik={} fazla={}"
                    + " — eski dünya göçünde bu varlıkları içeren chunk'lar düzeltilemez", missing, stale);
        }
    }
}
