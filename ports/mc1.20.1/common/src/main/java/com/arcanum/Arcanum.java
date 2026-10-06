package com.arcanum;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Arcanum — loader-bağımsız çekirdek giriş noktası.
 * Tüm registry'ler buradan başlatılır. Loader-özel kod (Fabric/Forge)
 * bu sınıfı çağırır ama bu sınıf loader-özel hiçbir şeye bağımlı değildir.
 *
 * <p>AĞ KAYDI (f3-network-contract): {@code ArcanumNetwork.registerC2SReceivers()}
 * loader ORTAK init'inde {@code Arcanum.init()}'ten HEMEN SONRA çağrılır (Fabric:
 * {@code ArcanumFabric.onInitialize}, Forge: mod ctor). Bilerek BURADA çağrılmıyor —
 * sözleşme kayıt noktasını loader'a verir; iki kez kayıt (hem burada hem loader'da)
 * kanal çakışmasına yol açar.
 */
public final class Arcanum {
    public static final String MODID = "arcanum";
    public static final Logger LOGGER = LoggerFactory.getLogger("Arcanum");

    private Arcanum() {}

    public static void init() {
        LOGGER.info("[Arcanum] Çekirdek başlatılıyor...");
        ModComponents.init();
        ModParticles.init();
        ModBlocks.init();
        ModVillagers.init();
        ModMenus.init();
        ModEntities.init();
        ModArmorMaterials.init();
        ModMobEffects.init();
        ModPotions.init();
        ModItems.init();
        ModFeatures.init();
        ModStructures.init();
        ModCreativeTabs.init();
        ModSpells.init();
        ModEntities.registerSpawnPlacements();
        LOGGER.info("[Arcanum] Çekirdek başlatıldı. (" + ModSpells.SPELLS.size() + " büyü)");
    }
}
