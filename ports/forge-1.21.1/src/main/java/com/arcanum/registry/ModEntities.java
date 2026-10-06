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
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Özel yaratıklar (EntityType kayıtları) — Forge DeferredRegister ile.
 * Attribute + spawn placement kayıtları da bu sınıfta toplanır; ikisi de
 * Forge'da MOD bus event'i gerektirir ve loader katmanı (ArcanumForge/T3)
 * ilgili event handler'ından buradaki metotları çağırır
 * (bkz. ports/_planning/f6-registry-contract.md).
 */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Arcanum.MODID);

    /** Ölümyiyen — büyü atan kara büyücü (kamp + gloomwood gecesi). */
    public static final RegistryObject<EntityType<DeathEaterEntity>> DEATH_EATER =
            ENTITIES.register("death_eater", () -> EntityType.Builder.of(DeathEaterEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("death_eater"));

    /** Ruh Emici — süzülen karanlık varlık (gloomwood gecesi). */
    public static final RegistryObject<EntityType<DementorEntity>> DEMENTOR =
            ENTITIES.register("dementor", () -> EntityType.Builder.of(DementorEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 2.4F)
                    .clientTrackingRange(12)
                    .build("dementor"));

    /** Bowtruckle — minik dal bekçisi (pasif). */
    public static final RegistryObject<EntityType<BowtruckleEntity>> BOWTRUCKLE =
            ENTITIES.register("bowtruckle", () -> EntityType.Builder.of(BowtruckleEntity::new, MobCategory.CREATURE)
                    .sized(0.35F, 0.65F)
                    .clientTrackingRange(8)
                    .build("bowtruckle"));

    /** Mooncalf — ürkek ay danası (pasif). */
    public static final RegistryObject<EntityType<MooncalfEntity>> MOONCALF =
            ENTITIES.register("mooncalf", () -> EntityType.Builder.of(MooncalfEntity::new, MobCategory.CREATURE)
                    .sized(0.8F, 1.0F)
                    .clientTrackingRange(10)
                    .build("mooncalf"));

    /** Uçan süpürge — binilebilir araç (doğal spawn yok). */
    public static final RegistryObject<EntityType<BroomEntity>> BROOM =
            ENTITIES.register("broom", () -> EntityType.Builder.of(BroomEntity::new, MobCategory.MISC)
                    .sized(0.9F, 0.5F)
                    .clientTrackingRange(10)
                    .build("broom"));

    /** Troll — devasa saldırgan dağ trolü (HogCraft portu). */
    public static final RegistryObject<EntityType<TrollEntity>> TROLL =
            ENTITIES.register("troll", () -> EntityType.Builder.of(TrollEntity::new, MobCategory.MONSTER)
                    .sized(1.2F, 2.8F)
                    .clientTrackingRange(10)
                    .build("troll"));

    /** Phoenix — evcilleştirilebilir uçan anka kuşu, ateşe bağışık (HogCraft portu). */
    public static final RegistryObject<EntityType<PhoenixEntity>> PHOENIX =
            ENTITIES.register("phoenix", () -> EntityType.Builder.of(PhoenixEntity::new, MobCategory.CREATURE)
                    .sized(0.5F, 0.9F)
                    .clientTrackingRange(10)
                    .build("phoenix"));

    /** Thunderbird — evcilleştirilebilir uçan fırtına kuşu (HogCraft portu). */
    public static final RegistryObject<EntityType<ThunderbirdEntity>> THUNDERBIRD =
            ENTITIES.register("thunderbird", () -> EntityType.Builder.of(ThunderbirdEntity::new, MobCategory.CREATURE)
                    .sized(0.9F, 1.3F)
                    .clientTrackingRange(10)
                    .build("thunderbird"));

    /** Kar Baykuşu — evcilleştirilebilir uçan baykuş, gözleri karanlıkta parlar (HogCraft portu). */
    public static final RegistryObject<EntityType<SnowyOwlEntity>> SNOWY_OWL =
            ENTITIES.register("snowy_owl", () -> EntityType.Builder.of(SnowyOwlEntity::new, MobCategory.CREATURE)
                    .sized(0.5F, 0.7F)
                    .clientTrackingRange(8)
                    .build("snowy_owl"));

    /** Thestral — evcilleştirilebilir, uçabilen kanatlı binek (HogCraft portu). */
    public static final RegistryObject<EntityType<ThestralEntity>> THESTRAL =
            ENTITIES.register("thestral", () -> EntityType.Builder.of(ThestralEntity::new, MobCategory.CREATURE)
                    .sized(1.3964844F, 1.6F)
                    .clientTrackingRange(10)
                    .build("thestral"));

    /** Unicorn — evcilleştirilebilir, eyerle binilebilen at-benzeri yaratık (HogCraft portu). */
    public static final RegistryObject<EntityType<UnicornEntity>> UNICORN =
            ENTITIES.register("unicorn", () -> EntityType.Builder.of(UnicornEntity::new, MobCategory.CREATURE)
                    .sized(1.3964844F, 1.6F)
                    .clientTrackingRange(10)
                    .build("unicorn"));

    /** Arcanewood botu — vanilla Boat.Type genişletilemediği için ayrı EntityType. */
    public static final RegistryObject<EntityType<ArcanewoodBoatEntity>> ARCANEWOOD_BOAT =
            ENTITIES.register("arcanewood_boat", () -> EntityType.Builder.of(ArcanewoodBoatEntity::new, MobCategory.MISC)
                    .sized(1.375F, 0.5625F)
                    .clientTrackingRange(10)
                    .build("arcanewood_boat"));

    /** Arcanewood sandıklı botu. */
    public static final RegistryObject<EntityType<ArcanewoodChestBoatEntity>> ARCANEWOOD_CHEST_BOAT =
            ENTITIES.register("arcanewood_chest_boat", () -> EntityType.Builder.of(ArcanewoodChestBoatEntity::new, MobCategory.MISC)
                    .sized(1.375F, 0.5625F)
                    .clientTrackingRange(10)
                    .build("arcanewood_chest_boat"));

    /** Hippogriff — gururlu, yarı kartal yarı at yaratığı; eğilerek saygı gösterilirse evcilleştirilebilir ve binilebilir. */
    public static final RegistryObject<EntityType<HippogriffEntity>> HIPPOGRIFF =
            ENTITIES.register("hippogriff", () -> EntityType.Builder.of(HippogriffEntity::new, MobCategory.CREATURE)
                    .sized(1.5F, 1.8F)
                    .clientTrackingRange(10)
                    .build("hippogriff"));

    /** Acromantula — Aragog'un soyundan dev zehirli örümcek. */
    public static final RegistryObject<EntityType<AcromantulaEntity>> ACROMANTULA =
            ENTITIES.register("acromantula", () -> EntityType.Builder.of(AcromantulaEntity::new, MobCategory.MONSTER)
                    .sized(1.8F, 1.4F)
                    .clientTrackingRange(10)
                    .build("acromantula"));

    /** Kurtadam — yalnızca geceleri doğan vahşi yaratık. */
    public static final RegistryObject<EntityType<WerewolfEntity>> WEREWOLF =
            ENTITIES.register("werewolf", () -> EntityType.Builder.of(WerewolfEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 2.1F)
                    .clientTrackingRange(10)
                    .build("werewolf"));

    /** Grindylow — su altında yaşayan saldırgan küçük yaratık. */
    public static final RegistryObject<EntityType<GrindylowEntity>> GRINDYLOW =
            ENTITIES.register("grindylow", () -> EntityType.Builder.of(GrindylowEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 0.9F)
                    .clientTrackingRange(8)
                    .build("grindylow"));

    /** Kneazle — kedimsi, sadık evcil yaratık. */
    public static final RegistryObject<EntityType<KneazleEntity>> KNEAZLE =
            ENTITIES.register("kneazle", () -> EntityType.Builder.of(KneazleEntity::new, MobCategory.CREATURE)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(8)
                    .build("kneazle"));

    /**
     * Basilisk — devasa BOSS yılan. Yalnızca zindan feature'ı addFreshEntity ile
     * doğurur (SpawnPlacements kaydı YOK — doğal spawn yok). Büyük gövde + geniş
     * takip menzili için clientTrackingRange yükseltildi.
     */
    public static final RegistryObject<EntityType<BasiliskEntity>> BASILISK =
            ENTITIES.register("basilisk", () -> EntityType.Builder.of(BasiliskEntity::new, MobCategory.MONSTER)
                    .sized(3.0F, 4.0F)
                    .clientTrackingRange(16)
                    .build("basilisk"));

    /** Cisimleşmiş Patronus — Expecto Patronum'un çağırdığı ruhani yoldaş (doğal spawn yok). */
    public static final RegistryObject<EntityType<PatronusEntity>> PATRONUS =
            ENTITIES.register("patronus", () -> EntityType.Builder.of(PatronusEntity::new, MobCategory.MISC)
                    .sized(0.9F, 1.2F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build("patronus"));

    /** Hogsmeade büyücü tüccarı — sabit zümrüt ekonomili NPC (yapılara yerleştirilir). */
    public static final RegistryObject<EntityType<WizardTraderEntity>> WIZARD_TRADER =
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
     * bir ejderha zaten 20 tick içinde kendini siler).
     * <p>{@code sized} model hacminden küçük tutuldu (çarpışma zaten kapalı) —
     * {@code noCulling} sayesinde ~4 bloklık gövde yine de kırpılmaz.
     */
    public static final RegistryObject<EntityType<FiendfyreDragonEntity>> DIABOLICA_DRAGON =
            ENTITIES.register("diabolica_dragon", () -> EntityType.Builder.of(FiendfyreDragonEntity::new, MobCategory.MISC)
                    .sized(2.0F, 2.0F)
                    .fireImmune()
                    .noSummon()
                    .clientTrackingRange(16)
                    .build("diabolica_dragon"));

    private ModEntities() {}

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }

    /**
     * Doğal spawn yerleşim kuralları — T3 (ArcanumForge) MOD bus'ta
     * {@link SpawnPlacementRegisterEvent} dinleyip bu metodu çağırır.
     * Operation.REPLACE: bu tipler için vanilla kaydı yoktur; REPLACE,
     * Fabric halindeki SpawnPlacements.register davranışının birebir karşılığıdır.
     *
     * NOT: Monster.checkMonsterSpawnRules yerine checkAnyLightMonsterSpawnRules
     * kullanılıyor — Ölümyiyen/Ruh Emici HP lore'unda "yalnız karanlıkta"
     * yaratıklar değil; ayrıca gloomwood'un kendi glow_lichen dekoru
     * (blok ışığı yayıyor) + gündüz gökyüzü ışığı normal karanlık şartını
     * sık sık boşa çıkarıyordu, bu da doğal spawn'ı beklenenden çok
     * seyrekleştiriyordu.
     */
    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        // KULLANICI CONFIG'İ (oyuncu şikayeti: "the creatures are spawning quite
        // frequently"): her predicate SpawnGate.gated ile sarmalandı — doğal
        // (NATURAL/CHUNK_GENERATION) spawn denemeleri mob-başına çarpanla süzülür.
        // Reagent veren yaratıklar varsayılan 1.0, diğerleri 0.85 (−%15).
        // Yapı spawn'ı / spawn yumurtası / summon / üreme ETKİLENMEZ.
        event.register(DEATH_EATER.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.deathEaterSpawnMult,
                        Monster::checkAnyLightMonsterSpawnRules),
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(DEMENTOR.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.dementorSpawnMult,
                        Monster::checkAnyLightMonsterSpawnRules),
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(BOWTRUCKLE.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.bowtruckleSpawnMult,
                        BowtruckleEntity::canSpawn),
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(MOONCALF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.mooncalfSpawnMult,
                        MooncalfEntity::canSpawn),
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(TROLL.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.trollSpawnMult,
                        Monster::checkAnyLightMonsterSpawnRules),
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(PHOENIX.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.phoenixSpawnMult,
                        PhoenixEntity::canSpawn),
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(THUNDERBIRD.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.thunderbirdSpawnMult,
                        ThunderbirdEntity::canSpawn),
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(SNOWY_OWL.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.snowyOwlSpawnMult,
                        SnowyOwlEntity::canSpawn),
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(THESTRAL.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.thestralSpawnMult,
                        ThestralEntity::canSpawn),
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(UNICORN.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.unicornSpawnMult,
                        Animal::checkAnimalSpawnRules),
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(HIPPOGRIFF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.hippogriffSpawnMult,
                        Animal::checkAnimalSpawnRules),
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ACROMANTULA.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.acromantulaSpawnMult,
                        Monster::checkAnyLightMonsterSpawnRules),
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(WEREWOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.werewolfSpawnMult,
                        WerewolfEntity::canSpawn),
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(GRINDYLOW.get(),
                SpawnPlacementTypes.IN_WATER,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.grindylowSpawnMult,
                        GrindylowEntity::canSpawn),
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(KNEAZLE.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.arcanum.entity.SpawnGate.gated(c -> c.kneazleSpawnMult,
                        KneazleEntity::canSpawn),
                SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    /**
     * Attribute supplier kayıtları — Fabric halinde ArcanumFabric.onInitialize
     * içindeki FabricDefaultAttributeRegistry bloğunun birebir taşınmış hali.
     * T3 (ArcanumForge) MOD bus'ta {@link EntityAttributeCreationEvent} dinleyip
     * bu metodu çağırır. 19 kayıt (BASILISK, BROOM, PATRONUS ve WIZARD_TRADER dahil).
     */
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(DEATH_EATER.get(), DeathEaterEntity.createAttributes().build());
        event.put(DEMENTOR.get(), DementorEntity.createAttributes().build());
        event.put(BOWTRUCKLE.get(), BowtruckleEntity.createAttributes().build());
        event.put(MOONCALF.get(), MooncalfEntity.createAttributes().build());
        event.put(BROOM.get(), BroomEntity.createAttributes().build());
        event.put(TROLL.get(), TrollEntity.createAttributes().build());
        event.put(PHOENIX.get(), PhoenixEntity.createAttributes().build());
        event.put(THUNDERBIRD.get(), ThunderbirdEntity.createAttributes().build());
        event.put(SNOWY_OWL.get(), SnowyOwlEntity.createAttributes().build());
        event.put(THESTRAL.get(), ThestralEntity.createAttributes().build());
        event.put(UNICORN.get(), UnicornEntity.createAttributes().build());
        event.put(HIPPOGRIFF.get(), HippogriffEntity.createAttributes().build());
        event.put(ACROMANTULA.get(), AcromantulaEntity.createAttributes().build());
        event.put(WEREWOLF.get(), WerewolfEntity.createAttributes().build());
        event.put(GRINDYLOW.get(), GrindylowEntity.createAttributes().build());
        event.put(KNEAZLE.get(), KneazleEntity.createAttributes().build());
        event.put(BASILISK.get(), BasiliskEntity.createAttributes().build());
        event.put(PATRONUS.get(), PatronusEntity.createAttributes().build());
        event.put(WIZARD_TRADER.get(), WizardTraderEntity.createAttributes().build());
        // Protego Diabolica alev ejderhası — Mob tabanlı olduğu için attribute ZORUNLU;
        // unutulursa doğum anında (DiabolicaDragonManager.tick → summonFor) CRASH.
        event.put(DIABOLICA_DRAGON.get(), FiendfyreDragonEntity.createAttributes().build());
    }
}
