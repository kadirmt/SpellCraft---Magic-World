package com.arcanum;

import com.arcanum.entity.ArcanumSurfaceSpawner;
import com.arcanum.item.CastManager;
import com.arcanum.item.WandItem;
import com.arcanum.network.ArcanumNetwork;
import com.arcanum.registry.ModItems;
import com.arcanum.registry.ModPotions;
import com.arcanum.server.ImperiusInputBlocker;
import com.arcanum.server.LumosLight;
import com.arcanum.spell.ArcanumLeveling;
import com.arcanum.spell.CrucioTracker;
import com.arcanum.spell.ImperiusCurseTracker;
import com.arcanum.spell.ManaRegen;
import com.arcanum.spell.Spells;
import com.arcanum.spell.UmbraFormManager;
import com.arcanum.spell.WandLockManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;

/**
 * Ortak oyun-içi olay köprüleri — kök {@code ArcanumFabric.onInitialize} içindeki olay kayıtlarının
 * GÖVDELERİ. İki loader da AYNI statik metotları çağırır; loader yalnız olayı yakalar:
 * <ul>
 *   <li>Fabric: {@code ServerTickEvents.END_SERVER_TICK}, {@code ServerLifecycleEvents}, {@code ServerPlayConnectionEvents},
 *       {@code ServerLivingEntityEvents.AFTER_DEATH}, interaction callback'leri, {@code FabricPotionBrewingBuilder.BUILD}.</li>
 *   <li>Forge: {@code TickEvent.ServerTickEvent.Post}, {@code ServerStarted/StoppingEvent}, {@code PlayerLoggedIn/OutEvent},
 *       {@code LivingDeathEvent} (LOWEST, iptal edilmemişse), interaction event'leri, {@code BrewingRecipeRegisterEvent}.</li>
 * </ul>
 * Loot → {@link com.arcanum.server.ArcanumLoot}, komutlar → {@link com.arcanum.server.ArcanumCommands},
 * biyom değişiklikleri → {@link com.arcanum.server.ArcanumBiomeModifications}.
 */
public final class ArcanumEvents {
    private ArcanumEvents() {}

    // ------------------------------------------------------------------
    // Sunucu tick — kök END_SERVER_TICK kayıt SIRASI (Fabric callback'leri kayıt sırasıyla çağırır):
    // LumosLight.register → ImperiusCurseTicker.register (kök fabric sınıfları) → CastManager → WandLockManager
    // → CrucioTracker → ManaRegen → UmbraFormManager → ArcanumSurfaceSpawner.
    // Forge'da tek dinleyicide aynı sırayla çağrılır → birebir eşleşir.
    // ------------------------------------------------------------------
    public static void onServerTickEnd(MinecraftServer server) {
        // Lumos takip-ışığı (asayı tutan oyuncuyu izleyen görünmez light bloğu)
        LumosLight.tick(server);
        // Imperio (İtaat Laneti) — "hedeflemeyi durdur" davranışı Mixin'siz, tick süpürmesiyle
        ImperiusCurseTracker.tick(server);
        // Büyü cast zamanlayıcısı — asa artık BASILI-TUT kanallamaz. WandItem.use TEK BASIŞ
        // ile bir cast başlatır, CastManager her sunucu tick'inde geri sayar ve süre dolunca
        // büyüyü ateşler (bkz. CastManager / WandItem). Telegraf yalnızca zeminde tek çember.
        CastManager.tick(server);
        // Asa kenetlenmesi (Priori Incantatem) yöneticisi — CastManager kardeşi, her tick
        // düğümü kaydırır/mana yakar/çözer. Kenetlenme CastManager fire kancasında başlatılır.
        WandLockManager.tick(server);
        // Crucio (Cruciatus Laneti) — kalp-bazlı DoT + debuff tazeleme (ImperiusCurseTicker deseni).
        CrucioTracker.tick(server);
        // 10. tur: oyuncu-bazlı MANA yenilenmesi (mana artık asada değil).
        ManaRegen.tick(server);
        // Umbravolo kara duman formu — mana drenajı (21/sn) + duman FX + iz + güvenlik süpürmesi.
        UmbraFormManager.tick(server);
        // Arcanum YÜZEY SPAWNER'ı — troll vb. büyük yüzey moblarının gündüz de garanti
        // görünmesini sağlar (vanilla MONSTER mob-cap yarışını atlar; bkz. sınıf notu).
        ArcanumSurfaceSpawner.tick(server);
    }

    // ------------------------------------------------------------------
    // Sunucu yaşam döngüsü — kök SERVER_STARTED / SERVER_STOPPING kayıt sırası:
    // LumosLight → ImperiusCurseTicker → (STOPPING'de) UmbraFormManager.exitAll
    // ------------------------------------------------------------------
    public static void onServerStarted(MinecraftServer server) {
        // SP'de aynı JVM içinde yeni dünya açılışına bayat girdi taşınmasın
        LumosLight.onServerStarted();
        ImperiusCurseTracker.clear();
    }

    /** Son saveAll'dan ÖNCE çağrılmalı (Fabric SERVER_STOPPING / Forge ServerStoppingEvent ikisi de öyle). */
    public static void onServerStopping(MinecraftServer server) {
        // Sunucu kapanırken (final save'den önce) tüm Lumos ışıklarını süpür
        LumosLight.onServerStopping(server);
        ImperiusCurseTracker.clear();
        // Sunucu kapanırken TÜM aktif Umbravolo formlarını temiz kapat: SERVER_STOPPING
        // son saveAll'dan ÖNCE tetiklenir → oyuncu NBT'sine mayfly/flying/flySpeed=0.1
        // yazılmaz (yazılırsa creative uçuşu kalıcı 2× hızlanıyordu; JOIN sanitize yedek ağ).
        UmbraFormManager.exitAll(server);
    }

    // ------------------------------------------------------------------
    // Bağlantı — kök ServerPlayConnectionEvents.JOIN / DISCONNECT (SUNUCU ANA THREAD'i)
    // ------------------------------------------------------------------
    public static void onPlayerJoin(ServerPlayer player) {
        ArcanumNetwork.syncKnownSpells(player);
        ArcanumNetwork.syncLoadout(player);
        ArcanumNetwork.syncMagicData(player);
        // Umbravolo sanitize: formdayken logout/crash/sunucu kapanışı NBT'ye 2×
        // flySpeed (0.1) sızdırmış olabilir (vanilla login flySpeed'i SIFIRLAMAZ) —
        // formda olmayan oyuncuda tespit edilirse vanilla 0.05'e geri çekilir.
        UmbraFormManager.sanitizeOnJoin(player);
        // Umbravolo: o an formda olan oyuncuların setini geç katılan izleyiciye HEMEN
        // gönder — periyodik RESYNC beklenirse 5 sn'ye kadar "süzülen zırh+asa" görünür.
        for (int umbraId : UmbraFormManager.activeEntityIds(player.level().getServer())) {
            ArcanumNetwork.sendUmbraFormTo(player, umbraId, true);
        }
    }

    /**
     * Oyuncu çıkışında cast/kenetlenme/duman-formu yardımcı haritalarını temizle
     * (RECENT sızıntısını kapatır; Umbravolo formu logout'ta temiz kapanır) + Lumos ışığını söndür.
     * Kök sıra: ArcanumFabric DISCONNECT (4 temizlik) → LumosLight DISCONNECT.
     */
    public static void onPlayerLeave(ServerPlayer player) {
        CastManager.forget(player.getUUID());
        UmbraFormManager.forget(player.getUUID());
        Spells.vulneraForget(player.getUUID()); // vulnera drenaj kesiri
        WandItem.channelForget(player.getUUID()); // kanal başlangıç notu (+ Diabolica ejderhası haritası)
        // Çıkan oyuncunun ışığını hemen söndür — dünyada yetim blok kalmasın
        LumosLight.onPlayerLeave(player);
    }

    // ------------------------------------------------------------------
    // 10. tur: büyücü XP — bir yaratığı öldüren oyuncuya XP ver (hostile/pasif/custom/boss farklı).
    // Kök ServerLivingEntityEvents.AFTER_DEATH. Forge: LivingDeathEvent LOWEST + yalnız iptal
    // EDİLMEMİŞ ölüm (iptal edilen ölüme XP yazılmaz — AFTER_DEATH semantiği).
    // ------------------------------------------------------------------
    public static void onLivingDeath(LivingEntity entity, DamageSource source) {
        if (entity.level().isClientSide()) {
            return;
        }
        // Umbravolo: ölen oyuncunun duman formunu ANINDA sök (tick süpürmesini beklemeden
        // izleyen istemcilerin gizleme seti temizlensin; abilities respawn'da zaten sıfırlanır).
        if (entity instanceof ServerPlayer deadSp && UmbraFormManager.isActive(deadSp)) {
            UmbraFormManager.exit(deadSp);
        }
        // Öldüren: büyü indirectMagic(player,player) / ok / melee → hepsi source.getEntity()'de
        // oyuncuyu verir. getLastHurtByMob fallback'i KALDIRILDI: mob 5sn içinde ortam/başka-mob
        // ile ölürse en son vuran oyuncuya hak etmediği XP yazıyordu (yanlış-atıf).
        if (source.getEntity() instanceof ServerPlayer p) {
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
    // Imperio (İtaat Laneti) + Umbravolo girdi kesme — kökteki ImperiusInputBlocker'ın 5 callback'i.
    // true = girdiyi ENGELLE (Fabric: FAIL / false; Forge: iptal, BreakEvent'te Result.DENY).
    // Mantık ImperiusInputBlocker'da; burası loader'ların tek çağrı noktası.
    // ------------------------------------------------------------------

    /** Sol tık — varlığa saldırı. Engellemiyorsa ve sunucudaysa Imperio "saldır" komutunu kaydeder. */
    public static boolean shouldBlockAttackEntity(Player player, Level level, Entity target) {
        return ImperiusInputBlocker.shouldBlockAttackEntity(player, level, target);
    }

    /** Sol tık — blok kırmayı başlatma. */
    public static boolean shouldBlockAttackBlock(Player player) {
        return ImperiusInputBlocker.shouldBlockAttackBlock(player);
    }

    /** Sağ tık — item kullan (duman formunda ASA MUAF). */
    public static boolean shouldBlockUseItem(Player player, InteractionHand hand) {
        return ImperiusInputBlocker.shouldBlockUseItem(player, hand);
    }

    /** Sağ tık — bloğa etkileşim (duman formunda ASA MUAF). */
    public static boolean shouldBlockUseBlock(Player player, InteractionHand hand) {
        return ImperiusInputBlocker.shouldBlockUseBlock(player, hand);
    }

    /** Blok kırmayı tamamen engelle. */
    public static boolean shouldBlockBlockBreak(Player player) {
        return ImperiusInputBlocker.shouldBlockBlockBreak(player);
    }

    // ------------------------------------------------------------------
    // İksir tarifleri (brewing stand) — normal büyücülük tezgahı reçeteleriyle (crafting)
    // karıştırılmasın: bunlar data-driven değil, kod ile kaydediliyor (vanilla kısıtı).
    // Kök FabricBrewingRecipeRegistryBuilder.BUILD → registerPotionRecipe(Holder, Ingredient.of(item), Holder);
    // 26.x: vanilla PotionBrewing.Builder.addMix(Holder, Item, Holder) aynı Mix'i (Ingredient.of(item)) üretir.
    // NOT: RegistrySupplier.holder() kanonik (kayıtlı) Holder.Reference döner — kökteki
    // ModPotions.holderOf(...) gerekçesi (Unregistered holder çökmesi) aynen karşılanır.
    // ------------------------------------------------------------------
    public static void onRegisterBrewing(PotionBrewing.Builder builder) {
        // Mana Hızlandırma İksiri: Garip İksir + Anka tüyü → mana yenilenmesi %40 hızlı
        builder.addMix(Potions.AWKWARD,
                ModItems.PHOENIX_FEATHER.get(), ModPotions.MANA_HASTE_POTION.holder());
        // Exstimulo: Güç İksiri + 3 farklı kuş tüyü (sırayla) → güç ×1.5, mana %20 hızlı, cooldown %20 az
        builder.addMix(Potions.STRENGTH,
                ModItems.PHOENIX_FEATHER.get(), ModPotions.EXSTIMULO_STAGE1.holder());
        builder.addMix(ModPotions.EXSTIMULO_STAGE1.holder(),
                ModItems.THUNDERBIRD_FEATHER.get(), ModPotions.EXSTIMULO_STAGE2.holder());
        builder.addMix(ModPotions.EXSTIMULO_STAGE2.holder(),
                ModItems.SNOWY_OWL_FEATHER.get(), ModPotions.EXSTIMULO_POTION.holder());
        // Girding: Garip İksir + Troll derisi → %40 dayanıklılık + %20 hız
        builder.addMix(Potions.AWKWARD,
                ModItems.TROLL_HIDE.get(), ModPotions.GIRDING_POTION.holder());
    }
}
