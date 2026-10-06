package com.arcanum.forge;

import com.arcanum.entity.ArcanumSurfaceSpawner;
import com.arcanum.item.CastManager;
import com.arcanum.network.ArcanumNetwork;
import com.arcanum.registry.ModBlocks;
import com.arcanum.registry.ModItems;
import com.arcanum.registry.ModVillagers;
import com.arcanum.spell.ArcanumLeveling;
import com.arcanum.spell.CrucioTracker;
import com.arcanum.spell.ImperiusCurseTracker;
import com.arcanum.spell.ManaRegen;
import com.arcanum.spell.WandLockManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * FORGE bus (oyun-içi) event'leri — fabric'teki {@code ArcanumFabric.onInitialize}
 * içindeki tick/bağlantı/ölüm/ticaret/komut/soyma kayıtlarının birebir karşılığı.
 * Davranış/denge değerleri fabric ile AYNI; yalnız kayıt mekanizması değişti.
 * (javap doğrulama: forge-1.20.1-47.4.10 merged jar — TickEvent.ServerTickEvent.getServer(),
 * PlayerEvent.PlayerLoggedIn/OutEvent, LivingDeathEvent, VillagerTradesEvent,
 * BlockEvent.BlockToolModificationEvent + ToolActions.AXE_STRIP, RegisterCommandsEvent.)
 */
public final class ArcanumForgeEvents {
    private ArcanumForgeEvents() {}

    // ------------------------------------------------------------------
    // Sunucu tick — fabric END_SERVER_TICK kayıt SIRASI korunur:
    // LumosLight → ImperiusCurseTracker → CastManager → WandLockManager
    // → CrucioTracker → ManaRegen → ArcanumSurfaceSpawner
    // (Fabric, callback'leri kayıt sırasıyla çağırır; burada tek handler'da
    // aynı sırayla çağrılarak birebir eşlenir.)
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        // Lumos takip-ışığı (asayı tutan oyuncuyu izleyen görünmez light bloğu)
        LumosLight.tick(server);
        // Imperio (İtaat Laneti) — "hedeflemeyi durdur" davranışı Mixin'siz, tick süpürmesiyle
        ImperiusCurseTracker.tick(server);
        // Büyü cast zamanlayıcısı — WandItem.use TEK BASIŞ ile cast başlatır,
        // CastManager geri sayar ve süre dolunca büyüyü ateşler.
        CastManager.tick(server);
        // Asa kenetlenmesi (Priori Incantatem) — düğüm kaydırma/mana yakma/çözme.
        WandLockManager.tick(server);
        // Crucio (Cruciatus Laneti) — kalp-bazlı DoT + debuff tazeleme.
        CrucioTracker.tick(server);
        // 10. tur: oyuncu-bazlı MANA yenilenmesi (mana artık asada değil).
        ManaRegen.tick(server);
        // Umbravolo kara duman formu — mana drenajı (21/sn) + duman FX + iz + güvenlik süpürmesi.
        com.arcanum.spell.UmbraFormManager.tick(server);
        // Arcanum YÜZEY SPAWNER'ı — troll vb. büyük yüzey moblarının gündüz de
        // garanti görünmesi (vanilla MONSTER mob-cap yarışını atlar; bkz. sınıf notu).
        ArcanumSurfaceSpawner.tick(server);
    }

    // ------------------------------------------------------------------
    // Bağlantı — fabric ServerPlayConnectionEvents.JOIN / DISCONNECT karşılığı
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ArcanumNetwork.syncKnownSpells(player);
            ArcanumNetwork.syncLoadout(player);
            ArcanumNetwork.syncMagicData(player);
            // Umbravolo sanitize: formdayken logout/crash/sunucu kapanışı NBT'ye 2×
            // flySpeed (0.1) sızdırmış olabilir (vanilla login flySpeed'i SIFIRLAMAZ) —
            // formda olmayan oyuncuda tespit edilirse vanilla 0.05'e geri çekilir.
            com.arcanum.spell.UmbraFormManager.sanitizeOnJoin(player);
            // Umbravolo: o an formda olan oyuncuların setini geç katılan izleyiciye HEMEN
            // gönder — periyodik RESYNC beklenirse 5 sn'ye kadar "süzülen zırh+asa" görünür.
            for (int umbraId : com.arcanum.spell.UmbraFormManager.activeEntityIds(player.getServer())) {
                ArcanumNetwork.sendUmbraFormTo(player, umbraId, true);
            }
        }
    }

    /** Oyuncu çıkışında cast/kenetlenme haritalarını temizle (RECENT sızıntısını kapatır) + Lumos ışığını söndür. */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CastManager.forget(player.getUUID());
            // Umbravolo formu logout'ta temiz unutulur (JOIN sanitize NBT sızıntısını toparlar).
            com.arcanum.spell.UmbraFormManager.forget(player.getUUID());
            com.arcanum.spell.Spells.vulneraForget(player.getUUID()); // vulnera drenaj kesiri
            com.arcanum.item.WandItem.channelForget(player.getUUID()); // kanal başlangıç notu
            LumosLight.onLogout(player);
        }
    }

    // ------------------------------------------------------------------
    // Sunucu yaşam döngüsü — fabric SERVER_STARTED / SERVER_STOPPING karşılığı
    // (LumosLight.register + ImperiusCurseTicker.register içindeki kayıtlar)
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // SP'de aynı JVM içinde yeni dünya açılışına bayat girdi taşınmasın
        LumosLight.onServerStarted();
        ImperiusCurseTracker.clear();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Sunucu kapanırken (final save'den önce) tüm Lumos ışıklarını süpür
        LumosLight.onServerStopping(event.getServer());
        ImperiusCurseTracker.clear();
        // TÜM aktif Umbravolo formlarını temiz kapat: son saveAll'dan ÖNCE tetiklenir →
        // oyuncu NBT'sine mayfly/flying/flySpeed=0.1 yazılmaz (yazılırsa creative uçuşu
        // kalıcı 2× hızlanıyordu; JOIN sanitize yedek ağ).
        com.arcanum.spell.UmbraFormManager.exitAll(event.getServer());
    }

    // ------------------------------------------------------------------
    // Büyücü XP — fabric ServerLivingEntityEvents.AFTER_DEATH karşılığı.
    // LOWEST öncelik: Fabric AFTER_DEATH ölüm KESİNLEŞTİKTEN sonra çalışır;
    // LivingDeathEvent iptal edilebilir olduğundan en sona kayıt olup iptal
    // edilmiş event'i hiç görmemek (varsayılan receiveCanceled=false) aynı
    // semantiği verir — iptal edilen ölüme XP yazılmaz.
    // ------------------------------------------------------------------
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        // Umbravolo: ölen oyuncunun duman formunu ANINDA sök (tick süpürmesini beklemeden
        // izleyen istemcilerin gizleme seti temizlensin; abilities respawn'da zaten sıfırlanır).
        if (entity instanceof ServerPlayer deadSp
                && com.arcanum.spell.UmbraFormManager.isActive(deadSp)) {
            com.arcanum.spell.UmbraFormManager.exit(deadSp);
        }
        // Öldüren: büyü indirectMagic(player,player) / ok / melee → hepsi
        // source.getEntity()'de oyuncuyu verir. getLastHurtByMob fallback'i YOK:
        // mob 5sn içinde ortam/başka-mob ile ölürse en son vuran oyuncuya hak
        // etmediği XP yazıyordu (fabric'teki yanlış-atıf düzeltmesi aynen korunur).
        if (event.getSource().getEntity() instanceof ServerPlayer p) {
            int xp = ArcanumLeveling.xpForKill(entity);
            if (xp == 2 && p.getRandom().nextBoolean()) {
                xp = 0; // pasif mob → %50 şans (hostile'ın yarısı)
            }
            if (xp > 0) {
                ArcanumLeveling.addXp(p, xp);
            }
        }
    }

    // ------------------------------------------------------------------
    // Wizard köylüsü — fabric TradeOfferHelper.registerVillagerOffers karşılığı.
    // Seviye 1 ticaret: Başlangıç Büyü Kitabı (ucuz, restocklanır).
    // "maxUses" restocklanabilir normal bir sayı (12) — tek kullanımlık olsaydı
    // köy başına yalnızca 1 oyuncu satın alabilirdi, bu istenmiyor.
    // NOT: Fabric'teki VillagerTrades.ItemsForEmeralds 1.20.1'de PACKAGE-PRIVATE
    // (fabric tarafında Fabric API'nin transitive access widener'ı public yapıyor;
    // Forge'da bu widener yok). Bu yüzden public ItemListing arayüzü lambda ile
    // implemente edilir; üretilen MerchantOffer, ItemsForEmeralds(Item,4,12,5)
    // 4-arg ctor'unun bytecode'uyla BİREBİR aynıdır (javap doğrulandı):
    // bedel=4 zümrüt, sonuç=12 kitap, maxUses=12 (ctor sabiti), xp=5, çarpan=0.05F.
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() == ModVillagers.WIZARD.get()) {
            event.getTrades().get(1).add((VillagerTrades.ItemListing) (trader, random) ->
                    new MerchantOffer(
                            new ItemStack(Items.EMERALD, 4),
                            new ItemStack(ModItems.STARTER_SPELL_BOOK.get(), 12),
                            12, 5, 0.05F));
        }
    }

    // ------------------------------------------------------------------
    // Arcanewood: baltayla soyma — fabric StrippableBlockRegistry.register karşılığı.
    // Simülasyon dahil finalState döndürülür (yan etkimiz yok; AxeItem'ın
    // canPerformAction ön-kontrolü simulated=true ile gelir).
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (event.getToolAction() == ToolActions.AXE_STRIP
                && event.getState().is(ModBlocks.ARCANEWOOD_LOG.get())) {
            event.setFinalState(
                    ModBlocks.STRIPPED_ARCANEWOOD_LOG.get().withPropertiesOf(event.getState()));
        }
    }

    // ------------------------------------------------------------------
    // /arcanum komut ağacı — fabric CommandRegistrationCallback karşılığı
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ArcanumCommands.register(event.getDispatcher());
    }
}
