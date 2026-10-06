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
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Arcanum — çekirdek giriş noktası (Forge portu).
 *
 * <p>Forge'da DeferredRegister'lar kayıt olayını (RegisterEvent) MOD event
 * bus'ından dinler; bu yüzden init artık bus alır. ArcanumForge (loader
 * katmanı) mod constructor'ında {@code Arcanum.init(modBus)} çağırır — tüm
 * registry sınıfları tek noktadan bus'a bağlanır (bkz.
 * ports/_planning/f6-registry-contract.md).
 *
 * <p>DİKKAT — Fabric halinden farklar:
 * <ul>
 *   <li>Bu metot döndüğünde registry'ler HENÜZ DOLU DEĞİLDİR (kayıt daha
 *       sonra RegisterEvent'te yapılır). Burada asla {@code RegistryObject.get()}
 *       çağrılmamalı.</li>
 *   <li>Spawn placement kayıtları artık burada değil — T3 (ArcanumForge)
 *       mod bus'ta {@code SpawnPlacementRegisterEvent} dinleyip
 *       {@link ModEntities#registerSpawnPlacements} çağırır.</li>
 *   <li>Entity attribute kayıtları da T3'te — {@code EntityAttributeCreationEvent}
 *       ile {@link ModEntities#registerAttributes}.</li>
 * </ul>
 */
public final class Arcanum {
    public static final String MODID = "arcanum";
    public static final Logger LOGGER = LoggerFactory.getLogger("Arcanum");

    private Arcanum() {}

    /**
     * Tek toplu kayıt giriş noktası ("registerAll") — mod constructor'ında,
     * MOD event bus'ı ile çağrılır. Sıra Fabric halindeki init sırasıyla aynen
     * korunmuştur (Forge'da DR'ler arası sıra kayıt sonucunu değiştirmez;
     * gerçek kayıt sırası registry türüne göre Forge tarafından belirlenir).
     */
    public static void init(IEventBus modBus) {
        LOGGER.info("[Arcanum] Çekirdek başlatılıyor...");
        ModComponents.register(modBus);
        ModParticles.register(modBus);
        ModBlocks.register(modBus);
        ModVillagers.register(modBus);
        ModMenus.register(modBus);
        ModEntities.register(modBus);
        ModArmorMaterials.register(modBus);
        ModMobEffects.register(modBus);
        ModPotions.register(modBus);
        ModItems.register(modBus);
        ModFeatures.register(modBus);
        ModStructures.register(modBus);
        ModCreativeTabs.register(modBus);
        ModSpells.init();
        LOGGER.info("[Arcanum] Çekirdek başlatıldı. (" + ModSpells.SPELLS.size() + " büyü)");
    }
}
