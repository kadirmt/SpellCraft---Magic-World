package com.arcanum.spell;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.entity.player.Player;

/**
 * Protego Maxima kalkanının sunucu-taraflı durumu. Oyuncu sağ tıkı BASILI TUTARKEN
 * her tick {@link #refresh} ile canlı tutulur (keepalive); bıraktığında birkaç tick
 * içinde söner. {@link Spells#ray} bu kalkanı kontrol eder: kalkanlı bir oyuncuya
 * atılan SALDIRI büyüleri (Affedilmez 3 lanet HARİÇ) hedefe ulaşmaz — kalkana çarpar.
 */
public final class ProtegoShield {
    private ProtegoShield() {}

    /** Son yenilemeden bu kadar tick sonra kalkan düşer (sağ tık bırakılınca). */
    private static final long KEEPALIVE = 3L;

    private static final Map<UUID, Long> SHIELDED = new ConcurrentHashMap<>();

    /** Kanal tick'inde çağrılır — kalkanı bu oyuncu için canlı tut. */
    public static void refresh(Player p) {
        SHIELDED.put(p.getUUID(), p.level().getGameTime());
    }

    /** Oyuncunun şu an aktif Protego Maxima kalkanı var mı? */
    public static boolean isShielded(Player p) {
        Long t = SHIELDED.get(p.getUUID());
        return t != null && p.level().getGameTime() - t <= KEEPALIVE;
    }

    public static void clear(UUID id) {
        SHIELDED.remove(id);
    }
}
