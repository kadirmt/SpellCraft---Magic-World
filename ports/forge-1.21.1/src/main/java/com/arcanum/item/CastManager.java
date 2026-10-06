package com.arcanum.item;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.arcanum.network.ArcanumNetwork;
import com.arcanum.spell.ModSpells;
import com.arcanum.spell.Spell;
import com.arcanum.spell.SpellFx;
import com.arcanum.spell.WandLockManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/**
 * Bekleyen büyü cast'lerini tutan sunucu-tarafı zamanlayıcı.
 *
 * <p>Asa artık BASILI-TUT kanallamaz: {@link WandItem#use} TEK BASIŞ ile bir cast
 * başlatır, bu yönetici her sunucu tick'inde ({@code ServerTickEvents.END_SERVER_TICK})
 * geri sayar ve süre dolunca büyüyü ATEŞLER ({@link WandItem#executeCast}). Cast
 * sırasında SADECE zeminde tek bir rün çemberi telegrafı gösterilir — göğüs/göz hizası
 * halkası YOK, yoğunlaşma YOK (kullanıcı isteği: "çok fazla run çıkıyor").
 *
 * <p>Oyuncu başına tek cast (UUID anahtarı). Tümü sunucu ana thread'inde çalışır; yine
 * de null tutmayan {@link ConcurrentHashMap} kullanılır. Mana ve cooldown yalnızca ateş
 * anında ({@link WandItem#executeCast} → castSelected) düşer; erken iptal hiçbir şey
 * yakmaz.
 */
public final class CastManager {
    private CastManager() {}

    /** Telegraf rün çemberini kaç sunucu tick'inde bir çiz. */
    private static final int TELEGRAPH_INTERVAL = 5;

    private record Cast(int spellIndex, int ticksLeft) {}

    private static final Map<UUID, Cast> CASTS = new ConcurrentHashMap<>();

    /**
     * Bir oyuncunun EN SON ateşlediği saldırı büyüsünün kaydı (asa kenetlenmesi tespiti
     * için). {@link WandLockManager#tryStart} "karşı taraf az önce BENİ hedefleyerek
     * ateşledi mi?" kontrolünde {@link #targetId} (ateş anında nişan alınan oyuncu; yoksa
     * null) ve zamanı ({@link #gameTime}) kullanır — bakış yönü değil, GERÇEK hedef, ki
     * bir mob'a/boşluğa atılan büyü alakasız bir bystander'ı düelloya sürüklemesin.
     */
    public record RecentFire(int spellIndex, long gameTime, UUID targetId) {}

    private static final Map<UUID, RecentFire> RECENT = new ConcurrentHashMap<>();

    /** Oyuncunun şu an bekleyen bir cast'i var mı? (aynı anda tek cast kısıtı için) */
    public static boolean isCasting(Player p) {
        return CASTS.containsKey(p.getUUID());
    }

    /** Oyuncunun beklemekte olan cast'inin büyü index'i; yoksa -1. */
    public static int castingSpellIndex(UUID id) {
        Cast c = CASTS.get(id);
        return c == null ? -1 : c.spellIndex();
    }

    /** Oyuncunun beklemekte olan cast'inin kalan tick'i; yoksa -1. */
    public static int castingTicksLeft(UUID id) {
        Cast c = CASTS.get(id);
        return c == null ? -1 : c.ticksLeft();
    }

    /** Oyuncunun en son saldırı ateşleme kaydı (kenetlenme penceresi); yoksa null. */
    public static RecentFire recentFire(UUID id) {
        return RECENT.get(id);
    }

    /**
     * Bir saldırı büyüsü ateşlendiğini "az önce ateşledi" penceresine yaz — ateş anında
     * nişan alınan OYUNCU (mob/blok/boşluk değil) da kaydedilir.
     */
    public static void noteFire(ServerPlayer p, int spellIndex) {
        UUID targetId = WandLockManager.aimedPlayerId(p.serverLevel(), p);
        RECENT.put(p.getUUID(), new RecentFire(spellIndex, p.level().getGameTime(), targetId));
    }

    /** Oyuncu çıkışında/kaldırıldığında sunucu-taraf haritalarını temizle (RECENT sızıntısını kapatır). */
    public static void forget(UUID id) {
        RECENT.remove(id);
        CASTS.remove(id); // CASTS zaten tick'te budanır; simetri + erken temizlik için
    }

    /** Yeni bir cast başlat — {@code castTimeTicks} tick sonra ateşlenir (min 1). */
    public static void start(Player p, int spellIndex, int castTimeTicks) {
        int total = Math.max(1, castTimeTicks);
        CASTS.put(p.getUUID(), new Cast(spellIndex, total));
        // istemci cast göstergesini başlat (server-authoritative cast durumu)
        if (p instanceof ServerPlayer sp) {
            ArcanumNetwork.syncCastState(sp, spellIndex, total);
        }
    }

    /** Bekleyen cast'i iptal et (ateşlemeden). */
    public static void cancel(Player p) {
        CASTS.remove(p.getUUID());
        // istemci göstergesini temizle
        if (p instanceof ServerPlayer sp) {
            ArcanumNetwork.syncCastState(sp, -1, 0);
        }
    }

    /**
     * Her sunucu tick'inde: her bekleyen cast için oyuncuyu bul; yok/ölü/kaldırılmış
     * ya da artık asa tutmuyorsa iptal et. Aksi halde telegrafı çiz ve geri say;
     * {@code ticksLeft <= 0} olunca büyüyü ateşle ({@link WandItem#executeCast}).
     */
    public static void tick(MinecraftServer server) {
        if (CASTS.isEmpty()) {
            return;
        }
        List<Spell> spells = ModSpells.SPELLS;
        Iterator<Map.Entry<UUID, Cast>> it = CASTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Cast> e = it.next();
            Cast cast = e.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            // oyuncu yok / ölü / kaldırılmış / asa tutmuyor → iptal
            if (player == null || !player.isAlive() || player.isRemoved() || !holdingWand(player)) {
                it.remove();
                // hâlâ bağlıysa istemci göstergesini temizle (null/çıkışta gerek yok)
                if (player != null) {
                    ArcanumNetwork.syncCastState(player, -1, 0);
                }
                continue;
            }
            // Bu oyuncu bu tick içinde başka birinin kenetlenmesine kilitlendiyse
            // (WandLockManager.tryStart onun cast'ini iptal etti) → ateşleme, sessizce düş.
            if (WandLockManager.isLocked(e.getKey())) {
                it.remove();
                ArcanumNetwork.syncCastState(player, -1, 0);
                continue;
            }
            int idx = cast.spellIndex();
            if (idx < 0 || idx >= spells.size()) {
                it.remove();
                ArcanumNetwork.syncCastState(player, -1, 0);
                continue;
            }
            Spell s = spells.get(idx);
            ServerLevel level = player.serverLevel();

            // TELEGRAF — SADECE zeminde tek rün çemberi (göğüs/göz hizası YOK, yoğunlaşma YOK).
            if (cast.ticksLeft() % TELEGRAPH_INTERVAL == 0) {
                SpellFx.runeRing(level, player.position(), s.color(), 9, 1.1);
            }

            int left = cast.ticksLeft() - 1;
            if (left <= 0) {
                it.remove();
                // >>> ASA KENETLENMESİ KANCASI <<< Saldırı büyüsü ateşlenirken karşıda
                // uygun bir rakip (karşılıklı bakan + o da saldırı cast eden/az önce atmış)
                // varsa mermi UÇMAZ — kenetlenme kurulur (mana/cooldown lock içinde işlenir).
                // noteFire her hâlükârda "az önce ateşledi" penceresine yazılır (aynı-tick
                // fire yarışı: biri kilit kurar, diğerinin fire'ı yukarıdaki isLocked ile düşer).
                if (WandLockManager.isAttackIdx(idx)) {
                    noteFire(player, idx);
                    if (WandLockManager.tryStart(level, player, idx)) {
                        ArcanumNetwork.syncCastState(player, -1, 0);
                        continue;
                    }
                }
                WandItem.executeCast(level, player, idx); // ATEŞ — mana/cooldown burada düşer
                ArcanumNetwork.syncCastState(player, -1, 0); // cast bitti → göstergeyi temizle
            } else {
                e.setValue(new Cast(idx, left));
            }
        }
    }

    private static boolean holdingWand(Player p) {
        return p.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof WandItem
                || p.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof WandItem;
    }
}
