package com.arcanum.forge;

import java.lang.reflect.Method;

import com.arcanum.Arcanum;
import com.arcanum.entity.AcromantulaEntity;
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
import com.arcanum.forge.worldgen.ArcanumTerraBlenderForge;
import com.arcanum.network.ArcanumNetwork;
import com.arcanum.registry.ModBlocks;
import com.arcanum.registry.ModEntities;
import dev.architectury.platform.forge.EventBuses;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge ana giriş noktası — fabric'teki {@code ArcanumFabric.onInitialize()} ile
 * birebir aynı davranış kümesini Forge event düzenine dağıtır:
 * <ul>
 *   <li><b>Mod ctor:</b> EventBuses kaydı (Arch 9.x şartı) → {@code Arcanum.init()} →
 *       {@code ArcanumNetwork.registerC2SReceivers()} (f3-network-contract "ortak init") +
 *       MOD/FORGE bus dinleyici kayıtları.</li>
 *   <li><b>FMLCommonSetupEvent:</b> registry-sonrası işler (TerraBlender bölgesi,
 *       yanabilirlik, brewing) — fabric'te onInitialize "her şey kayıtlıyken" çalıştığı
 *       için orada inline'dı; Forge'da ctor kayıtlardan ÖNCE koştuğundan buraya taşındı.</li>
 *   <li><b>EntityAttributeCreationEvent (MOD bus):</b> fabric'teki 17
 *       {@code FabricDefaultAttributeRegistry.register} kaydının karşılığı.</li>
 *   <li><b>Oyun-içi event'ler:</b> {@link ArcanumForgeEvents} (tick/login/ölüm/komut/ticaret/soyma),
 *       {@link ImperiusInputBlocker} (kurban girdi iptali), {@link ArcanumForgeLoot} (loot enjeksiyonu).</li>
 * </ul>
 *
 * <p>SPAWN PLACEMENT NOTU: {@code ModEntities.registerSpawnPlacements()} common'da Architectury
 * {@code SpawnPlacementsRegistry} ile yazılı — Arch 9.x bunu Forge'da kendi içinde
 * {@code SpawnPlacementRegisterEvent}'e köprüler, fabric'te de ekstra kayıt yoktu →
 * burada ELLE SpawnPlacementRegisterEvent kaydı GEREKMEZ (doğrulandı).</p>
 */
@Mod(Arcanum.MODID)
public final class ArcanumForge {
    public ArcanumForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        // Architectury'nin mod event bus'ını bulabilmesi için kayıt (Architectury 9.x Forge kalıbı)
        // — Arcanum.init()'ten ÖNCE yapılmalı (DeferredRegister'lar bus'a bağlanır).
        EventBuses.registerModEventBus(Arcanum.MODID, modBus);
        Arcanum.init();

        // 1.20.1 PORT: ağ katmanı komple common'da (Architectury NetworkManager).
        // C2S alıcıları ORTAK init'te kaydedilir (f3-network-contract.md); S2C alıcıları
        // YALNIZCA istemci init'inde (aşağıdaki client iskeleti — dedicated server'da ÇAĞRILMAZ).
        ArcanumNetwork.registerC2SReceivers();

        // ---- MOD bus (kayıt + lifecycle) ----
        modBus.addListener(ArcanumForge::onCommonSetup);
        modBus.addListener(ArcanumForge::onEntityAttributes);

        // ---- FORGE bus (oyun-içi event'ler) ----
        MinecraftForge.EVENT_BUS.register(ArcanumForgeEvents.class);
        MinecraftForge.EVENT_BUS.register(ImperiusInputBlocker.class);
        MinecraftForge.EVENT_BUS.register(ArcanumForgeLoot.class);

        // ---- İSTEMCİ init ----
        // Fiziksel istemci UI/input/durum kayıtları (keybinding, HUD overlay, MenuScreens,
        // S2C handler → registerS2CReceivers sırası, client tick + disconnect temizliği).
        // FMLEnvironment koruması: dedicated server ArcanumForgeClient'ı hiç YÜKLEMEZ
        // (renderer/partikül kayıtları ayrıca render.ArcanumForgeRenderers'ta,
        // @Mod.EventBusSubscriber(Dist.CLIENT) ile kendiliğinden).
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            com.arcanum.forge.client.ArcanumForgeClient.init(modBus);
        }
    }

    /**
     * Registry'ler tamamlandıktan sonraki kayıt-yanı işler. {@code enqueueWork} ana
     * thread'de, senkronize koşar — statik registry'lere (TerraBlender Regions,
     * FireBlock yanabilirlik haritası, BrewingRecipeRegistry) yazmanın güvenli yeri.
     */
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // TerraBlender: fabric'teki "terrablender" entrypoint'inin Forge karşılığı —
            // Forge'da özel entrypoint yok, resmi kalıp FMLCommonSetupEvent.enqueueWork.
            ArcanumTerraBlenderForge.init();

            // Yanabilirlik — vanilla odun paritesi (log 5/5, plank 5/20, yaprak 30/60).
            // Fabric karşılığı: FlammableBlockRegistry.getDefaultInstance().add(...)
            setFlammable(ModBlocks.ARCANEWOOD_LOG.get(), 5, 5);
            setFlammable(ModBlocks.STRIPPED_ARCANEWOOD_LOG.get(), 5, 5);
            setFlammable(ModBlocks.ARCANEWOOD_PLANKS.get(), 5, 20);
            setFlammable(ModBlocks.ARCANEWOOD_LEAVES.get(), 30, 60);

            // İksir tarifleri (brewing stand) — fabric'teki
            // FabricBrewingRecipeRegistry.registerPotionRecipe zincirinin karşılığı.
            ArcanumBrewing.register();
        });
    }

    /**
     * Yaratık attribute'ları (her living entity için ZORUNLU) — fabric'teki 17
     * {@code FabricDefaultAttributeRegistry.register} kaydıyla birebir aynı liste.
     * Fark: Fabric Builder'ı kendisi build eder; Forge {@code put} build edilmiş
     * {@code AttributeSupplier} ister → {@code .build()} eklendi.
     */
    private static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.DEATH_EATER.get(), DeathEaterEntity.createAttributes().build());
        event.put(ModEntities.DEMENTOR.get(), DementorEntity.createAttributes().build());
        event.put(ModEntities.BOWTRUCKLE.get(), BowtruckleEntity.createAttributes().build());
        event.put(ModEntities.MOONCALF.get(), MooncalfEntity.createAttributes().build());
        event.put(ModEntities.BROOM.get(), BroomEntity.createAttributes().build());
        event.put(ModEntities.TROLL.get(), TrollEntity.createAttributes().build());
        event.put(ModEntities.PHOENIX.get(), PhoenixEntity.createAttributes().build());
        event.put(ModEntities.THUNDERBIRD.get(), ThunderbirdEntity.createAttributes().build());
        event.put(ModEntities.SNOWY_OWL.get(), SnowyOwlEntity.createAttributes().build());
        event.put(ModEntities.THESTRAL.get(), ThestralEntity.createAttributes().build());
        event.put(ModEntities.UNICORN.get(), UnicornEntity.createAttributes().build());
        event.put(ModEntities.HIPPOGRIFF.get(), HippogriffEntity.createAttributes().build());
        event.put(ModEntities.ACROMANTULA.get(), AcromantulaEntity.createAttributes().build());
        event.put(ModEntities.WEREWOLF.get(), WerewolfEntity.createAttributes().build());
        event.put(ModEntities.GRINDYLOW.get(), GrindylowEntity.createAttributes().build());
        event.put(ModEntities.KNEAZLE.get(), KneazleEntity.createAttributes().build());
        event.put(ModEntities.BASILISK.get(), BasiliskEntity.createAttributes().build());
        event.put(ModEntities.PATRONUS.get(), PatronusEntity.createAttributes().build());
        event.put(ModEntities.WIZARD_TRADER.get(), WizardTraderEntity.createAttributes().build());
        // Protego Diabolica alev ejderhası — Mob tabanlı olduğu için attribute kaydı
        // ZORUNLU (unutulursa ejderha doğduğu anda crash).
        event.put(ModEntities.DIABOLICA_DRAGON.get(), FiendfyreDragonEntity.createAttributes().build());
    }

    /**
     * {@code FireBlock.setFlammable(Block,int,int)} Forge 1.20.1'de de PRIVATE
     * (javap ile doğrulandı; Forge public patch'lemiyor). AT (accesstransformer.cfg)
     * eklemek yerine çift-isimli yansıma: dev ortamı mojmap ("setFlammable"),
     * üretim jar'ı SRG ("m_53444_" — srg-mapped jar'dan javap ile doğrulandı).
     * Bloklar common'da olduğundan IForgeBlock override yolu kapalı.
     */
    private static void setFlammable(Block block, int igniteOdds, int burnOdds) {
        FireBlock fire = (FireBlock) Blocks.FIRE;
        for (String name : new String[] {"setFlammable", "m_53444_"}) {
            try {
                Method m = FireBlock.class.getDeclaredMethod(name, Block.class, int.class, int.class);
                m.setAccessible(true);
                m.invoke(fire, block, igniteOdds, burnOdds);
                return;
            } catch (ReflectiveOperationException ignored) {
                // diğer ismi dene
            }
        }
        Arcanum.LOGGER.warn("[Arcanum] FireBlock.setFlammable erişilemedi — {} yanabilirliği kaydedilemedi", block);
    }
}
