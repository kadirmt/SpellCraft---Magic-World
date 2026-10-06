package com.arcanum.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * 26.2 İLERİ-UYUM dikişi (res-delta-26.2.md §1 kural 7, §6.1/§6.2): açık ekran, ekran değiştirme,
 * toast yöneticisi ve F1 (HUD gizli) erişimleri modun HER yerinde YALNIZ buradan yapılır.
 *
 * <p>26.2 vanilla (genSources: {@code Gui.java:72/218/222/290}, {@code Hud.java:211}): 26.1.2'deki
 * {@code Minecraft.screen} / {@code Minecraft.setScreen} / {@code Minecraft.getToastManager()} /
 * {@code Options.hideGui} kaldırıldı → {@code mc.gui.screen()}, {@code mc.gui.setScreen(..)},
 * {@code mc.gui.toastManager()}, {@code mc.gui.hud.isHidden()}. Davranış aynı (Forge
 * {@code ScreenEvent.Opening} artık {@code Gui.setScreen} içinde tetiklenir).
 */
public final class ClientCompat {
    private ClientCompat() {}

    /** İstemci örneği (kısa yol). */
    public static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** Şu an açık ekran; yoksa {@code null}. */
    public static @Nullable Screen screen() {
        return Minecraft.getInstance().gui.screen();
    }

    /** Ekranı değiştirir ({@code null} = ekranı kapat, oyuna dön). */
    public static void setScreen(@Nullable Screen s) {
        Minecraft.getInstance().gui.setScreen(s);
    }

    /** Toast yöneticisi (kökteki {@code getToasts()} / 1.21.2+ {@code getToastManager()} / 26.2 {@code gui.toastManager()}). */
    public static ToastManager toasts() {
        return Minecraft.getInstance().gui.toastManager();
    }

    /** F1 ile HUD gizli mi? (HUD elemanları kendi kontrol eder — kökteki HudRenderCallback paritesi.) */
    public static boolean hudHidden() {
        return Minecraft.getInstance().gui.hud.isHidden();
    }
}
