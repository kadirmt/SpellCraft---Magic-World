package com.arcanum.spell;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.arcanum.entity.FiendfyreDragonEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Protego Diabolica'nın MAVİ ALEV EJDERHASI'nın (bkz. {@link FiendfyreDragonEntity})
 * sunucu-taraflı ömür yöneticisi. Tasarımı {@link ProtegoShield} ile birebir aynı
 * "keepalive" desenidir: kanal her tick {@link #tick} çağırır, çağrı kesilince
 * bayrak birkaç tick içinde söner ve ejderha kendi watchdog'uyla dağılır.
 *
 * <p>Bu tercih BİLİNÇLİdir: kanalın bitiş yolları çok ({@code releaseUsing}, mana
 * bitişi, aktif büyünün değişmesi, oyuncunun ölmesi, boyut değiştirmesi, sunucudan
 * düşmesi). Hepsine ayrı kanca yazmak yerine tek ortak kural: <b>tick akmıyorsa
 * kanal yoktur</b>. Böylece {@code WandItem}'a hiçbir ek kanca gerekmez.
 *
 * <p>TEK CASTER = TEK EJDERHA: {@link #ACTIVE} caster UUID'sini tek bir ejderha
 * UUID'sine bağlar; kayıtlı ejderha ölmüş/dağılıyorsa yerine yenisi doğar.
 */
public final class DiabolicaDragonManager {
    private DiabolicaDragonManager() {}

    /** Son {@link #tick}'ten bu kadar tick sonra kanal "bitmiş" sayılır (ProtegoShield ile aynı). */
    private static final long KEEPALIVE = 3L;

    /** Bayat kayıt süpürme periyodu / eşiği (tick). */
    private static final long SWEEP_PERIOD = 200L;
    private static final long SWEEP_AGE = 100L;

    /** caster UUID → son kanal tick'i. */
    private static final Map<UUID, Long> CHANNELING = new ConcurrentHashMap<>();
    /** caster UUID → o casterın yaşayan ejderhasının UUID'si. */
    private static final Map<UUID, UUID> ACTIVE = new ConcurrentHashMap<>();

    /**
     * Protego Diabolica'nın HER kanal tick'inde çağrılır ({@code Spells.protegoDiabolica}
     * en başında). Kanal bayrağını tazeler; caster'ın ejderhası yoksa (ilk tick, ya da
     * bir şekilde kaybolmuşsa) doğurur, varsa hiçbir şey yapmaz — ejderhanın hareketi
     * kendi {@code aiStep}'indedir.
     */
    public static void tick(ServerLevel level, Player player) {
        UUID id = player.getUUID();
        CHANNELING.put(id, level.getGameTime());

        if (level.getGameTime() % SWEEP_PERIOD == 0L) {
            sweep(level.getGameTime());
        }
        if (!(player instanceof ServerPlayer owner)) {
            return; // yalnız gerçek sunucu oyuncusu ejderha çağırır
        }

        UUID dragonId = ACTIVE.get(id);
        if (dragonId != null) {
            if (level.getEntity(dragonId) instanceof FiendfyreDragonEntity dragon
                    && dragon.isAlive() && !dragon.isRemoved() && !dragon.isDissolving()) {
                return; // ejderha zaten uçuyor
            }
            ACTIVE.remove(id, dragonId); // ölü/dağılan kaydı bırak, aşağıda yenisi doğar
        }

        FiendfyreDragonEntity spawned = FiendfyreDragonEntity.summonFor(owner, level);
        if (spawned != null) {
            ACTIVE.put(id, spawned.getUUID());
        }
    }

    /**
     * Bu oyuncunun Protego Diabolica kanalı ŞU AN akıyor mu?
     * {@link FiendfyreDragonEntity}'nin watchdog'u her tick bunu sorar.
     */
    public static boolean isChanneling(Player player) {
        Long last = CHANNELING.get(player.getUUID());
        return last != null && player.level().getGameTime() - last <= KEEPALIVE;
    }

    /** Ejderha dünyadan çıktığında kendisi çağırır — yalnız KENDİ kaydını siler. */
    public static void onDragonRemoved(UUID ownerId, UUID dragonId) {
        ACTIVE.remove(ownerId, dragonId);
    }

    /**
     * Bir caster için ejderhayı ANINDA dağılmaya sokar (isteğe bağlı sert kapatma —
     * normal akışta gerekmez, keepalive zaten hallediyor). Örn. bir gelecek turda
     * {@code WandItem.channelForget} ya da ölüm olayına bağlanmak istenirse.
     */
    public static void stop(ServerLevel level, UUID ownerId) {
        CHANNELING.remove(ownerId);
        UUID dragonId = ACTIVE.remove(ownerId);
        if (dragonId != null && level.getEntity(dragonId) instanceof FiendfyreDragonEntity dragon) {
            dragon.beginDissolve(level);
        }
    }

    /** Oyuncu çıkışında kayıtları unut (WandItem.channelForget deseni). */
    public static void forget(UUID ownerId) {
        CHANNELING.remove(ownerId);
        ACTIVE.remove(ownerId);
    }

    /** Bayat kayıtları at — uzun oturumlarda harita sızıntısını önler. */
    private static void sweep(long now) {
        Iterator<Map.Entry<UUID, Long>> it = CHANNELING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            if (now - e.getValue() > SWEEP_AGE) {
                ACTIVE.remove(e.getKey());
                it.remove();
            }
        }
    }
}
