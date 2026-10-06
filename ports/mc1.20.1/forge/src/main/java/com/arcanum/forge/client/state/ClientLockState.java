package com.arcanum.forge.client.state;

import java.util.Collection;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;

/**
 * İstemci tarafı ASA KENETLENMESİ durumu (Forge portu — fabric ClientLockState birebir) —
 * ÇOK-DÜELLO destekli. Sunucu, kenetlenmenin durumunu (küçük paket: iki UUID + düğüm +
 * iki renk) hem KATILIMCILARA (her tick) hem de yakındaki İZLEYİCİLERE (kısılmış hızda)
 * yollar; her istemci ışını KENDİ bilgisayarında yerel partiküllerle çizer (ağa partikül
 * gitmez → senkron gecikmesi yok).
 *
 * <p>Her aktif kenetlenme bir {@link View}. Ekranda birden çok düello olabilir (ör. bir
 * izleyici iki düelloyu birden görebilir). Güncellenmeyen görünümler ~2 sn sonra otomatik
 * temizlenir (izleyici menzil dışına çıkıp bitiş paketini kaçırsa bile ışın asılı kalmaz).
 */
public final class ClientLockState {

    /** Tek bir aktif kenetlenmenin istemci görünümü. */
    public static final class View {
        public final UUID a;
        public final UUID b;
        public volatile int colorA;
        public volatile int colorB;
        public volatile float node;        // sunucudan gelen hedef
        public volatile float displayNode; // pürüzsüz gösterilen
        public volatile long lastUpdate;   // client ms (bayat temizliği)

        View(UUID a, UUID b, float node, int colorA, int colorB, long now) {
            this.a = a;
            this.b = b;
            this.node = node;
            this.displayNode = node;
            this.colorA = colorA;
            this.colorB = colorB;
            this.lastUpdate = now;
        }

        public boolean involves(UUID id) {
            return id != null && (id.equals(a) || id.equals(b));
        }
    }

    private static final Map<String, View> LOCKS = new ConcurrentHashMap<>();

    // yerel oyuncunun kendi düellosunun bitiş parlaması
    private static volatile int winnerSide = 0;   // 1 = a kazandı, 2 = b kazandı
    private static volatile UUID flashA = null;    // biten kilidin 'a' tarafı
    private static volatile int flashColorA = 0xFFFFFF;
    private static volatile int flashColorB = 0xFFFFFF;
    private static volatile long flashUntil = 0L;
    private static volatile long localClickAt = 0L;

    private ClientLockState() {}

    private static String key(UUID a, UUID b) {
        return a + "|" + b;
    }

    private static UUID localUUID() {
        var p = Minecraft.getInstance().player;
        return p == null ? null : p.getUUID();
    }

    /**
     * S2C durumunu uygular. {@code phase}: 0 = başladı/güncel · 1 = bitti (kazanan a) ·
     * 2 = bitti (kazanan b) · 3 = iptal.
     */
    public static void set(UUID a, UUID b, float node, int colorA, int colorB, int phase) {
        String k = key(a, b);
        if (phase == 3) {
            LOCKS.remove(k);
            return;
        }
        if (phase == 1 || phase == 2) {
            LOCKS.remove(k);
            UUID me = localUUID();
            if (me != null && (me.equals(a) || me.equals(b))) {
                winnerSide = phase;
                flashA = a;
                flashColorA = colorA;
                flashColorB = colorB;
                flashUntil = System.currentTimeMillis() + 450L;
            }
            return;
        }
        long now = System.currentTimeMillis();
        View v = LOCKS.get(k);
        if (v == null) {
            LOCKS.put(k, new View(a, b, node, colorA, colorB, now));
        } else {
            v.node = node;
            v.colorA = colorA;
            v.colorB = colorB;
            v.lastUpdate = now;
        }
    }

    /** Her istemci tick'i — düğümleri pürüzsüz ilerlet, bayat (güncellenmeyen) görünümleri temizle. */
    public static void tickInterp() {
        long now = System.currentTimeMillis();
        Iterator<View> it = LOCKS.values().iterator();
        while (it.hasNext()) {
            View v = it.next();
            if (now - v.lastUpdate > 2000L) {
                it.remove(); // bitiş paketi kaçtı / menzil dışı → asılı ışını temizle
                continue;
            }
            v.displayNode += (v.node - v.displayNode) * 0.35f;
        }
    }

    /** Tüm aktif görünümler (ışın render'ı iterasyonla çizer). */
    public static Collection<View> views() {
        return LOCKS.values();
    }

    public static boolean anyActive() {
        return !LOCKS.isEmpty();
    }

    /** Yerel oyuncunun İÇİNDE olduğu kilit (HUD için); yoksa null. */
    public static View myView() {
        UUID me = localUUID();
        if (me == null) {
            return null;
        }
        for (View v : LOCKS.values()) {
            if (v.involves(me)) {
                return v;
            }
        }
        return null;
    }

    // ---- bitiş parlaması (yerel oyuncu) ----

    public static boolean flashActive() {
        return System.currentTimeMillis() < flashUntil;
    }

    public static float flashStrength() {
        long rem = flashUntil - System.currentTimeMillis();
        return rem <= 0 ? 0f : Math.min(1f, rem / 450f);
    }

    /** Yerel oyuncu kendi biten düellosunu kazandı mı? */
    public static boolean flashIWon() {
        UUID me = localUUID();
        boolean iAmA = me != null && me.equals(flashA);
        return (winnerSide == 1) == iAmA;
    }

    /** Biten düelloda kazananın büyü rengi. */
    public static int flashWinColor() {
        return (winnerSide == 1 ? flashColorA : flashColorB) & 0xFFFFFF;
    }

    // ---- yerel tık (HUD elmas pulse) ----

    public static void noteLocalClick() {
        localClickAt = System.currentTimeMillis();
    }

    public static long localClickAgo() {
        return System.currentTimeMillis() - localClickAt;
    }

    public static void clear() {
        LOCKS.clear();
        winnerSide = 0;
        flashA = null;
        flashUntil = 0L;
    }
}
