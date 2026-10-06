package com.arcanum.forge;

import com.arcanum.Arcanum;
import com.arcanum.forge.worldgen.ArcanumTerraBlenderForge;
import com.arcanum.network.ArcanumNetwork;
import com.arcanum.registry.ModBlocks;
import com.arcanum.registry.ModEntities;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Arcanum — MinecraftForge 1.21.1 giriş noktası (kök {@code ArcanumFabric}'in
 * loader karşılığı; bkz. ports/_planning/f6-registry-contract.md ve
 * f6-network-contract.md).
 *
 * <p>Kökteki {@code onInitialize} gövdesinin Forge dağılımı:
 * <ul>
 *   <li><b>Mod ctor:</b> {@code Arcanum.init(modBus)} (13 DeferredRegister +
 *       ModSpells) + {@code ArcanumNetwork.register()} (kanal, handshake'ten
 *       önce şart) + MOD/FORGE bus dinleyici kayıtları.</li>
 *   <li><b>MOD bus:</b> attribute'lar ({@code EntityAttributeCreationEvent}),
 *       spawn placement'lar ({@code SpawnPlacementRegisterEvent}) — ikisi de
 *       registry sözleşmesindeki hazır metotlara delege.</li>
 *   <li><b>FMLCommonSetupEvent.enqueueWork:</b> registry'lere dokunan geç işler —
 *       yanabilirlik (Fabric {@code FlammableBlockRegistry} karşılığı) +
 *       TerraBlender bölge/yüzey kuralı kaydı (Forge'da "terrablender"
 *       entrypoint'i yok).</li>
 *   <li><b>FORGE bus:</b> tick/lifecycle/ölüm/komut/ticaret/iksir/soyma
 *       ({@link ArcanumForgeEvents}), Imperio girdi iptali
 *       ({@link ImperiusInputBlocker}), loot enjeksiyonu ({@link ArcanumLootForge}).</li>
 *   <li><b>Data:</b> kökteki 11 vanilla-biyom {@code addSpawn} +
 *       3 {@code addFeature} çağrısı koddan değil
 *       {@code data/arcanum/forge/biome_modifier/*.json} dosyalarından gelir
 *       (forge:add_spawns / forge:add_features). Özel biyomların (gloomwood,
 *       arcanewood_grove) spawn listeleri biyom JSON'unda native durur —
 *       ÇİFTE KAYIT YAPMA.</li>
 * </ul>
 */
@Mod(ArcanumForge.MOD_ID)
public class ArcanumForge {
    public static final String MOD_ID = Arcanum.MODID;
    private static final Logger LOGGER = LogUtils.getLogger();

    public ArcanumForge(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        // 1) Registry katmanı — DR'ler RegisterEvent'i mod bus'tan dinler.
        //    DİKKAT: bu çağrı döndüğünde registry'ler henüz BOŞ (sözleşme);
        //    .get()/holderOf() burada ÇAĞRILMAZ.
        Arcanum.init(modBus);

        // 2) Ağ kanalı — ctor'da BİR KEZ (kanal listesi login handshake'inde
        //    bildirilir; commonSetup'a ertelemek geç kalma riski taşır).
        //    C2S alıcı gövdeleri (SelectSpell..Respec) ArcanumNetwork içinde.
        ArcanumNetwork.register();

        // 3) MOD bus — registry sözleşmesinin iki hazır delegasyonu.
        modBus.addListener(ModEntities::registerAttributes);       // 19 attribute (PATRONUS + WIZARD_TRADER dahil)
        modBus.addListener(ModEntities::registerSpawnPlacements);  // 15 placement
        modBus.addListener(this::commonSetup);

        // 4) FORGE bus — oyun-içi event'ler (statik @SubscribeEvent sınıfları).
        MinecraftForge.EVENT_BUS.register(ArcanumForgeEvents.class);
        MinecraftForge.EVENT_BUS.register(ImperiusInputBlocker.class);
        MinecraftForge.EVENT_BUS.register(ArcanumLootForge.class);

        LOGGER.info("[Arcanum] Forge 1.21.1 loader katmanı kuruldu (ağ + event bus kayıtları)");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Yanabilirlik — vanilla odun paritesi (log 5/5, plank 5/20, yaprak 30/60).
            // Fabric'teki FlammableBlockRegistry.add karşılığı; FireBlock.setFlammable
            // Forge'da public patch'lidir (javap doğrulandı). Argüman sırası vanilla
            // FireBlock.bootstrap ile aynı: (igniteOdds, burnOdds).
            FireBlock fire = (FireBlock) Blocks.FIRE;
            fire.setFlammable(ModBlocks.ARCANEWOOD_LOG.get(), 5, 5);
            fire.setFlammable(ModBlocks.STRIPPED_ARCANEWOOD_LOG.get(), 5, 5);
            fire.setFlammable(ModBlocks.ARCANEWOOD_PLANKS.get(), 5, 20);
            fire.setFlammable(ModBlocks.ARCANEWOOD_LEAVES.get(), 30, 60);

            // TerraBlender — bölge + gloomwood yüzey kuralları. commonSetup
            // kayıtlardan sonra koştuğu için Fabric'teki listen(...) sıralı-yükleme
            // hilesine gerek yok; GLOOM_GRASS_BLOCK.get() burada güvenli.
            ArcanumTerraBlenderForge.init();

            LOGGER.info("[Arcanum] Common setup tamam (yanabilirlik + TerraBlender)");
        });
    }
}
