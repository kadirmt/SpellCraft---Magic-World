package com.arcanum.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * 26.2 İLERİ-UYUM dikişi (res-delta-26.2.md §1 kural 7): {@code Minecraft.screen},
 * {@code Minecraft.setScreen}, {@code Minecraft.getToastManager()} ve {@code Options.hideGui}
 * erişimleri modun HER yerinde YALNIZ buradan yapılır. 26.2'de bunlar
 * {@code mc.gui.screen()}, {@code mc.gui.setScreen(..)}, {@code mc.gui.toastManager()} ve
 * {@code mc.gui.hud.isHidden()} olur — o portta yalnız bu dosyanın 4 satırı değişir.
 *
 * <p>26.1.2 vanilla adları genSources'tan doğrulandı: {@code public @Nullable Screen screen},
 * {@code public void setScreen(@Nullable Screen)}, {@code public ToastManager getToastManager()},
 * {@code Options#hideGui} (public boolean).
 */
public final class ClientCompat {
    private ClientCompat() {}

    /** İstemci örneği (kısa yol). */
    public static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** Şu an açık ekran; yoksa {@code null}. */
    public static @Nullable Screen screen() {
        return Minecraft.getInstance().screen;
    }

    /** Ekranı değiştirir ({@code null} = ekranı kapat, oyuna dön). */
    public static void setScreen(@Nullable Screen s) {
        Minecraft.getInstance().setScreen(s);
    }

    /** Toast yöneticisi (kökteki {@code getToasts()} / 1.21.2+ {@code getToastManager()}). */
    public static ToastManager toasts() {
        return Minecraft.getInstance().getToastManager();
    }

    /** F1 ile HUD gizli mi? (HUD elemanları kendi kontrol eder — kökteki HudRenderCallback paritesi.) */
    public static boolean hudHidden() {
        return Minecraft.getInstance().options.hideGui;
    }
}
