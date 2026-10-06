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
import com.arcanum.platform.Platform;
import com.arcanum.platform.registry.DeferredRegister;
import com.arcanum.platform.registry.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Özel yaratıklar (EntityType kayıtları).
 *
 * <p>26.x: {@code EntityType.Builder.build(ResourceKey)} (kayıt anahtarı fabrikaya gelir). Attribute ve
 * doğal spawn yerleşimi Platform SPI kuyruklarıyla ({@link #registerAttributes()},
 * {@link #registerSpawnPlacements()} — ikisi de {@code Arcanum.init()} sonunda çağrılır).
 * <p>{@code notInPeaceful()}: 1.21.1'de {@code Monster} alt sınıfları barışçıl zorlukta
 * {@code shouldDespawnInPeaceful()=true} ile siliniyordu; 26.x'te bu karar EntityType bayrağına taşındı.
 * Aynı davranış için Monster tabanlı 7 tipe (Ölümyiyen, Ruh Emici, Troll, Acromantula, Kurtadam,
 * Grindylow, Basilisk) bayrak eklendi.
 */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Arcanum.MODID, Registries.ENTITY_TYPE);

    /** Ölümyiyen — büyü atan kara büyücü (kamp + gloomwood gecesi). */
    public static final RegistrySupplier<EntityType<DeathEaterEntity>> DEATH_EATER =
            ENTITIES.register("death_eater", key -> EntityType.Builder.of(DeathEaterEntity::new, MobCategory.MONSTER)
                    .notInPeaceful() // 26.x: 1.21.1 Monster.shouldDespawnInPeaceful paritesi
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build(key));

    /** Ruh Emici — süzülen karanlık varlık (gloomwood gecesi). */
    public static final RegistrySupplier<EntityType<DementorEntity>> DEMENTOR =
            ENTITIES.register("dementor", key -> EntityType.Builder.of(DementorEntity::new, MobCategory.MONSTER)
                    .notInPeaceful() // 26.x: 1.21.1 Monster.shouldDespawnInPeaceful paritesi
                    .sized(0.7F, 2.4F)
                    .clientTrackingRange(12)
                    .build(key));

    /** Bowtruckle — minik dal bekçisi (pasif). */
    public static final RegistrySupplier<EntityType<BowtruckleEntity>> BOWTRUCKLE =
            ENTITIES.register("bowtruckle", key -> EntityType.Builder.of(BowtruckleEntity::new, MobCategory.CREATURE)
                    .sized(0.35F, 0.65F)
                    .clientTrackingRange(8)
                    .build(key));

    /** Mooncalf — ürkek ay danası (pasif). */
    public static final RegistrySupplier<EntityType<MooncalfEntity>> MOONCALF =
            ENTITIES.register("mooncalf", key -> EntityType.Builder.of(MooncalfEntity::new, MobCategory.CREATURE)
                    .sized(0.8F, 1.0F)
                    .clientTrackingRange(10)
                    .build(key));

    /** Uçan süpürge — binilebilir araç (doğal spawn yok). */
    public static final RegistrySupplier<EntityType<BroomEntity>> BROOM =
            ENTITIES.register("broom", key -> EntityType.Builder.of(BroomEntity::new, MobCategory.MISC)
                    .sized(0.9F, 0.5F)
                    .clientTrackingRange(10)
                    .build(key));

    /** Troll — devasa saldırgan dağ trolü (HogCraft portu). */
    public static final RegistrySupplier<EntityType<TrollEntity>> TROLL =
            ENTITIES.register("troll", key -> EntityType.Builder.of(TrollEntity::new, MobCategory.MONSTER)
                    .notInPeaceful() // 26.x: 1.21.1 Monster.shouldDespawnInPeaceful paritesi
                    .sized(1.2F, 2.8F)
                    .clientTrackingRange(10)
                    .build(key));

    /** Phoenix — evcilleştirilebilir uçan anka kuşu, ateşe bağışık (HogCraft portu). */
    public static final RegistrySupplier<EntityType<PhoenixEntity>> PHOENIX =
            ENTITIES.register("phoenix", key -> EntityType.Builder.of(PhoenixEntity::new, MobCategory.CREATURE)
                    .sized(0.5F, 0.9F)
                    .clientTrackingRange(10)
                    .build(key));

    /** Thunderbird — evcilleştirilebilir uçan fırtına kuşu (HogCraft portu). */
    public static final RegistrySupplier<EntityType<ThunderbirdEntity>> THUNDERBIRD =
            ENTITIES.register("thunderbird", key -> EntityType.Builder.of(ThunderbirdEntity::new, MobCategory.CREATURE)
                    .sized(0.9F, 1.3F)
                    .clientTrackingRange(10)
                    .build(key));

    /** Kar Baykuşu — evcilleştirilebilir uçan baykuş, gözleri karanlıkta parlar (HogCraft portu). */
    public static final RegistrySupplier<EntityType<SnowyOwlEntity>> SNOWY_OWL =
            ENTITIES.register("snowy_owl", key -> EntityType.Builder.of(SnowyOwlEntity::new, MobCategory.CREATURE)
                    .sized(0.5F, 0.7F)
                    .clientTrackingRange(8)
                    .build(key));

    /** Thestral — evcilleştirilebilir, uçabilen kanatlı binek (HogCraft portu). */
    public static final RegistrySupplier<EntityType<ThestralEntity>> THESTRAL =
            ENTITIES.register("thestral", key -> EntityType.Builder.of(ThestralEntity::new, MobCategory.CREATURE)
                    .sized(1.3964844F, 1.6F)
                    .clientTrackingRange(10)
                    .build(key));

    /** Unicorn — evcilleştirilebilir, eyerle binilebilen at-benzeri yaratık (HogCraft portu). */
    public static final RegistrySupplier<EntityType<UnicornEntity>> UNICORN =
            ENTITIES.register("unicorn", key -> EntityType.Builder.of(UnicornEntity::new, MobCategory.CREATURE)
                    .sized(1.3964844F, 1.6F)
                    .clientTrackingRange(10)
                    .build(key));

    /** Arcanewood botu — vanilla Boat.Type genişletilemediği için ayrı EntityType. */
    public static final RegistrySupplier<EntityType<ArcanewoodBoatEntity>> ARCANEWOOD_BOAT =
            ENTITIES.register("arcanewood_boat", key -> EntityType.Builder.of(ArcanewoodBoatEntity::new, MobCategory.MISC)
                    .sized(1.375F, 0.5625F)
                    .clientTrackingRange(10)
                    .build(key));

    /** Arcanewood sandıklı botu. */
    public static final RegistrySupplier<EntityType<ArcanewoodChestBoatEntity>> ARCANEWOOD_CHEST_BOAT =
            ENTITIES.register("arcanewood_chest_boat", key -> EntityType.Builder.of(ArcanewoodChestBoatEntity::new, MobCategory.MISC)
                    .sized(1.375F, 0.5625F)
                    .clientTrackingRange(10)
                    .build(key));

    /** Hippogriff — gururlu, yarı kartal yarı at yaratığı; eğilerek saygı gösterilirse evcilleştirilebilir ve binilebilir. */
    public static final RegistrySupplier<EntityType<HippogriffEntity>> HIPPOGRIFF =
            ENTITIES.register("hippogriff", key -> EntityType.Builder.of(HippogriffEntity::new, MobCategory.CREATURE)
                    .sized(1.5F, 1.8F)
                    .clientTrackingRange(10)
                    .build(key));

    /** Acromantula — Aragog'un soyundan dev zehirli örümcek. */
    public static final RegistrySupplier<EntityType<AcromantulaEntity>> ACROMANTULA =
            ENTITIES.register("acromantula", key -> EntityType.Builder.of(AcromantulaEntity::new, MobCategory.MONSTER)
                    .notInPeaceful() // 26.x: 1.21.1 Monster.shouldDespawnInPeaceful paritesi
                    .sized(1.8F, 1.4F)
                    .clientTrackingRange(10)
                    .build(key));

    /** Kurtadam — yalnızca geceleri doğan vahşi yaratık. */
    public static final RegistrySupplier<EntityType<WerewolfEntity>> WEREWOLF =
            ENTITIES.register("werewolf", key -> EntityType.Builder.of(WerewolfEntity::new, MobCategory.MONSTER)
                    .notInPeaceful() // 26.x: 1.21.1 Monster.shouldDespawnInPeaceful paritesi
                    .sized(0.7F, 2.1F)
                    .clientTrackingRange(10)
                    .build(key));

    /** Grindylow — su altında yaşayan saldırgan küçük yaratık. */
    public static final RegistrySupplier<EntityType<GrindylowEntity>> GRINDYLOW =
            ENTITIES.register("grindylow", key -> EntityType.Builder.of(GrindylowEntity::new, MobCategory.MONSTER)
                    .notInPeaceful() // 26.x: 1.21.1 Monster.shouldDespawnInPeaceful paritesi
                    .sized(0.6F, 0.9F)
                    .clientTrackingRange(8)
                    .build(key));

    /** Kneazle — kedimsi, sadık evcil yaratık. */
    public static final RegistrySupplier<EntityType<KneazleEntity>> KNEAZLE =
            ENTITIES.register("kneazle", key -> EntityType.Builder.of(KneazleEntity::new, MobCategory.CREATURE)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(8)
                    .build(key));

    /**
     * Basilisk — devasa BOSS yılan. Yalnızca zindan feature'ı addFreshEntity ile
     * doğurur (SpawnPlacements kaydı YOK — doğal spawn yok). Büyük gövde + geniş
     * takip menzili için clientTrackingRange yükseltildi.
     */
    public static final RegistrySupplier<EntityType<BasiliskEntity>> BASILISK =
            ENTITIES.register("basilisk", key -> EntityType.Builder.of(BasiliskEntity::new, MobCategory.MONSTER)
                    .notInPeaceful() // 26.x: 1.21.1 Monster.shouldDespawnInPeaceful paritesi
                    .sized(3.0F, 4.0F)
                    .clientTrackingRange(16)
                    .build(key));

    /** Cisimleşmiş Patronus — Expecto Patronum'un çağırdığı ruhani yoldaş (doğal spawn yok). */
    public static final RegistrySupplier<EntityType<PatronusEntity>> PATRONUS =
            ENTITIES.register("patronus", key -> EntityType.Builder.of(PatronusEntity::new, MobCategory.MISC)
                    .sized(0.9F, 1.2F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build(key));

    /** Hogsmeade büyücü tüccarı — sabit zümrüt ekonomili NPC (yapılara yerleştirilir). */
    public static final RegistrySupplier<EntityType<WizardTraderEntity>> WIZARD_TRADER =
            ENTITIES.register("wizard_trader", key -> EntityType.Builder.of(WizardTraderEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.9F)
                    .clientTrackingRange(10)
                    .build(key));

    /**
     * Protego Diabolica'nın MAVİ ALEV EJDERHASI — salt görsel tezahür.
     * <p>MISC kategorisi BİLİNÇLİ: Mob tabanlı olsa
     * da doğal spawn'ı yok; MONSTER seçilseydi global spawn tavanına girerdi.
     * {@code SpawnPlacements} kaydı YOK, {@code noSummon()} → /summon ve spawn egg ile
     * elle çağrılamaz (yalnız {@code DiabolicaDragonManager} doğurur; sahipsiz doğan
     * bir ejderha zaten 20 tick içinde kendini siler).
     * <p>{@code sized} model hacminden küçük tutuldu (çarpışma zaten kapalı) —
     * {@code noCulling} sayesinde ~4 bloklık gövde yine de kırpılmaz.
     */
    public static final RegistrySupplier<EntityType<FiendfyreDragonEntity>> DIABOLICA_DRAGON =
            ENTITIES.register("diabolica_dragon", key -> EntityType.Builder.of(FiendfyreDragonEntity::new, MobCategory.MISC)
                    .sized(2.0F, 2.0F)
                    .fireImmune()
                    .noSummon()
                    .clientTrackingRange(16)
                    .build(key));

    private ModEntities() {}

    public static void init() {
        ENTITIES.register();
    }

    /**
     * Varsayılan attribute kümeleri (her living entity için ZORUNLU). Kök: Fabric
     * {@code FabricDefaultAttributeRegistry.register} (ArcanumFabric) — aynı liste, aynı sıra.
     * Platform kuyruğuna alınır (Fabric: bağlama sonrası; Forge: EntityAttributeCreationEvent).
     */
    public static void registerAttributes() {
        Platform.get().registerAttributes(DEATH_EATER, () -> DeathEaterEntity.createAttributes());
        Platform.get().registerAttributes(DEMENTOR, () -> DementorEntity.createAttributes());
        Platform.get().registerAttributes(BOWTRUCKLE, () -> BowtruckleEntity.createAttributes());
        Platform.get().registerAttributes(MOONCALF, () -> MooncalfEntity.createAttributes());
        Platform.get().registerAttributes(BROOM, () -> BroomEntity.createAttributes());
        Platform.get().registerAttributes(TROLL, () -> TrollEntity.createAttributes());
        Platform.get().registerAttributes(PHOENIX, () -> PhoenixEntity.createAttributes());
        Platform.get().registerAttributes(THUNDERBIRD, () -> ThunderbirdEntity.createAttributes());
        Platform.get().registerAttributes(SNOWY_OWL, () -> SnowyOwlEntity.createAttributes());
        Platform.get().registerAttributes(THESTRAL, () -> ThestralEntity.createAttributes());
        Platform.get().registerAttributes(UNICORN, () -> UnicornEntity.createAttributes());
        Platform.get().registerAttributes(HIPPOGRIFF, () -> HippogriffEntity.createAttributes());
        Platform.get().registerAttributes(ACROMANTULA, () -> AcromantulaEntity.createAttributes());
        Platform.get().registerAttributes(WEREWOLF, () -> WerewolfEntity.createAttributes());
        Platform.get().registerAttributes(GRINDYLOW, () -> GrindylowEntity.createAttributes());
        Platform.get().registerAttributes(KNEAZLE, () -> KneazleEntity.createAttributes());
        Platform.get().registerAttributes(BASILISK, () -> BasiliskEntity.createAttributes());
        Platform.get().registerAttributes(PATRONUS, () -> PatronusEntity.createAttributes());
        Platform.get().registerAttributes(WIZARD_TRADER, () -> WizardTraderEntity.createAttributes());
        // Protego Diabolica alev ejderhası — Mob tabanlı olduğu için attribute kaydı ZORUNLU
        Platform.get().registerAttributes(DIABOLICA_DRAGON, () -> FiendfyreDragonEntity.createAttributes());
    }

    /** Doğal spawn yerleşim kuralları (Arcanum.init sonunda çağrılır; Platform kuyruğu). */
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
        Platform.get().registerSpawnPlacement(DEATH_EATER,
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.deathEaterSpawnMult,
                        Monster::checkAnyLightMonsterSpawnRules));
        Platform.get().registerSpawnPlacement(DEMENTOR,
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.dementorSpawnMult,
                        Monster::checkAnyLightMonsterSpawnRules));
        Platform.get().registerSpawnPlacement(BOWTRUCKLE,
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.bowtruckleSpawnMult,
                        com.arcanum.entity.BowtruckleEntity::canSpawn));
        Platform.get().registerSpawnPlacement(MOONCALF,
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.mooncalfSpawnMult,
                        com.arcanum.entity.MooncalfEntity::canSpawn));
        Platform.get().registerSpawnPlacement(TROLL,
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.trollSpawnMult,
                        Monster::checkAnyLightMonsterSpawnRules));
        Platform.get().registerSpawnPlacement(PHOENIX,
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.phoenixSpawnMult,
                        com.arcanum.entity.PhoenixEntity::canSpawn));
        Platform.get().registerSpawnPlacement(THUNDERBIRD,
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.thunderbirdSpawnMult,
                        com.arcanum.entity.ThunderbirdEntity::canSpawn));
        Platform.get().registerSpawnPlacement(SNOWY_OWL,
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.snowyOwlSpawnMult,
                        com.arcanum.entity.SnowyOwlEntity::canSpawn));
        Platform.get().registerSpawnPlacement(THESTRAL,
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.thestralSpawnMult,
                        com.arcanum.entity.ThestralEntity::canSpawn));
        Platform.get().registerSpawnPlacement(UNICORN,
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.unicornSpawnMult,
                        Animal::checkAnimalSpawnRules));
        Platform.get().registerSpawnPlacement(HIPPOGRIFF,
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.hippogriffSpawnMult,
                        Animal::checkAnimalSpawnRules));
        Platform.get().registerSpawnPlacement(ACROMANTULA,
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.acromantulaSpawnMult,
                        Monster::checkAnyLightMonsterSpawnRules));
        Platform.get().registerSpawnPlacement(WEREWOLF,
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.werewolfSpawnMult,
                        com.arcanum.entity.WerewolfEntity::canSpawn));
        Platform.get().registerSpawnPlacement(GRINDYLOW,
                SpawnPlacementTypes.IN_WATER,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.grindylowSpawnMult,
                        com.arcanum.entity.GrindylowEntity::canSpawn));
        Platform.get().registerSpawnPlacement(KNEAZLE,
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.kneazleSpawnMult,
                        com.arcanum.entity.KneazleEntity::canSpawn));
    }
}
