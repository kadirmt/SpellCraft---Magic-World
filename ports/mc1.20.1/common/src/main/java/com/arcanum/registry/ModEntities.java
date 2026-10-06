package com.arcanum.registry;

import com.arcanum.Arcanum;
import com.arcanum.entity.AcromantulaEntity;
import com.arcanum.entity.ArcanewoodBoatEntity;
import com.arcanum.entity.ArcanewoodChestBoatEntity;
import com.arcanum.entity.BasiliskEntity;
import com.arcanum.entity.BowtruckleEntity;
import com.arcanum.entity.BroomEntity;
import com.arcanum.entity.DeathEaterEntity;
import com.arcanum.entity.DementorEntity;
import com.arcanum.entity.FiendfyreDragonEntity;
import com.arcanum.entity.GrindylowEntity;
import com.arcanum.entity.HippogriffEntity;
import com.arcanum.entity.KneazleEntity;
import com.arcanum.entity.MooncalfEntity;
import com.arcanum.entity.PatronusEntity;
import com.arcanum.entity.PhoenixEntity;
import com.arcanum.entity.SnowyOwlEntity;
import com.arcanum.entity.ThestralEntity;
import com.arcanum.entity.ThunderbirdEntity;
import com.arcanum.entity.TrollEntity;
import com.arcanum.entity.UnicornEntity;
import com.arcanum.entity.WerewolfEntity;
import com.arcanum.entity.WizardTraderEntity;
import dev.architectury.registry.level.entity.SpawnPlacementsRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Özel yaratıklar (EntityType kayıtları). Attribute kaydı Fabric tarafında.
 */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Arcanum.MODID, Registries.ENTITY_TYPE);

    /** Ölümyiyen — büyü atan kara büyücü (kamp + gloomwood gecesi). */
    public static final RegistrySupplier<EntityType<DeathEaterEntity>> DEATH_EATER =
            ENTITIES.register("death_eater", () -> EntityType.Builder.of(DeathEaterEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("death_eater"));

    /** Ruh Emici — süzülen karanlık varlık (gloomwood gecesi). */
    public static final RegistrySupplier<EntityType<DementorEntity>> DEMENTOR =
            ENTITIES.register("dementor", () -> EntityType.Builder.of(DementorEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 2.4F)
                    .clientTrackingRange(12)
                    .build("dementor"));

    /** Bowtruckle — minik dal bekçisi (pasif). */
    public static final RegistrySupplier<EntityType<BowtruckleEntity>> BOWTRUCKLE =
            ENTITIES.register("bowtruckle", () -> EntityType.Builder.of(BowtruckleEntity::new, MobCategory.CREATURE)
                    .sized(0.35F, 0.65F)
                    .clientTrackingRange(8)
                    .build("bowtruckle"));

    /** Mooncalf — ürkek ay danası (pasif). */
    public static final RegistrySupplier<EntityType<MooncalfEntity>> MOONCALF =
            ENTITIES.register("mooncalf", () -> EntityType.Builder.of(MooncalfEntity::new, MobCategory.CREATURE)
                    .sized(0.8F, 1.0F)
                    .clientTrackingRange(10)
                    .build("mooncalf"));

    /** Uçan süpürge — binilebilir araç (doğal spawn yok). */
    public static final RegistrySupplier<EntityType<BroomEntity>> BROOM =
            ENTITIES.register("broom", () -> EntityType.Builder.of(BroomEntity::new, MobCategory.MISC)
                    .sized(0.9F, 0.5F)
                    .clientTrackingRange(10)
                    .build("broom"));

    /** Troll — devasa saldırgan dağ trolü (HogCraft portu). */
    public static final RegistrySupplier<EntityType<TrollEntity>> TROLL =
            ENTITIES.register("troll", () -> EntityType.Builder.of(TrollEntity::new, MobCategory.MONSTER)
                    .sized(1.2F, 2.8F)
                    .clientTrackingRange(10)
                    .build("troll"));

    /** Phoenix — evcilleştirilebilir uçan anka kuşu, ateşe bağışık (HogCraft portu). */
    public static final RegistrySupplier<EntityType<PhoenixEntity>> PHOENIX =
            ENTITIES.register("phoenix", () -> EntityType.Builder.of(PhoenixEntity::new, MobCategory.CREATURE)
                    .sized(0.5F, 0.9F)
                    .clientTrackingRange(10)
                    .build("phoenix"));

    /** Thunderbird — evcilleştirilebilir uçan fırtına kuşu (HogCraft portu). */
    public static final RegistrySupplier<EntityType<ThunderbirdEntity>> THUNDERBIRD =
            ENTITIES.register("thunderbird", () -> EntityType.Builder.of(ThunderbirdEntity::new, MobCategory.CREATURE)
                    .sized(0.9F, 1.3F)
                    .clientTrackingRange(10)
                    .build("thunderbird"));

    /** Kar Baykuşu — evcilleştirilebilir uçan baykuş, gözleri karanlıkta parlar (HogCraft portu). */
    public static final RegistrySupplier<EntityType<SnowyOwlEntity>> SNOWY_OWL =
            ENTITIES.register("snowy_owl", () -> EntityType.Builder.of(SnowyOwlEntity::new, MobCategory.CREATURE)
                    .sized(0.5F, 0.7F)
                    .clientTrackingRange(8)
                    .build("snowy_owl"));

    /** Thestral — evcilleştirilebilir, uçabilen kanatlı binek (HogCraft portu). */
    public static final RegistrySupplier<EntityType<ThestralEntity>> THESTRAL =
            ENTITIES.register("thestral", () -> EntityType.Builder.of(ThestralEntity::new, MobCategory.CREATURE)
                    .sized(1.3964844F, 1.6F)
                    .clientTrackingRange(10)
                    .build("thestral"));

    /** Unicorn — evcilleştirilebilir, eyerle binilebilen at-benzeri yaratık (HogCraft portu). */
    public static final RegistrySupplier<EntityType<UnicornEntity>> UNICORN =
            ENTITIES.register("unicorn", () -> EntityType.Builder.of(UnicornEntity::new, MobCategory.CREATURE)
                    .sized(1.3964844F, 1.6F)
                    .clientTrackingRange(10)
                    .build("unicorn"));

    /** Arcanewood botu — vanilla Boat.Type genişletilemediği için ayrı EntityType. */
    public static final RegistrySupplier<EntityType<ArcanewoodBoatEntity>> ARCANEWOOD_BOAT =
            ENTITIES.register("arcanewood_boat", () -> EntityType.Builder.of(ArcanewoodBoatEntity::new, MobCategory.MISC)
                    .sized(1.375F, 0.5625F)
                    .clientTrackingRange(10)
                    .build("arcanewood_boat"));

    /** Arcanewood sandıklı botu. */
    public static final RegistrySupplier<EntityType<ArcanewoodChestBoatEntity>> ARCANEWOOD_CHEST_BOAT =
            ENTITIES.register("arcanewood_chest_boat", () -> EntityType.Builder.of(ArcanewoodChestBoatEntity::new, MobCategory.MISC)
                    .sized(1.375F, 0.5625F)
                    .clientTrackingRange(10)
                    .build("arcanewood_chest_boat"));

    /** Hippogriff — gururlu, yarı kartal yarı at yaratığı; eğilerek saygı gösterilirse evcilleştirilebilir ve binilebilir. */
    public static final RegistrySupplier<EntityType<HippogriffEntity>> HIPPOGRIFF =
            ENTITIES.register("hippogriff", () -> EntityType.Builder.of(HippogriffEntity::new, MobCategory.CREATURE)
                    .sized(1.5F, 1.8F)
                    .clientTrackingRange(10)
                    .build("hippogriff"));

    /** Acromantula — Aragog'un soyundan dev zehirli örümcek. */
    public static final RegistrySupplier<EntityType<AcromantulaEntity>> ACROMANTULA =
            ENTITIES.register("acromantula", () -> EntityType.Builder.of(AcromantulaEntity::new, MobCategory.MONSTER)
                    .sized(1.8F, 1.4F)
                    .clientTrackingRange(10)
                    .build("acromantula"));

    /** Kurtadam — yalnızca geceleri doğan vahşi yaratık. */
    public static final RegistrySupplier<EntityType<WerewolfEntity>> WEREWOLF =
            ENTITIES.register("werewolf", () -> EntityType.Builder.of(WerewolfEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 2.1F)
                    .clientTrackingRange(10)
                    .build("werewolf"));

    /** Grindylow — su altında yaşayan saldırgan küçük yaratık. */
    public static final RegistrySupplier<EntityType<GrindylowEntity>> GRINDYLOW =
            ENTITIES.register("grindylow", () -> EntityType.Builder.of(GrindylowEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 0.9F)
                    .clientTrackingRange(8)
                    .build("grindylow"));

    /** Kneazle — kedimsi, sadık evcil yaratık. */
    public static final RegistrySupplier<EntityType<KneazleEntity>> KNEAZLE =
            ENTITIES.register("kneazle", () -> EntityType.Builder.of(KneazleEntity::new, MobCategory.CREATURE)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(8)
                    .build("kneazle"));

    /**
     * Basilisk — devasa BOSS yılan. Yalnızca zindan feature'ı addFreshEntity ile
     * doğurur (SpawnPlacements kaydı YOK — doğal spawn yok). Büyük gövde + geniş
     * takip menzili için clientTrackingRange yükseltildi.
     */
    public static final RegistrySupplier<EntityType<BasiliskEntity>> BASILISK =
            ENTITIES.register("basilisk", () -> EntityType.Builder.of(BasiliskEntity::new, MobCategory.MONSTER)
                    .sized(3.0F, 4.0F)
                    .clientTrackingRange(16)
                    .build("basilisk"));

    /** Cisimleşmiş Patronus — Expecto Patronum'un çağırdığı ruhani yoldaş (doğal spawn yok). */
    public static final RegistrySupplier<EntityType<PatronusEntity>> PATRONUS =
            ENTITIES.register("patronus", () -> EntityType.Builder.of(PatronusEntity::new, MobCategory.MISC)
                    .sized(0.9F, 1.2F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build("patronus"));

    /** Hogsmeade büyücü tüccarı — sabit zümrüt ekonomili NPC (yapılara yerleştirilir). */
    public static final RegistrySupplier<EntityType<WizardTraderEntity>> WIZARD_TRADER =
            ENTITIES.register("wizard_trader", () -> EntityType.Builder.of(WizardTraderEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.9F)
                    .clientTrackingRange(10)
                    .build("wizard_trader"));

    /**
     * Protego Diabolica'nın MAVİ ALEV EJDERHASI — salt görsel tezahür.
     * <p>MISC kategorisi BİLİNÇLİ: Mob tabanlı olsa
     * da doğal spawn'ı yok; MONSTER seçilseydi global spawn tavanına girerdi.
     * {@code SpawnPlacements} kaydı YOK, {@code noSummon()} → /summon ve spawn egg ile
     * elle çağrılamaz (yalnız {@code DiabolicaDragonManager} doğurur; sahipsiz doğan
     * bir ejderha zaten ~24 tick içinde kendini siler).
     * <p>{@code sized} model hacminden küçük tutuldu (çarpışma zaten kapalı) —
     * {@code noCulling} sayesinde ~4 bloklık gövde yine de kırpılmaz.
     * <p>KAYIT SIRASI: listenin SONUNA eklendi — mevcut entity'lerin ağ id indeksleri
     * kaymasın (kaydedilmiş dünyalarla uyum).
     */
    public static final RegistrySupplier<EntityType<FiendfyreDragonEntity>> DIABOLICA_DRAGON =
            ENTITIES.register("diabolica_dragon", () -> EntityType.Builder.of(FiendfyreDragonEntity::new, MobCategory.MISC)
                    .sized(2.0F, 2.0F)
                    .fireImmune()
                    .noSummon()
                    .clientTrackingRange(16)
                    .build("diabolica_dragon"));

    private ModEntities() {}

    public static void init() {
        ENTITIES.register();
    }

    /** Doğal spawn yerleşim kuralları (Arcanum.init sonunda çağrılır). */
    public static void registerSpawnPlacements() {
        // KULLANICI CONFIG'İ (oyuncu şikayeti: "the creatures are spawning quite
        // frequently"): her predicate SpawnGate.gated ile sarmalandı — doğal
        // (NATURAL/CHUNK_GENERATION) spawn denemeleri mob-başına çarpanla süzülür.
        // Reagent veren yaratıklar varsayılan 1.0, diğerleri 0.85 (−%15).
        // Yapı spawn'ı / spawn yumurtası / summon / üreme ETKİLENMEZ.
        // NOT: Monster.checkMonsterSpawnRules yerine checkAnyLightMonsterSpawnRules
        // kullanılıyor — Ölümyiyen/Ruh Emici HP lore'unda "yalnız karanlıkta"
        // yaratıklar değil; ayrıca gloomwood'un kendi glow_lichen dekoru
        // (blok ışığı yayıyor) + gündüz gökyüzü ışığı normal karanlık şartını
        // sık sık boşa çıkarıyordu, bu da doğal spawn'ı beklenenden çok
        // seyrekleştiriyordu.
        SpawnPlacementsRegistry.register(DEATH_EATER,
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.deathEaterSpawnMult,
                        Monster::checkAnyLightMonsterSpawnRules));
        SpawnPlacementsRegistry.register(DEMENTOR,
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.dementorSpawnMult,
                        Monster::checkAnyLightMonsterSpawnRules));
        SpawnPlacementsRegistry.register(BOWTRUCKLE,
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.bowtruckleSpawnMult,
                        com.arcanum.entity.BowtruckleEntity::canSpawn));
        SpawnPlacementsRegistry.register(MOONCALF,
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.mooncalfSpawnMult,
                        com.arcanum.entity.MooncalfEntity::canSpawn));
        SpawnPlacementsRegistry.register(TROLL,
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.trollSpawnMult,
                        Monster::checkAnyLightMonsterSpawnRules));
        SpawnPlacementsRegistry.register(PHOENIX,
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.phoenixSpawnMult,
                        com.arcanum.entity.PhoenixEntity::canSpawn));
        SpawnPlacementsRegistry.register(THUNDERBIRD,
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.thunderbirdSpawnMult,
                        com.arcanum.entity.ThunderbirdEntity::canSpawn));
        SpawnPlacementsRegistry.register(SNOWY_OWL,
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.snowyOwlSpawnMult,
                        com.arcanum.entity.SnowyOwlEntity::canSpawn));
        SpawnPlacementsRegistry.register(THESTRAL,
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.thestralSpawnMult,
                        com.arcanum.entity.ThestralEntity::canSpawn));
        SpawnPlacementsRegistry.register(UNICORN,
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.unicornSpawnMult,
                        Animal::checkAnimalSpawnRules));
        SpawnPlacementsRegistry.register(HIPPOGRIFF,
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.hippogriffSpawnMult,
                        Animal::checkAnimalSpawnRules));
        SpawnPlacementsRegistry.register(ACROMANTULA,
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.acromantulaSpawnMult,
                        Monster::checkAnyLightMonsterSpawnRules));
        SpawnPlacementsRegistry.register(WEREWOLF,
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.werewolfSpawnMult,
                        com.arcanum.entity.WerewolfEntity::canSpawn));
        SpawnPlacementsRegistry.register(GRINDYLOW,
                SpawnPlacements.Type.IN_WATER,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.grindylowSpawnMult,
                        com.arcanum.entity.GrindylowEntity::canSpawn));
        SpawnPlacementsRegistry.register(KNEAZLE,
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.kneazleSpawnMult,
                        com.arcanum.entity.KneazleEntity::canSpawn));
    }
}
