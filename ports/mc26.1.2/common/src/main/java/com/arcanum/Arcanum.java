package com.arcanum;

import com.arcanum.config.ArcanumConfig;
import com.arcanum.network.ArcanumNetwork;
import com.arcanum.registry.ModArmorMaterials;
import com.arcanum.registry.ModBlocks;
import com.arcanum.registry.ModComponents;
import com.arcanum.registry.ModCreativeTabs;
import com.arcanum.registry.ModEntities;
import com.arcanum.registry.ModFeatures;
import com.arcanum.registry.ModItems;
import com.arcanum.registry.ModMenus;
import com.arcanum.registry.ModMobEffects;
import com.arcanum.registry.ModParticles;
import com.arcanum.registry.ModPotions;
import com.arcanum.registry.ModStructures;
import com.arcanum.registry.ModVillagers;
import com.arcanum.spell.ModSpells;
import com.arcanum.worldgen.ArcanumRegions;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Arcanum — loader-bağımsız çekirdek giriş noktası.
 * Tüm registry'ler buradan başlatılır. Loader-özel kod (Fabric/Forge)
 * bu sınıfı çağırır ama bu sınıf loader-özel hiçbir şeye bağımlı değildir.
 *
 * <p>Loader sırası (iki loader'da AYNI — g2-platform-contract.md §2):
 * <ol>
 *   <li>{@code Platform.init(backend)}</li>
 *   <li>{@link #init()} — kayıt tanımları + ağ bildirimi + attribute/placement kuyruğu. Forge: mod ctor'u
 *       (FML worker thread'i). {@code .get()} YASAK.</li>
 *   <li>{@link #commonSetup()} — kayıtlar bağlandıktan SONRA ({@code .get()} serbest). Fabric: onInitialize sonu;
 *       Forge: {@code FMLCommonSetupEvent.enqueueWork}.</li>
 * </ol>
 * Oyun-içi olay köprüleri {@link ArcanumEvents}'te (iki loader AYNI metotları çağırır).
 */
public final class Arcanum {
    public static final String MODID = "arcanum";
    public static final Logger LOGGER = LoggerFactory.getLogger("Arcanum");

    private Arcanum() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    public static void init() {
        LOGGER.info("[Arcanum] Çekirdek başlatılıyor...");
        // Kök sırası (1.21.1 Arcanum.init) KORUNUR — Fabric'te registry flush sırası budur
        // (g2-platform-contract §11.3: Components, Particles, Blocks, ..., Entities, MobEffects, Potions, Items).
        ModComponents.init();
        ModParticles.init();
        ModBlocks.init();
        ModVillagers.init();
        ModMenus.init();
        ModEntities.init();
        // ModArmorMaterials: 26.x'te ArmorMaterial bir registry DEĞİL (düz değer) → init() boş;
        // kök çağrı sırası korunsun diye çağrılır.
        ModArmorMaterials.init();
        ModMobEffects.init();
        ModPotions.init();
        ModItems.init();
        ModFeatures.init();
        ModStructures.init();
        ModCreativeTabs.init();
        ModSpells.init();
        // Doğal spawn yerleşimleri (kök ModEntities.registerSpawnPlacements — SpawnGate config çarpanlı,
        // 15 tür) → Platform.registerSpawnPlacement kuyruğu (Fabric: drain'de SpawnPlacements.register,
        // Forge: SpawnPlacementRegisterEvent REPLACE). Kökte de Arcanum.init sonunda çağrılıyordu.
        ModEntities.registerSpawnPlacements();
        // Payload bildirimi + C2S alıcıları (kökteki ArcanumFabric PayloadTypeRegistry sırası = Forge discriminator'ı)
        ArcanumNetwork.registerPayloads();
        // Yaratık attribute'ları (kök ArcanumFabric FabricDefaultAttributeRegistry.register ×20, aynı liste)
        // → Platform.registerAttributes kuyruğu (Fabric: drain'de FabricDefaultAttributeRegistry,
        // Forge: EntityAttributeCreationEvent).
        ModEntities.registerAttributes();
        LOGGER.info("[Arcanum] Çekirdek başlatıldı. (" + ModSpells.SPELLS.size() + " büyü)");
    }

    /**
     * Kayıtlar bağlandıktan SONRA — {@code .get()} serbest, ana thread.
     * Kökteki ArcanumFabric.onInitialize içinde kayıt anında yapılan blok-bağımlı kurulumlar.
     */
    public static void commonSetup() {
        // Config ısıtma: config/arcanum.json'u başlangıçta oku/oluştur (ilk spawn denetiminde
        // sunucu tick'inde disk I/O olmasın). ArcanumConfig.get() asla exception fırlatmaz.
        ArcanumConfig.get();

        // Eski dünya göçü: DFU şemasına tanıtılan Arcanum varlık listesi (V1460EntitiesMixin) kayıtla aynı mı?
        com.arcanum.util.ArcanumDataFixerEntities.verifyAgainstRegistry();

        // Arcanewood: baltayla soyma → ModBlocks.init() içinde Platform.registerStrippable ile KUYRUĞA alındı
        // (kök StrippableBlockRegistry.register paritesi — AXIS korunur). Burada TEKRAR çağrılmaz (çift kayıt).

        // Yanabilirlik — vanilla odun paritesi (log 5/5, plank 5/20, yaprak 30/60).
        // Kökte FlammableBlockRegistry.add(block, burn, spread) idi; iki loader'da TEK mekanizma:
        // ortak invoker → vanilla FireBlock#setFlammable(block, igniteOdds, burnOdds) (ModBlocks.registerFlammables).
        ModBlocks.registerFlammables();

        // KALDIRILDI (oyuncu şikayeti: "trees from your mod are spawning outside of
        // their intended biomes"): arcanewood ağacı #minecraft:is_forest ile TÜM orman
        // biyomlarına ekleniyordu; BiomesOPlenty kendi ormanlarını da bu tag'e eklediği
        // için BOP dünyalarında her ormanda çıkıyordu. Ağaç artık YALNIZ kendi
        // biyomlarında doğar — gloomwood / arcanewood_grove biyom JSON'larının
        // "features" listesindeki arcanum:arcanewood_trees_gloomwood ve
        // arcanum:arcanewood_grove_trees placed feature'ları üzerinden.

        // Gloomwood yüzey kuralları (TerraBlender) — bloklara ihtiyaç duyduğu için BURADA
        // (kökteki GLOOM_GRASS_BLOCK.listen(...) deseninin eşdeğeri: blok kaydı bittikten sonra).
        // Bölge kaydı (Regions.register) loader'da: Fabric "terrablender" entrypoint'i, Forge commonSetup.
        ArcanumRegions.registerSurfaceRules();
    }
}
