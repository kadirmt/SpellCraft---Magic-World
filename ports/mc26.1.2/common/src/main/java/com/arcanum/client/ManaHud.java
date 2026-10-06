package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.config.ArcanumConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * SOL-ALT köşede cilalı mana barını çizer. Mana OYUNCUNUN kendi değeridir
 * ({@link ClientMagicData}); bar dokulu (mana_frame/mana_fill), lerp ile pürüzsüz
 * dolar, düşük manada kırmızı pulse yapar.
 *
 * <p>Config ({@code config/arcanum.json}) ile üç yönden ayarlanır — üçü de SALT
 * GÖRSEL, sunucuyla senkronlanmaz, oyuncunun kendi tercihidir:
 * <ul>
 *   <li>{@code manaHudScale} — çizim ölçeği (0.5–2.5). Bar ölçekten BAĞIMSIZ olarak
 *       sol-alt köşeye {@link #MARGIN} piksel mesafede yapışık kalır.</li>
 *   <li>{@code manaHudOpacity} — genel saydamlık (0.05–1.0). Doku blit'lerine
 *       blit'in ARGB renk parametresiyle alfa (26.1: {@code setShaderColor} yok), düz
 *       renklere {@link #fade(int)} uygulanır.</li>
 *   <li>{@code manaHudRequiresWand} — {@code true} (varsayılan) ise bar yalnız asa
 *       eldeyken çizilir; {@code false} ise her zaman görünür.</li>
 * </ul>
 */
public final class ManaHud {
    private static final Identifier FRAME_TEX = Arcanum.id("textures/gui/mana_frame.png");
    private static final Identifier FILL_TEX = Arcanum.id("textures/gui/mana_fill.png");

    private static final int FRAME_W = 132;
    private static final int FRAME_H = 14;
    private static final int FILL_W = 128;
    private static final int FILL_H = 10;

    /** Sol-alt kenar boşluğu (SpellSlotsHud ile ortak sol hiza) — ölçekten ETKİLENMEZ. */
    private static final int MARGIN = 6;

    /** Pürüzsüz bar için gösterilen (lerp'li) mana değeri. */
    private static float displayed = -1f;

    private ManaHud() {}

    /**
     * Config ölçeğiyle çarpılmış, ekrana çizilen bar yüksekliği (piksel).
     *
     * <p>SÖZLEŞME: bar her zaman ekranın sol-alt köşesinde, alt kenardan
     * {@link #MARGIN} piksel yukarıda oturur — yani üst kenarı
     * {@code guiHeight() - MARGIN - effectiveHeight()}'tır. Barın üstüne bir şey
     * yerleştiren HUD'lar (SpellSlotsHud) sabit {@code 14} yerine BU metodu
     * kullanmalı; aksi hâlde ölçek değişince çakışma/boşluk oluşur.
     * Bar çizilmiyor olsa bile (asa şartı) doğru değeri döner.
     */
    public static int effectiveHeight() {
        return Math.max(1, Math.round(FRAME_H * scale()));
    }

    /**
     * Çizim ölçeği: config değeri (ArcanumConfig.sanitize 0.5–2.5'e kıstırır), üstüne
     * TAŞMA SİGORTASI. 2.5× bar 330 px geniştir; çok yüksek GUI Scale + küçük pencerede
     * (guiWidth &lt; 342) sağ kenardan taşardı — barın iki yanında da MARGIN kalacak en
     * büyük ölçeğe indirilir. Yalnızca KÜÇÜLTÜR: vanilla'nın en dar GUI genişliğinde
     * (320 px) bile tavan ~2.33'tür, yani varsayılan 1.0 asla etkilenmez.
     */
    private static float scale() {
        float s = (float) ArcanumConfig.get().manaHudScale;
        float maxS = (Minecraft.getInstance().getWindow().getGuiScaledWidth() - 2f * MARGIN) / FRAME_W;
        return maxS > 0f ? Math.min(s, maxS) : s;
    }

    /**
     * ARGB rengin ALFA bileşenini config saydamlığıyla çarpar (RGB'ye dokunmaz).
     * Alfa en az 4'te tutulur (kökle aynı taban): 1.21.1 vanilla {@code Font}
     * {@code (color & 0xFC000000) == 0} olan rengi "alfasız" sayıp opak yazardı; 26.1.2'de
     * {@code GuiGraphicsExtractor.text} alfa 0'ı HİÇ çizmez — iki durumda da 0'a
     * yuvarlanan alfa yanlış görünürdü. Gölge rengi 26.1.2'de {@code ARGB.scaleRGB(renk, 0.25)}
     * ile türetilir (alfa korunur), yani gölge de aynı alfayı alır.
     */
    private static int fade(int argb) {
        float o = (float) ArcanumConfig.get().manaHudOpacity;
        if (o >= 1.0f) {
            return argb;
        }
        int a = Math.max(4, Math.round(((argb >>> 24) & 0xFF) * o));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = ClientCompat.mc();
        if (mc.player == null || ClientCompat.hudHidden()) {
            return;
        }
        ArcanumConfig cfg = ArcanumConfig.get();
        // ASA ŞARTI (CurseForge geri bildirimi): elde (main VEYA off) asa yokken mana barı
        // ÇİZİLMEZ — sol alt tamamen boş kalır (SpellSlotsHud.findWand ile aynı kontrol).
        // Config'te manaHudRequiresWand=false ise bu kontrol ATLANIR, bar hep görünür.
        if (cfg.manaHudRequiresWand
                && !(mc.player.getMainHandItem().getItem() instanceof com.arcanum.item.WandItem)
                && !(mc.player.getOffhandItem().getItem() instanceof com.arcanum.item.WandItem)) {
            return;
        }
        int max = Math.max(1, ClientMagicData.manaCap());
        int mana = ClientMagicData.mana();

        // gösterilen değer hedefe lerp ile yaklaşır (her frame %15)
        if (displayed < 0f || Math.abs(displayed - mana) > max) {
            displayed = mana; // ilk açılış / asa değişimi: zıplama yerine direkt otur
        }
        displayed += (mana - displayed) * 0.15f;
        float frac = Math.max(0f, Math.min(1f, displayed / max));

        float s = scale();
        float opacity = (float) cfg.manaHudOpacity;

        // ÖLÇEK MATEMATİĞİ: önce barın SOL-ALT köşesine taşı, sonra ölçekle. Böylece
        // yerel (0,0) daima ekrandaki (MARGIN, guiHeight()-MARGIN-effectiveHeight())
        // noktasıdır; ölçek büyüse de sol/alt kenar boşluğu MARGIN piksel kalır ve bar
        // aşağı/sola taşmaz. Tüm çizim bundan sonra 0,0 tabanlı yerel koordinatlarda.
        g.pose().pushMatrix();
        g.pose().translate((float) MARGIN, (float) (g.guiHeight() - MARGIN - effectiveHeight()));
        g.pose().scale(s, s);

        // çerçeve + soldan kırpılmış dolum dokusu (saydamlık: doku rengi × ARGB modülasyonu —
        // bunlar RENK SABİTİ değil DOKU; kökteki setShaderColor(1,1,1,opacity) karşılığı
        // 26.1'de blit'in son ARGB parametresidir. Durum sızmadığı için geri alma GEREKMEZ.)
        int texTint = ARGB.white(opacity);
        g.blit(RenderPipelines.GUI_TEXTURED, FRAME_TEX, 0, 0, 0f, 0f, FRAME_W, FRAME_H,
                FRAME_W, FRAME_H, texTint);
        int fillW = Math.round(FILL_W * frac);
        if (mana > 0) {
            fillW = Math.max(1, fillW); // sıfır olmayan mana asla boş görünmesin
        }
        if (fillW > 0) {
            int w = Math.min(FILL_W, fillW);
            // kök: blit(tex, x, y, u, v, w, h, texW, texH) → kaynak bölge = hedef boyutu (w×h)
            g.blit(RenderPipelines.GUI_TEXTURED, FILL_TEX, 2, 2, 0f, 0f, w, FILL_H,
                    FILL_W, FILL_H, texTint);
        }

        // düşük mana (<%20): kırmızımsı pulse overlay (sin tabanlı alfa)
        if (frac < 0.2f) {
            float pulse = (float) (Math.sin(System.currentTimeMillis() / 150.0) * 0.5 + 0.5);
            int a = (int) (40 + pulse * 90);
            g.fillGradient(2, 2, 2 + FILL_W, 2 + FILL_H,
                    fade((a << 24) | 0xFF3B30), fade(((a * 2 / 3) << 24) | 0x8C1410));
        }

        // barın üstüne ortalanmış "mana / max" (gölgeli, camgöbeği)
        String txt = mana + " / " + max;
        int tw = mc.font.width(txt);
        g.text(mc.font, txt, (FRAME_W - tw) / 2, (FRAME_H - 8) / 2, fade(0xFF7FE8FF), true);

        g.pose().popMatrix();
    }
}
