package com.arcanum.forge.client.input;

import com.arcanum.forge.client.state.ClientLockState;
import com.arcanum.forge.client.state.ClientSpellData;
import com.arcanum.item.WandItem;
import com.arcanum.network.ArcanumNetwork;
import com.arcanum.network.LockPushPayload;
import com.arcanum.network.SetActivePayload;
import com.arcanum.registry.ModMobEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Fabric'teki {@code MouseHandlerMixin}'in Forge karşılığı — mixin yerine cancellable
 * Forge event'leri ({@code InputEvent.MouseScrollingEvent} + {@code InputEvent.MouseButton.Pre};
 * her ikisi de MouseHandler.onScroll/onPress başında, ekran işlenmeden ÖNCE ateşlenir —
 * mixin'in @At("HEAD") noktasıyla eşdeğer). FORGE bus'ına {@code ArcanumForgeClient}
 * kaydeder.
 *
 * <p>1) Dünyada (hiçbir ekran açık değilken) asa elde iken Shift + fare tekerleği ile
 * aktif büyü slotunu (0..3) gezinir ve vanilla hotbar kaydırmasını iptal eder.
 * Yukarı kaydırma (delta > 0) önceki slota, aşağı sonraki slota geçer; sunucuya
 * {@link SetActivePayload} gönderilir.
 *
 * <p>2) ASA KENETLENMESİ sırasında SOL-TIK "asayı it" girdisine dönüşür: her fiziksel
 * basış bir kez sunucuya {@link LockPushPayload} yollar (tık SAYIMI + CPS TAVANI 18
 * otoritesi sunucuda) ve normal saldırı/blok-kırma iptal edilir. Kenetlenme yokken
 * hiçbir şey yapmaz (vanilla davranış). Ayrıca CRUCIO altında kıvranırken ekran-kapalı
 * TÜM fare basışları iptal edilir.
 */
public final class MouseInputHandler {

    private MouseInputHandler() {}

    /** Shift+tekerlek: aktif dizilim slotu gezinme (fabric arcanum$loadoutScroll birebir). */
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        double yOffset = event.getScrollDelta();
        if (mc.screen != null || yOffset == 0.0) {
            return;
        }
        // ASA KENETLENMESİ sırasında TÜM tekerlek kaydırması iptal: Shift'siz tekerlek vanilla
        // hotbar'ı değiştirir → asa main-hand'den çıkar → sunucu kilidi anında (grace'siz)
        // düşürürdü (S2 #3). Spam-tıklama sırasında kazara tekerleğe sürtmek yaygın; asa
        // değişimi bilinçli tuşla (1-9) hâlâ mümkün — denge korunur.
        if (ClientLockState.myView() != null) {
            event.setCanceled(true);
            return;
        }
        LocalPlayer p = mc.player;
        // "Shift" artık yapılandırılabilir: scroll_modifier keybind'inin bağlı tuşu
        // (varsayılan SOL SHIFT; oyuncu R'ye bağlarsa R+scroll). GLFW-poll'lu kontrol
        // için bkz. ArcanumForgeClient.isScrollModifierDown.
        if (p == null || !com.arcanum.forge.client.ArcanumForgeClient.isScrollModifierDown(mc)
                || !holdsWand(p)) {
            return;
        }
        int ns = Math.floorMod(ClientSpellData.activeSlot() + (yOffset > 0 ? -1 : 1), ClientSpellData.SLOTS);
        ArcanumNetwork.sendToServer(new SetActivePayload(ClientSpellData.activePage(), ns));
        event.setCanceled(true); // vanilla hotbar kaydırmasını engelle
    }

    /** Crucio tık-iptali + kenetlenme sol-tık "asayı it" (fabric arcanum$lockClick birebir). */
    public static void onMousePress(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        // CRUCIO: kıvranırken sağ/sol tık İŞE YARAMAZ — ekran kapalıyken tüm basışları iptal et.
        if (mc.screen == null && event.getAction() == GLFW.GLFW_PRESS && mc.player != null
                && mc.player.hasEffect(ModMobEffects.CRUCIO.get())) {
            event.setCanceled(true);
            return;
        }
        // Yalnız YEREL oyuncu bir düelloda ise (izleyici DEĞİL) sol-tık "asayı it"e döner.
        if (mc.screen != null || ClientLockState.myView() == null) {
            return;
        }
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        ClientLockState.noteLocalClick();               // HUD elmas pulse'u
        ArcanumNetwork.sendToServer(new LockPushPayload()); // ham tık → sunucu sayar
        event.setCanceled(true);                         // normal saldırı/blok-kırma iptal
    }

    private static boolean holdsWand(LocalPlayer p) {
        return p.getMainHandItem().getItem() instanceof WandItem
                || p.getOffhandItem().getItem() instanceof WandItem;
    }
}
