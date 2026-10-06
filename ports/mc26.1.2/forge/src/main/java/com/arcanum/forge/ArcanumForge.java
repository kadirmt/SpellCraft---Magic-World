package com.arcanum.forge;

import com.arcanum.Arcanum;
import com.arcanum.forge.client.ArcanumForgeClient;
import com.arcanum.forge.platform.ForgePlatform;
import com.arcanum.forge.worldgen.ArcanumTableBiomeModifier;
import com.arcanum.platform.Platform;
import com.arcanum.worldgen.ArcanumRegions;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge ana giriş (EventBus 7). İnce katman — sıra iki loader'da AYNI (g2-platform-contract.md §2):
 * <pre>
 *   Platform.init(ForgePlatform)  →  Arcanum.init()  [DeferredRegister'lar mod BusGroup'a bağlanır; ağ; kuyruklar]
 *   (istemci) Platform.initClient(ForgePlatformClient) → ArcanumClient.init()   [HEPSİ mod ctor'unda: kuyruklar
 *                                                                               SelfDestructing olaylardan ÖNCE dolar]
 *   RegisterEvent'ler (Forge, vanilla registry sırası) → EntityAttributeCreation/SpawnPlacementRegister (DEFAULT bus)
 *   FMLCommonSetupEvent.enqueueWork(Arcanum::commonSetup + TerraBlender bölgesi)   [.get() serbest, ana thread]
 * </pre>
 * Olay bus kuralı: YALNIZ {@code IModBusEvent} olanlar {@code XEvent.getBus(modBusGroup)}; geri kalan her şey statik
 * {@code XEvent.BUS} ({@link ArcanumForgeEvents}).
 *
 * <p>Biyom değişiklikleri: tek özel tip {@code arcanum:table} ({@link ArcanumTableBiomeModifier}) +
 * {@code data/arcanum/forge/biome_modifier/arcanum_table.json}.
 */
@Mod(Arcanum.MODID)
public final class ArcanumForge {

    public ArcanumForge(FMLJavaModLoadingContext context) {
        BusGroup modBusGroup = context.getModBusGroup();

        ForgePlatform platform = new ForgePlatform(modBusGroup);
        Platform.init(platform);              // 1) HER ŞEYDEN ÖNCE
        Arcanum.init();                       // 2) kayıt tanımları + DR bağlama + ağ + attribute/placement/POI kuyrukları

        // Biyom tablosu serializer'ı (forge:biome_modifier_serializers → arcanum:table)
        ArcanumTableBiomeModifier.register(modBusGroup);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ArcanumForgeClient.init(modBusGroup);   // 3) istemci SPI + ArcanumClient.init() (hâlâ mod ctor'u)
        }

        // 4) kayıtlar bağlandıktan sonra — ana thread
        FMLCommonSetupEvent.getBus(modBusGroup).addListener(ArcanumForge::onCommonSetup);

        // 5) oyun-içi olay köprüleri (DEFAULT bus)
        ArcanumForgeEvents.register();
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Arcanum.commonSetup();
            // TerraBlender'ın Forge'da entrypoint'i YOK — bölge kaydı TB'nin önerdiği yerde:
            // FMLCommonSetupEvent.enqueueWork (Fabric'te "terrablender" entrypoint'i).
            ArcanumRegions.registerRegions();
        });
    }
}
