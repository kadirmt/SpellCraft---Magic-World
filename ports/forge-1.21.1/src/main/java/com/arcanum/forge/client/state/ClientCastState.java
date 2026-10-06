package com.arcanum.forge.client.state;

import net.minecraft.client.Minecraft;

/**
 * İstemci tarafı CAST DURUMU tutucusu. (FORGE 1.21.1 PORTU — kök fabric
 * {@code com.arcanum.fabric.client.ClientCastState} birebir.)
 *
 * <p>Sunucu {@code CastStatePayload(spellIndex, totalTicks)} S2C paketini yollar;
 * istemci ağ alıcısı (ArcanumClientHooks.Handler) bu tutucuya yazar.
 * {@code CastBarHud} imlecin hemen altındaki minik ilerleme çubuğunu buradan okur.
 *
 * <p>İlerleme, cast başlangıcındaki oyun zamanı (game time) ile şu anki oyun zamanı
 * farkından hesaplanır — istemci tarafında ek senkron gerekmez. {@code totalTicks <= 0}
 * ve {@code mc.level == null} durumlarına karşı korumalıdır (0'a bölme yok).
 */
public final class ClientCastState {
    /** Kastedilen büyünün {@link com.arcanum.spell.ModSpells#SPELLS} global index'i; cast yoksa -1. */
    private static int spellIndex = -1;
    /** Cast'in toplam süresi (tick); ilerleme paydası. */
    private static int totalTicks = 0;
    /** Cast'in başladığı oyun zamanı (mc.level.getGameTime()). */
    private static long startGameTime = 0L;

    private ClientCastState() {}

    /**
     * Cast başlangıcını kaydeder. {@code spellIndex < 0} veya {@code totalTicks <= 0}
     * (ya da level yok) ise durumu temizler.
     */
    public static void set(int spellIndex, int totalTicks) {
        if (spellIndex < 0 || totalTicks <= 0) {
            clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }
        ClientCastState.spellIndex = spellIndex;
        ClientCastState.totalTicks = totalTicks;
        ClientCastState.startGameTime = mc.level.getGameTime();
    }

    /** Aktif bir cast var mı? (spellIndex >= 0 ve ilerleme < 1). */
    public static boolean isCasting() {
        return spellIndex >= 0 && progress() < 1f;
    }

    /** Kastedilen büyünün global index'i; cast yoksa -1. */
    public static int spellIndex() {
        return spellIndex;
    }

    /** Cast ilerlemesi (0..1). totalTicks &lt;= 0 / level yok → 0 (0'a bölme yok). */
    public static float progress() {
        if (spellIndex < 0 || totalTicks <= 0) {
            return 0f;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 0f;
        }
        float p = (float) (mc.level.getGameTime() - startGameTime) / totalTicks;
        return Math.max(0f, Math.min(1f, p));
    }

    /** Cast durumunu sıfırlar (cast bitti/iptal, spellIndex &lt; 0 geldi veya bağlantı koptu). */
    public static void clear() {
        spellIndex = -1;
        totalTicks = 0;
        startGameTime = 0L;
    }
}
