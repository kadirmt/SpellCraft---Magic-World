package com.arcanum.devtest;

import com.arcanum.client.ClientCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * DEV-ONLY düzeneğin 26.2 ileri-uyum dikişi (res-delta-26.2.md §1 kural 7'nin düzenek karşılığı).
 * Ekran/HUD-gizleme erişimleri YALNIZ buradan: ekran okuma/yazma ortak {@link ClientCompat}'a gider;
 * {@code ClientCompat}'ta setter'ı olmayan {@code Options.hideGui} yazımı burada tek satırdır
 * (26.2'de {@code mc.gui.hud} tarafına taşınır — o portta yalnız bu dosya değişir).
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
        Minecraft.getInstance().options.hideGui = hidden;
    }
}
