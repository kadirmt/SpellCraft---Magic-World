package com.arcanum.forge.client.ui;

import com.arcanum.forge.client.state.ClientLockState;
import com.arcanum.forge.client.state.ClientLockState.View;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * ASA KENETLENMESİ tug-of-war HUD'u — crosshair'in hemen üstünde ince bir denge çubuğu.
 * (FORGE 1.21.1 PORTU — kök fabric LockTugHud birebir.) Yalnızca YEREL oyuncu bir
 * düelloda ise çizilir (izleyiciler yalnız ışını görür, HUD görmez).
 *
 * <p>Bar her zaman doludur; SOL yarı SENİN büyü renginde, SAĞ yarı rakibinkinde. İkisinin
 * buluştuğu "düğüm" işareti kaybedene doğru kayar (sol = sen tehlikedesin). Düğüme
 * yaklaştığında tehlike titremesi + ekran kenarı vinyeti; her tıkta elmas kısa parlar.
 */
public final class LockTugHud {
    private static final int BAR_W = 122;
    private static final int BAR_H = 6;
    private static final int ABOVE_CROSSHAIR = 30;
    private static final int GOLD = 0xFFE9B0;

    private LockTugHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return;
        }
        // Kenetlenme bitişi: ~450ms ekran parlaması (kazanan/kaybeden). Aktif bardan bağımsız.
        if (ClientLockState.flashActive()) {
            endFlash(g);
        }
        View v = ClientLockState.myView();
        if (v == null || mc.player == null) {
            return;
        }
        boolean left = mc.player.getUUID().equals(v.a);
        float node = v.displayNode;
        // markerFrac: 0 = ekranın solu (BEN tehlikedeyim) .. 1 = sağ (rakip tehlikede)
        float markerFrac = left ? node : 1f - node;
        int myColor = (left ? v.colorA : v.colorB) & 0xFFFFFF;
        int oppColor = (left ? v.colorB : v.colorA) & 0xFFFFFF;

        int x = (g.guiWidth() - BAR_W) / 2;
        int y = g.guiHeight() / 2 - ABOVE_CROSSHAIR;
        int markerX = x + Math.round(BAR_W * markerFrac);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, 0xAA0A0A12);
        g.fill(x, y, markerX, y + BAR_H, 0xE0000000 | myColor);
        g.fill(markerX, y, x + BAR_W, y + BAR_H, 0xE0000000 | oppColor);

        double period = markerFrac < 0.3f ? 120.0 : 200.0;
        float pulse = (float) (Math.sin(System.currentTimeMillis() / period) * 0.5 + 0.5);
        long clickAgo = ClientLockState.localClickAgo();
        int clickBoost = clickAgo < 160 ? (int) (2 * (1f - clickAgo / 160f)) : 0;
        int nodeW = 2 + Math.round(pulse * 2) + clickBoost;
        g.fill(markerX - nodeW, y - 2, markerX + nodeW, y + BAR_H + 2, 0xFFFFFFFF);
        g.fill(markerX - nodeW + 1, y - 1, markerX + nodeW - 1, y + BAR_H + 1, 0xFF000000 | GOLD);
        RenderSystem.disableBlend();

        if (markerFrac < 0.34f) {
            dangerVignette(g, oppColor, (0.34f - markerFrac) / 0.34f);
        }
    }

    /** Kenetlenme bitiş ekran parlaması — kazananın büyü renginde, sönen tam-ekran flash. */
    private static void endFlash(GuiGraphics g) {
        int winColor = ClientLockState.flashWinColor();
        boolean iWon = ClientLockState.flashIWon();
        float s = ClientLockState.flashStrength();
        int a = (int) (s * (iWon ? 0.45f : 0.35f) * 255f);
        if (a <= 2) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | winColor);
        RenderSystem.disableBlend();
    }

    /** Ekranın üst+alt kenarına rakip renginde, atımlı yumuşak vinyet (kaybediyorsun uyarısı). */
    private static void dangerVignette(GuiGraphics g, int rgb, float strength) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        float pulse = (float) (Math.sin(System.currentTimeMillis() / 180.0) * 0.3 + 0.7);
        int a = (int) (Math.min(1f, strength) * pulse * 90f);
        if (a <= 2) {
            return;
        }
        int col = (a << 24) | (rgb & 0xFFFFFF);
        int trans = rgb & 0xFFFFFF;
        int band = Math.max(28, h / 6);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.fillGradient(0, 0, w, band, col, trans);
        g.fillGradient(0, h - band, w, h, trans, col);
        RenderSystem.disableBlend();
    }
}
