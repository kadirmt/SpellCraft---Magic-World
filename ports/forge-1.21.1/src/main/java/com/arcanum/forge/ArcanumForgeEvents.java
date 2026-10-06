package com.arcanum.forge;

import com.arcanum.entity.ArcanumSurfaceSpawner;
import com.arcanum.item.CastManager;
import com.arcanum.network.ArcanumNetwork;
import com.arcanum.registry.ModBlocks;
import com.arcanum.registry.ModItems;
import com.arcanum.registry.ModPotions;
import com.arcanum.registry.ModVillagers;
import com.arcanum.spell.ArcanumLeveling;
import com.arcanum.spell.CrucioTracker;
import com.arcanum.spell.ImperiusCurseTracker;
import com.arcanum.spell.ManaRegen;
import com.arcanum.spell.UmbraFormManager;
import com.arcanum.spell.WandLockManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.brewing.BrewingRecipeRegisterEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * FORGE bus (oyun-içi) event'leri — kök {@code ArcanumFabric.onInitialize}
 * içindeki tick/bağlantı/ölüm/ticaret/iksir/komut/soyma kayıtlarının birebir
 * karşılığı. Davranış/denge değerleri kökle AYNI; yalnız kayıt mekanizması değişti.
 */
public final class ArcanumForgeEvents {
    private ArcanumForgeEvents() {}

    // ------------------------------------------------------------------
    // Sunucu tick — kök END_SERVER_TICK kayıt SIRASI korunur:
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
        // Imperio (İtaat Laneti) — "hedeflemeyi durdur" davranışı tick süpürmesiyle
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
        // Umbravolo kara duman formu — mana drenajı (21/sn) + duman FX + iz + güvenlik
        // süpürmesi (kök kayıt sırası: ManaRegen'den sonra, SurfaceSpawner'dan önce).
        UmbraFormManager.tick(server);
        // Arcanum YÜZEY SPAWNER'ı — troll vb. büyük yüzey moblarının gündüz de
        // garanti görünmesi (vanilla MONSTER mob-cap yarışını atlar).
        ArcanumSurfaceSpawner.tick(server);
    }

    // ------------------------------------------------------------------
    // Bağlantı — kök ServerPlayConnectionEvents.JOIN / DISCONNECT karşılığı
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ArcanumNetwork.syncKnownSpells(player);
            ArcanumNetwork.syncLoadout(player);
            ArcanumNetwork.syncMagicData(player);
            // Umbravolo sanitize: formdayken logout/crash/sunucu kapanışı NBT'ye 2×
            // uçuş hızı yazmış olabilir — formda OLMAYAN oyuncunun flySpeed'i tam
            // form hızıysa vanilla'ya çekilir (kalıcı 2× creative uçuşu bug'ı).
            UmbraFormManager.sanitizeOnJoin(player);
            // Umbravolo: o an formda olan oyuncuların setini geç katılan izleyiciye
            // HEMEN gönder (periyodik ~5 sn tazelemeyi beklemeden; "süzülen zırh+asa"
            // görünmesin). Herkese yeniden yayın yapılmaz — unicast.
            for (int umbraId : UmbraFormManager.activeEntityIds(player.getServer())) {
                ArcanumNetwork.sendUmbraFormTo(player, umbraId, true);
            }
        }
    }

    /** Oyuncu çıkışında cast/kenetlenme haritalarını temizle (RECENT sızıntısını kapatır) + Lumos ışığını söndür. */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CastManager.forget(player.getUUID());
            // Umbravolo: sunucu-taraf form haritalarını temizle (bayat girdi kalmasın;
            // NBT'ye yazılmış olabilecek bozuk flySpeed'i JOIN sanitize toparlar).
            UmbraFormManager.forget(player.getUUID());
            com.arcanum.spell.Spells.vulneraForget(player.getUUID()); // vulnera drenaj kesiri
            com.arcanum.item.WandItem.channelForget(player.getUUID()); // kanal başlangıç notu
            LumosLight.onLogout(player);
        }
    }

    // ------------------------------------------------------------------
    // Sunucu yaşam döngüsü — kök SERVER_STARTED / SERVER_STOPPING karşılığı
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
        // TÜM aktif Umbravolo formlarını TEMİZ kapat: ServerStoppingEvent son
        // saveAll'dan ÖNCE tetiklenir → oyuncu NBT'sine mayfly/flying/flySpeed=0.1
        // yazılmaz (yazılırsa creative uçuşu kalıcı 2× hızlanıyordu; JOIN sanitize yedek ağ).
        UmbraFormManager.exitAll(event.getServer());
    }

    // ------------------------------------------------------------------
    // Büyücü XP — kök ServerLivingEntityEvents.AFTER_DEATH karşılığı.
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
        if (entity instanceof ServerPlayer deadSp && UmbraFormManager.isActive(deadSp)) {
            UmbraFormManager.exit(deadSp);
        }
        // Öldüren: büyü indirectMagic(player,player) / ok / melee → hepsi
        // source.getEntity()'de oyuncuyu verir. getLastHurtByMob fallback'i YOK
        // (kökteki yanlış-atıf düzeltmesi aynen korunur).
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
    // Wizard köylüsü — kök TradeOfferHelper.registerVillagerOffers karşılığı.
    // Seviye 1 ticaret: Başlangıç Büyü Kitabı (ucuz, restocklanır; maxUses 12).
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() == ModVillagers.WIZARD.get()) {
            event.getTrades().get(1).add(
                    new VillagerTrades.ItemsForEmeralds(ModItems.STARTER_SPELL_BOOK.get(), 4, 12, 5));
        }
    }

    // ------------------------------------------------------------------
    // İksir tarifleri — kök FabricBrewingRecipeRegistryBuilder.BUILD karşılığı.
    // NOT: ModPotions.holderOf(...) kanonik Holder üretir (Unregistered holder
    // çökmesine karşı — kök yorumdaki gerekçe aynen geçerli). PotionBrewing
    // Builder.addMix (Holder, Item, Holder) — javap doğrulandı; kökteki
    // Ingredient.of(item) sarmalayıcısına burada gerek yok (tek item'lık girdi).
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onBrewingRegister(BrewingRecipeRegisterEvent event) {
        PotionBrewing.Builder builder = event.getBuilder();
        // Mana Hızlandırma İksiri: Garip İksir + Anka tüyü → mana yenilenmesi %40 hızlı
        builder.addMix(Potions.AWKWARD,
                ModItems.PHOENIX_FEATHER.get(), ModPotions.holderOf(ModPotions.MANA_HASTE_POTION));
        // Exstimulo: Güç İksiri + 3 farklı kuş tüyü (sırayla) → güç ×1.5, mana %20 hızlı, cooldown %20 az
        builder.addMix(Potions.STRENGTH,
                ModItems.PHOENIX_FEATHER.get(), ModPotions.holderOf(ModPotions.EXSTIMULO_STAGE1));
        builder.addMix(ModPotions.holderOf(ModPotions.EXSTIMULO_STAGE1),
                ModItems.THUNDERBIRD_FEATHER.get(), ModPotions.holderOf(ModPotions.EXSTIMULO_STAGE2));
        builder.addMix(ModPotions.holderOf(ModPotions.EXSTIMULO_STAGE2),
                ModItems.SNOWY_OWL_FEATHER.get(), ModPotions.holderOf(ModPotions.EXSTIMULO_POTION));
        // Girding: Garip İksir + Troll derisi → %40 dayanıklılık + %20 hız
        builder.addMix(Potions.AWKWARD,
                ModItems.TROLL_HIDE.get(), ModPotions.holderOf(ModPotions.GIRDING_POTION));
    }

    // ------------------------------------------------------------------
    // Arcanewood soyma — kök StrippableBlockRegistry.register karşılığı.
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
    // /arcanum komut ağacı — kök CommandRegistrationCallback karşılığı
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ArcanumCommands.register(event.getDispatcher());
    }
}
