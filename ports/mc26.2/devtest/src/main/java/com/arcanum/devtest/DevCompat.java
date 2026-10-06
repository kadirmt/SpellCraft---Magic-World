package com.arcanum.devtest;

import com.arcanum.client.ClientCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * DEV-ONLY düzeneğin 26.2 ileri-uyum dikişi (res-delta-26.2.md §1 kural 7'nin düzenek karşılığı).
 * Ekran/HUD-gizleme erişimleri YALNIZ buradan: ekran okuma/yazma ortak {@link ClientCompat}'a gider;
 * {@code ClientCompat}'ta setter'ı olmayan HUD-gizleme yazımı burada tek yerdir. 26.2'de {@code Options.hideGui}
 * kaldırıldı; {@code Hud}'da setter YOK, yalnız {@code toggle()} / {@code isHidden()} var (genSources
 * {@code Hud.java:207/211}; F1 de {@code Gui.java:319}'da {@code hud.toggle()} çağırır) → istenen duruma geçmek
 * için gerektiğinde bir kez toggle edilir. Diğer 26.2 istemci yeniden adları da buradadır ({@code gui.overlay()},
 * {@code gui.hud.getChat()}, {@code gameRenderer.mainRenderTarget()}).
 */
final class DevCompat {
    private DevCompat() {}

    static @Nullable Screen screen() {
        return ClientCompat.screen();
    }

    static void setScreen(@Nullable Screen s) {
        ClientCompat.setScreen(s);
    }

    static boolean hudHidden() {
        return ClientCompat.hudHidden();
    }

    /** F1 eşdeğeri — temiz yaratık/partikül/yaprak kareleri için. */
    static void setHudHidden(boolean hidden) {
        var hud = Minecraft.getInstance().gui.hud;
        if (hud.isHidden() != hidden) {
            hud.toggle();
        }
    }

    /** Yükleme overlay'i (kaynak yeniden yükleme vb.); 26.1.2 {@code Minecraft.getOverlay()} → 26.2 {@code gui.overlay()}. */
    static net.minecraft.client.gui.screens.@Nullable Overlay overlay() {
        return Minecraft.getInstance().gui.overlay();
    }

    /** Sohbet bileşeni; 26.1.2 {@code Gui.getChat()} → 26.2 {@code Hud.getChat()} (Hud.java:1246). */
    static net.minecraft.client.gui.components.ChatComponent chat() {
        return Minecraft.getInstance().gui.hud.getChat();
    }

    /** Ana render hedefi; 26.1.2 {@code Minecraft.getMainRenderTarget()} → 26.2 {@code GameRenderer.mainRenderTarget()}. */
    static com.mojang.blaze3d.pipeline.RenderTarget mainRenderTarget() {
        return Minecraft.getInstance().gameRenderer.mainRenderTarget();
    }
}
