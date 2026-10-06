package com.arcanum.fabric;

import com.arcanum.Arcanum;
import com.arcanum.ArcanumEvents;
import com.arcanum.fabric.platform.FabricPlatform;
import com.arcanum.fabric.worldgen.ArcanumBiomeModificationsFabric;
import com.arcanum.platform.Platform;
import com.arcanum.server.ArcanumCommands;
import com.arcanum.server.ArcanumLoot;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.registry.FabricPotionBrewingBuilder;
import net.minecraft.world.InteractionResult;

/**
 * Fabric ana giriş noktası (server + client ortak). İnce katman — oyun mantığı ortak
 * {@link Arcanum} / {@link ArcanumEvents} / {@code com.arcanum.server.*} içinde; burada yalnız
 * Fabric olayları ortak köprülere bağlanır.
 *
 * <p>Sıra (iki loader'da AYNI — g2-platform-contract §2): Platform.init → Arcanum.init → kuyrukları boşalt
 * (attribute / spawn placement / POI / soyma) → Arcanum.commonSetup → biyom tablosu → olay bağlama.
 * Olay kayıt sırası kökteki ArcanumFabric.onInitialize ile aynı tutuldu; sunucu tick'i TEK dinleyicide
 * ({@link ArcanumEvents#onServerTickEnd}) kök kayıt sırasıyla çalışır.
 */
public final class ArcanumFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricPlatform platform = new FabricPlatform();
        Platform.init(platform);          // 1) HER ŞEYDEN ÖNCE
        Arcanum.init();                   // 2) kayıtlar (Fabric'te register() anında bağlanır) + ağ + kuyruklar
        platform.drainDeferred();         // 3) attribute + spawn placement + POI kuyruğu
        Arcanum.commonSetup();            // 4) kayıt sonrası ortak kurulum (.get() serbest): soyma, yanabilirlik, yüzey kuralları

        // Bağlantı — JOIN sunucu ana thread'ine alınır (execute: zaten o thread'deyse anında çalışır)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> ArcanumEvents.onPlayerJoin(handler.player)));
        // Oyuncu çıkışında cast/kenetlenme/duman-formu yardımcı haritalarını temizle + Lumos ışığını söndür
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                ArcanumEvents.onPlayerLeave(handler.player));

        // Arcane ruin / Ölümyiyen kampı yerleşimleri + yaratık spawn'ları (ortak tablo, kökle birebir)
        ArcanumBiomeModificationsFabric.register();

        // Sandık loot enjeksiyonu — sadece vanilla/mod'un kendi tablolarını değiştir; datapack override'larına dokunma
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (!source.isBuiltin()) return;
            ArcanumLoot.modify(key, tableBuilder::withPool);
        });

        // Sunucu tick sonu — Lumos → Imperius → CastManager → WandLockManager → CrucioTracker → ManaRegen
        // → UmbraFormManager → ArcanumSurfaceSpawner (kök END_SERVER_TICK kayıt sırası, tek dinleyicide)
        ServerTickEvents.END_SERVER_TICK.register(ArcanumEvents::onServerTickEnd);
        // SERVER_STOPPING son saveAll'dan ÖNCE tetiklenir (Umbravolo exitAll + Lumos/Imperius temizliği)
        ServerLifecycleEvents.SERVER_STOPPING.register(ArcanumEvents::onServerStopping);
        ServerLifecycleEvents.SERVER_STARTED.register(ArcanumEvents::onServerStarted);

        // 10. tur: büyücü XP — bir yaratığı öldüren oyuncuya XP ver (+ Umbravolo ölüm sökümü)
        ServerLivingEntityEvents.AFTER_DEATH.register(ArcanumEvents::onLivingDeath);

        // Wizard köylüsü ticareti (Başlangıç Büyü Kitabı): 26.x'te DATA-DRIVEN
        // (data/arcanum/villager_trade + trade_set) — kod kaydı yok (ARCHITECTURE §5).

        // Imperio (İtaat Laneti) + Umbravolo: kurbanın/dumanın kendi sol/sağ tıklarını iptal et
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) ->
                ArcanumEvents.shouldBlockAttackEntity(player, level, entity)
                        ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                ArcanumEvents.shouldBlockAttackBlock(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseItemCallback.EVENT.register((player, level, hand) ->
                ArcanumEvents.shouldBlockUseItem(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) ->
                ArcanumEvents.shouldBlockUseBlock(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
        // Blok kırmayı tamamen engelle (false = iptal)
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                !ArcanumEvents.shouldBlockBlockBreak(player));

        // İksir tarifleri (brewing stand)
        FabricPotionBrewingBuilder.BUILD.register(ArcanumEvents::onRegisterBrewing);

        // /arcanum komut ağacı (OP seviye 2)
        CommandRegistrationCallback.EVENT.register(ArcanumCommands::register);
    }
}
