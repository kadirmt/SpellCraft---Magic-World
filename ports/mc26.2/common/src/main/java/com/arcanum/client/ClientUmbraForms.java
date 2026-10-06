package com.arcanum.client;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * İstemci tarafı UMBRAVOLO form seti ({@link ClientMagicData} deseninde statik holder).
 * Sunucudan {@code UmbraFormPayload} ile senkronlanır: formdaki oyuncuların ENTITY id'leri.
 * Zırh + eldeki-eşya render mixin'leri (HumanoidArmorLayerMixin / ItemInHandLayerMixin)
 * bu sete bakarak formdaki oyuncuyu TAMAMEN gizler (gövde vanilla INVISIBILITY ile zaten
 * gizli; kalan katmanlar burada kesilir). Sunucu periyodik tazeleme de yollar
 * (UmbraFormManager.RESYNC_INTERVAL) — geç katılan izleyici en geç 5 sn'de yakalar.
 */
public final class ClientUmbraForms {
    private ClientUmbraForms() {}

    private static final Set<Integer> ACTIVE = ConcurrentHashMap.newKeySet();

    /** Sunucu payload'ı: {@code active} true → sete ekle, false → çıkar. */
    public static void set(int entityId, boolean active) {
        if (active) {
            ACTIVE.add(entityId);
        } else {
            ACTIVE.remove(entityId);
        }
    }

    /** Bu entity id şu an kara duman formunda mı? (render mixin'leri çağırır) */
    public static boolean has(int entityId) {
        return !ACTIVE.isEmpty() && ACTIVE.contains(entityId);
    }

    /** Sunucudan kopunca tüm set temizlenir (DISCONNECT bloğu). */
    public static void clear() {
        ACTIVE.clear();
    }
}
