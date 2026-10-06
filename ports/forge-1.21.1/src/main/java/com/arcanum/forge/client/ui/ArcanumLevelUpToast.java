package com.arcanum.forge.client.ui;

import com.arcanum.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * "Büyücü Seviyesi X" toast bildirimi — sağ-üstte açılan cilalı popup (vanilla toast
 * yerleşimini/kaymasını {@link ToastComponent} yönetir; biz yalnızca 0,0'dan içeriği
 * çizeriz). (FORGE 1.21.1 PORTU — kök fabric ArcanumLevelUpToast birebir; vanilla
 * Toast API'si aynı.) Arka plan tamamen elle çizilir (mod'un mor/altın/camgöbeği
 * teması), asa ikonu + altın kalın başlık + camgöbeği "yetenek ağacını aç" satırı.
 *
 * <p>{@code LevelUpPayload} alınınca istemci ağ alıcısı (ArcanumClientHooks.Handler)
 * tarafından eklenir.
 */
public final class ArcanumLevelUpToast implements Toast {

    private static final int W = 200;
    private static final int H = 32;
    /** Görünür kalma süresi (ms) — kullanıcı okuyabilsin diye rahat uzun. */
    private static final long DURATION_MS = 6000L;

    private final Component title;
    private final Component desc;
    private final ItemStack icon;

    public ArcanumLevelUpToast(int level) {
        this.title = Component.translatable("arcanum.toast.levelup.title", level)
                .withStyle(ChatFormatting.BOLD);
        this.desc = Component.translatable("arcanum.toast.levelup.desc");
        this.icon = new ItemStack(ModItems.ARCANEWOOD_WAND.get());
    }

    @Override
    public int width() {
        return W;
    }

    @Override
    public int height() {
        return H;
    }

    @Override
    public Toast.Visibility render(GuiGraphics g, ToastComponent toastComponent, long timeSinceLastVisible) {
        // --- arka plan: mor gradyan gövde + çok renkli ince çerçeve ---
        g.fillGradient(0, 0, W, H, 0xF0241243, 0xF01A0D31);
        g.fill(0, 0, W, 1, 0xFFB98CFF);        // üst kenar (yumuşak menekşe)
        g.fill(0, H - 1, W, H, 0xFF3A1F66);    // alt kenar (koyu)
        g.fill(0, 0, 1, H, 0xFF7A4FCF);        // sol kenar
        g.fill(W - 1, 0, W, H, 0xFF3A1F66);    // sağ kenar
        // ikon ile metni ayıran ince altın çizgi
        g.fill(28, 5, 29, H - 5, 0x55FFD24A);

        // --- asa ikonu (16x16) ---
        g.renderFakeItem(icon, 6, 8);

        // --- metinler ---
        Font font = toastComponent.getMinecraft().font;
        g.drawString(font, title, 34, 7, 0xFFFFD24A, true);   // altın kalın başlık
        g.drawString(font, desc, 34, 19, 0xFF7FE8FF, true);   // camgöbeği açıklama

        double mult = toastComponent.getNotificationDisplayTimeMultiplier();
        return timeSinceLastVisible >= DURATION_MS * mult
                ? Toast.Visibility.HIDE
                : Toast.Visibility.SHOW;
    }
}
