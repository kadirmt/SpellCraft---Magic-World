package com.arcanum.item;

/**
 * Asa kademeleri. 10. tur: asalar artık YALNIZCA büyü GÜCÜ çarpanı ({@code powerMult}) ve
 * KAST SÜRESİ çarpanı ({@code castTimeMult}) belirler. Mana tamamen oyuncuya taşındı
 * (bkz. {@link com.arcanum.data.ArcanumPlayerData}) — asanın mana/cooldown/regen etkisi YOK.
 *
 * <p>{@code castTimeMult} &lt; 1.0 = daha HIZLI büyü yapar, &gt; 1.0 = daha YAVAŞ.
 * assetId, GeckoLib texture dosya adıdır.
 */
public enum WandTier {
    /** Çaylak asası — tamamen dengeli (1.00 / 1.00). */
    ARCANEWOOD("arcanewood_wand", 1.00f, 1.00f),
    /** Gökgürültü Kuşu Asası — çevik ama zayıf: çok hızlı büyü, düşük güç. */
    THUNDERBIRD("thunderbird_wand", 0.70f, 0.70f),
    /** Anka asası — hızlı ve zarif; gücünden biraz ödün verir. */
    PHOENIX("phoenix_wand", 0.80f, 0.80f),
    /** Troll asası — daha güçlü; ama daha yavaş. */
    TROLL("troll_wand", 1.20f, 1.20f),
    /** Mürver Asa — efsanevi güç (2×); ama ağır ve yavaş kast. */
    ELDER("elder_wand", 2.00f, 1.50f);

    private final String assetId;
    private final float powerMult;
    private final float castTimeMult;

    WandTier(String assetId, float powerMult, float castTimeMult) {
        this.assetId = assetId;
        this.powerMult = powerMult;
        this.castTimeMult = castTimeMult;
    }

    public String assetId() {
        return assetId;
    }

    public float powerMult() {
        return powerMult;
    }

    public float castTimeMult() {
        return castTimeMult;
    }
}
