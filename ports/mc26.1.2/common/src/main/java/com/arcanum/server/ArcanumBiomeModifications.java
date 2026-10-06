package com.arcanum.server;

import java.util.List;
import java.util.function.Supplier;

import com.arcanum.Arcanum;
import com.arcanum.registry.ModEntities;
import com.arcanum.worldgen.ArcanumRegion;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Biyom değişikliklerinin TEK kaynak tablosu — kök {@code ArcanumFabric} içindeki
 * {@code BiomeModifications.addSpawn} ×24 ve {@code ArcanumWorldgenFabric} içindeki
 * {@code BiomeModifications.addFeature} ×2 çağrısının birebir verisi (sıra, seçici,
 * kategori, ağırlık/min/max aynı).
 *
 * <p>Uygulayıcılar:
 * <ul>
 *   <li>Fabric: {@code com.arcanum.fabric.worldgen.ArcanumBiomeModificationsFabric} — seçiciler kökle BİREBİR
 *       ({@link Selector#foundInOverworld()} → {@code BiomeSelectors.foundInOverworld()},
 *       {@link Selector#keys} → {@code BiomeSelectors.includeByKey(...)}).</li>
 *   <li>Forge: tek özel BiomeModifier tipi {@code arcanum:table} ({@code data/arcanum/forge/biome_modifier/arcanum_table.json})
 *       aynı tabloyu uygular; "overworld" seçicisi Fabric'in {@code canGenerateIn(OVERWORLD)} tanımıyla aynı
 *       kümeyi üretir (overworld LevelStem'inin biyom kaynağının {@code possibleBiomes()}'u).</li>
 * </ul>
 *
 * <p>Spawn SIKLIĞI config çarpanları (22. tur, {@code SpawnGate}) burada DEĞİL — spawn placement
 * predicate'lerinde ({@code Arcanum.registerSpawnPlacements}, iki loader ortak) ve
 * {@code ArcanumSurfaceSpawner}'da uygulanır; bu tablo yalnız biyom havuzunu tanımlar.
 * 22. turda {@code #minecraft:is_forest} ağaç enjeksiyonu KALDIRILMIŞTI — burada da YOK.
 *
 * <p>Tablo bir METOTLA üretilir (statik alan değil): girdiler {@code RegistrySupplier} TUTAR, {@code .get()}
 * yalnız uygulama anında (sunucu açılışı) çağrılır.
 */
public final class ArcanumBiomeModifications {
    private ArcanumBiomeModifications() {}

    /**
     * Biyom seçici. {@code overworld=true} → Fabric {@code BiomeSelectors.foundInOverworld()} anlamı;
     * aksi halde {@code keys} listesindeki biyomlar ({@code includeByKey}).
     */
    public record Selector(boolean overworld, List<ResourceKey<Biome>> keys) {
        public static Selector foundInOverworld() {
            return new Selector(true, List.of());
        }

        @SafeVarargs
        public static Selector keys(ResourceKey<Biome>... keys) {
            return new Selector(false, List.of(keys));
        }
    }

    /** Bir {@code addSpawn(selector, category, type, weight, minCount, maxCount)} çağrısı. */
    public record SpawnEntry(Selector biomes, MobCategory category, Supplier<? extends EntityType<?>> type,
                             int weight, int minCount, int maxCount) {
    }

    /** Bir {@code addFeature(selector, step, placedFeatureKey)} çağrısı. */
    public record FeatureEntry(Selector biomes, GenerationStep.Decoration step, ResourceKey<PlacedFeature> feature) {
    }

    /** Arcane ruin placed feature anahtarı (data/arcanum/worldgen/placed_feature/arcane_ruin.json). */
    public static final ResourceKey<PlacedFeature> ARCANE_RUIN =
            ResourceKey.create(Registries.PLACED_FEATURE, Arcanum.id("arcane_ruin"));

    /** Ölümyiyen kampı placed feature anahtarı (data/arcanum/worldgen/placed_feature/death_eater_camp.json). */
    public static final ResourceKey<PlacedFeature> DEATH_EATER_CAMP =
            ResourceKey.create(Registries.PLACED_FEATURE, Arcanum.id("death_eater_camp"));

    // NOT: Unutulmuş Oda artık BiomeModifications ile eklenen bir feature DEĞİL;
    // gerçek bir Structure (data/arcanum/worldgen/structure + structure_set ile
    // vanilla structure yerleştirme sistemi tarafından üretilir). Bu yüzden burada
    // herhangi bir placed_feature enjeksiyonu yoktur.

    /** Kök ArcanumWorldgenFabric.register() — addFeature ×2 (aynı sıra). */
    public static List<FeatureEntry> features() {
        return List.of(
                // Kadim büyücü taş çemberi — tüm overworld biyomlarına eklenir; feature kendi
                // zeminini doğruladığı için okyanus/nehir ortasında zaten kendini iptal eder.
                new FeatureEntry(Selector.foundInOverworld(), GenerationStep.Decoration.SURFACE_STRUCTURES, ARCANE_RUIN),
                // Ölümyiyen kampı — tüm overworld biyomları; zemin/sıvı doğrulamasını
                // feature kendi içinde yaptığı için ek biyom filtresi gerekmez.
                new FeatureEntry(Selector.foundInOverworld(), GenerationStep.Decoration.SURFACE_STRUCTURES, DEATH_EATER_CAMP));
    }

    /** Kök ArcanumFabric.onInitialize — addSpawn ×24 (aynı sıra, aynı değerler). */
    public static List<SpawnEntry> spawns() {
        return List.of(
                // HogCraft portu — doğal spawn'lar HER YARATIK KENDİ uygun vanilla biyomuna
                // dağıtıldı, tek bir "büyücü biyomu"na sıkıştırılmadı. Ağırlıklar kullanıcı
                // geri bildirimi üzerine yükseltildi ("nadir olmamalı") — bkz. gloomwood.json.
                new SpawnEntry(Selector.keys(Biomes.DARK_FOREST, Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_HILLS),
                        MobCategory.MONSTER, ModEntities.TROLL, 66, 1, 1),
                // Troll ovalarda da doğsun (kullanıcı isteği: "Plains'te spawn olsun"). Troll ışık
                // şartsız (checkAnyLight) → Plains'te gündüz+gece görünür; weight orta (baskın olmasın).
                new SpawnEntry(Selector.keys(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW),
                        MobCategory.MONSTER, ModEntities.TROLL, 36, 1, 1),
                // Kuşlar (büyü reagent kaynakları: phoenix_quill / thunderbird_feather / owl_charm).
                // Tester "her biyomda çıksınlar + biraz daha sık" istedi → biyom kısıtı KALDIRILDI
                // (foundInOverworld = tüm overworld). Ağırlıklar KASITLI ılımlı (14/16/18): eski
                // 60-70'lik değerler CREATURE havuzunda vanilla hayvanları (koyun 12 / inek 8)
                // %80+ oranında ezip "her yer baykuş, çiftlik hayvanı yok" yapıyordu (adversarial
                // review bulgusu). 14-18 hâlâ koyundan sık → reagent bulmak kolay ama doğal denge korunur.
                new SpawnEntry(Selector.foundInOverworld(), MobCategory.CREATURE, ModEntities.PHOENIX, 20, 1, 1),
                new SpawnEntry(Selector.foundInOverworld(), MobCategory.CREATURE, ModEntities.THUNDERBIRD, 22, 1, 1),
                new SpawnEntry(Selector.foundInOverworld(), MobCategory.CREATURE, ModEntities.SNOWY_OWL, 24, 1, 2),
                // Mooncalf (mooncalf_dust reagent'ı) — eskiden YALNIZ gloomwood/arcanewood'da
                // doğuyordu, oyuncular pratikte hiç göremiyordu; artık tüm overworld'de mütevazı
                // ağırlıkla da doğar (+ ArcanumSurfaceSpawner mevcut chunk'larda aktif doğurur).
                new SpawnEntry(Selector.foundInOverworld(), MobCategory.CREATURE, ModEntities.MOONCALF, 10, 1, 2),
                new SpawnEntry(Selector.keys(Biomes.DARK_FOREST),
                        MobCategory.CREATURE, ModEntities.THESTRAL, 20, 1, 1),
                new SpawnEntry(Selector.keys(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.FLOWER_FOREST),
                        MobCategory.CREATURE, ModEntities.UNICORN, 22, 1, 1),
                // Ölümyiyen (Death Eater) yakın-temalı vanilla karanlık ormana da eklendi.
                // RUH EMİCİ (Dementor) ise YALNIZCA gloomwood'da doğar (kullanıcı isteği) —
                // vanilla biyomlara / overworld geneline EKLENMEZ. gloomwood dementor addSpawn'ı
                // aşağıda (weight 450, sürü 3-6) + gloomwood.json biyom-native listesinden gelir.
                new SpawnEntry(Selector.keys(Biomes.DARK_FOREST),
                        MobCategory.MONSTER, ModEntities.DEATH_EATER, 30, 1, 2),
                // Kurtadam — spawn denetimi bulgusu (kullanıcı: "hiç werewolf görmedim"):
                // vanilla biyomlarda HİÇ addSpawn kaydı yoktu (yalnız gloomwood + dark_forest
                // yüzey spawner'ı). Gece avcısı temasıyla karanlık orman + orman/tayga
                // biyomlarına eklendi; WerewolfEntity.canSpawn zaten geceye kilitli olduğundan
                // gündüz denemeleri elenir, gece MONSTER havuzunda 35 ağırlık makul sıklık verir.
                new SpawnEntry(Selector.keys(Biomes.DARK_FOREST, Biomes.FOREST,
                        Biomes.BIRCH_FOREST, Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA,
                        Biomes.OLD_GROWTH_SPRUCE_TAIGA),
                        MobCategory.MONSTER, ModEntities.WEREWOLF, 35, 1, 2),

                // Yeni yaratıklar — her biri kendi uygun vanilla biyomuna dağıtıldı.
                new SpawnEntry(Selector.keys(Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS,
                        Biomes.MEADOW, Biomes.WINDSWEPT_FOREST),
                        MobCategory.CREATURE, ModEntities.HIPPOGRIFF, 18, 1, 1),
                // NOT: River/Swamp/Frozen River kasıtlı olarak DIŞARIDA — bu biyomların su
                // sütunu genelde 1-2 blok derinliğinde (Frozen River'da üstelik buzla kaplı),
                // ama Grindylow'un IN_WATER + "pos VE pos.above() su" şartı üst üste 2 dolu
                // su bloğu gerektiriyor. Yalnızca gerçekten derin süregelen su sütunu olan
                // okyanus varyantlarına eklendi, aksi halde 3/4 biyom neredeyse hiç doğurmuyordu.
                // Ağırlık 1 + GrindylowEntity.NATURAL_RARITY ölçümle seçildi (yalnız karanlıkta, drowned ölçeğinde seyrek):
                // ports/_planning26/g9-grindylow/26.1.2-fabric/RAPOR.md
                new SpawnEntry(Selector.keys(Biomes.OCEAN, Biomes.DEEP_OCEAN,
                        Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN,
                        Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN),
                        MobCategory.MONSTER, ModEntities.GRINDYLOW, 1, 1, 1),
                new SpawnEntry(Selector.keys(Biomes.PLAINS, Biomes.FOREST, Biomes.SUNFLOWER_PLAINS),
                        MobCategory.CREATURE, ModEntities.KNEAZLE, 14, 1, 1),
                // Bowtruckle — spawn denetimi bulgusu: YALNIZ özel biyomlara (gloomwood /
                // arcanewood_grove) kayıtlıydı; o biyomları hiç bulamayan oyuncu pratikte hiç
                // göremiyordu. Vanilla orman biyomlarına mütevazı ağırlıkla eklendi
                // (+ ArcanumSurfaceSpawner girdisi mevcut chunk'larda aktif doğurur).
                new SpawnEntry(Selector.keys(Biomes.FOREST, Biomes.BIRCH_FOREST,
                        Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.FLOWER_FOREST, Biomes.DARK_FOREST),
                        MobCategory.CREATURE, ModEntities.BOWTRUCKLE, 14, 1, 2),

                // ---- ÖZEL BİYOM SPAWN'LARI (kök neden düzeltmesi) ----
                // Bu blok, gloomwood/arcanewood_grove için arcanum moblarının TEK kayıt
                // noktasıdır. Biyom JSON'larında (gloomwood.json, arcanewood_grove.json)
                // arcanum girdisi BİLEREK YOKTUR — oradaki "spawners" blokları yalnız vanilla
                // mobları taşır. Eskiden hem JSON'da hem burada tanımlıydılar; bu 2x duplikasyon
                // üretiyordu, JSON tarafı kaldırıldı. Ağırlık/min/max değiştirmek gerekirse
                // YALNIZ burayı düzenleyin; JSON'a arcanum girdisi geri EKLEMEYİN.
                // Gloomwood — CREATURE
                new SpawnEntry(Selector.keys(ArcanumRegion.GLOOMWOOD),
                        MobCategory.CREATURE, ModEntities.MOONCALF, 50, 3, 5),
                new SpawnEntry(Selector.keys(ArcanumRegion.GLOOMWOOD),
                        MobCategory.CREATURE, ModEntities.BOWTRUCKLE, 55, 2, 5),
                new SpawnEntry(Selector.keys(ArcanumRegion.GLOOMWOOD),
                        MobCategory.CREATURE, ModEntities.THESTRAL, 40, 1, 2),
                new SpawnEntry(Selector.keys(ArcanumRegion.GLOOMWOOD),
                        MobCategory.CREATURE, ModEntities.KNEAZLE, 35, 1, 2),
                // Gloomwood — MONSTER
                new SpawnEntry(Selector.keys(ArcanumRegion.GLOOMWOOD),
                        MobCategory.MONSTER, ModEntities.DEATH_EATER, 100, 2, 4),
                // Ruh Emici ağırlğı 450 → 150: 450, gloomwood canavar havuzunun ~%60'ıydı ve
                // biyomdaki her canavar seçimini pratikte tek türe kilitliyordu ("çok sık
                // doguyor" şikayeti). Toplam canavar YOĞUNLUĞUNU ağırlıklar belirlemez
                // (mob-cap belirler) — bu değişiklik KARIŞIMI düzeltir; gerçek yoğunluk tavanı
                // gloomwood.json'daki "spawn_costs" → arcanum:dementor girdisidir.
                new SpawnEntry(Selector.keys(ArcanumRegion.GLOOMWOOD),
                        MobCategory.MONSTER, ModEntities.DEMENTOR, 150, 3, 6),
                new SpawnEntry(Selector.keys(ArcanumRegion.GLOOMWOOD),
                        MobCategory.MONSTER, ModEntities.WEREWOLF, 90, 1, 2),
                new SpawnEntry(Selector.keys(ArcanumRegion.GLOOMWOOD),
                        MobCategory.MONSTER, ModEntities.ACROMANTULA, 90, 2, 4),
                // Arcanewood Grove — CREATURE
                new SpawnEntry(Selector.keys(ArcanumRegion.ARCANEWOOD_GROVE),
                        MobCategory.CREATURE, ModEntities.MOONCALF, 45, 1, 3),
                new SpawnEntry(Selector.keys(ArcanumRegion.ARCANEWOOD_GROVE),
                        MobCategory.CREATURE, ModEntities.BOWTRUCKLE, 45, 1, 3));
    }
}
