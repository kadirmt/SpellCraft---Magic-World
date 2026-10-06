package com.arcanum.mixin.client;

import com.arcanum.client.ArcanumKeys;
import com.arcanum.client.ClientCompat;
import com.arcanum.client.ClientLockState;
import com.arcanum.client.ClientSpellData;
import com.arcanum.item.WandItem;
import com.arcanum.network.ArcanumNetwork;
import com.arcanum.network.LockPushPayload;
import com.arcanum.network.SetActivePayload;
import com.arcanum.registry.ModMobEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dünyada (hiçbir ekran açık değilken) asa elde iken Shift + fare tekerleği ile
 * aktif büyü slotunu (0..3) gezinir ve vanilla hotbar kaydırmasını iptal eder.
 *
 * <p>Hedef metod [javap 26.1.2 — Fabric merged jar ve Forge 64.1.3 yamalı jar'da AYNI]:
 *   {@code private void MouseHandler#onScroll(long handle, double xoffset, double yoffset)}
 *   descriptor: {@code (JDD)V}. Yukarı kaydırma (yOffset > 0) önceki slota,
 *   aşağı sonraki slota geçer; sunucuya {@link SetActivePayload} gönderilir.
 *   (Forge yaması onScroll GÖVDESİNE MouseScrollingEvent ekler; HEAD etkilenmez.)
 */
@Mixin(net.minecraft.client.MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void arcanum$loadoutScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        Minecraft mc = ClientCompat.mc();
        if (ClientCompat.screen() != null || yOffset == 0.0) {
            return;
        }
        // ASA KENETLENMESİ sırasında TÜM tekerlek kaydırması iptal: Shift'siz tekerlek vanilla
        // hotbar'ı değiştirir → asa main-hand'den çıkar → sunucu kilidi anında (grace'siz)
        // düşürürdü (S2 #3). Spam-tıklama sırasında kazara tekerleğe sürtmek yaygın; asa
        // değişimi bilinçli tuşla (1-9) hâlâ mümkün — denge korunur.
        if (ClientLockState.myView() != null) {
            ci.cancel();
            return;
        }
        LocalPlayer p = mc.player;
        // "Shift" artık yapılandırılabilir: scroll_modifier keybind'inin bağlı tuşu
        // (varsayılan SOL SHIFT; oyuncu R'ye bağlarsa R+scroll). GLFW-poll'lu kontrol
        // için bkz. ArcanumKeys.isScrollModifierDown.
        if (p == null || !ArcanumKeys.isScrollModifierDown(mc)
                || !holdsWand(p)) {
            return;
        }
        int ns = Math.floorMod(ClientSpellData.activeSlot() + (yOffset > 0 ? -1 : 1), ClientSpellData.SLOTS);
        ArcanumNetwork.sendToServer(new SetActivePayload(ClientSpellData.activePage(), ns));
        ci.cancel(); // vanilla hotbar kaydırmasını engelle
    }

    /**
     * ASA KENETLENMESİ sırasında SOL-TIK "asayı it" girdisine dönüşür: her fiziksel basış
     * bir kez sunucuya {@link LockPushPayload} yollar (tık SAYIMI + CPS TAVANI 18 otoritesi
     * sunucuda) ve normal saldırı/blok-kırma iptal edilir. Kenetlenme yokken hiçbir şey
     * yapmaz (vanilla davranış).
     *
     * <p>Hedef [javap 26.1.2 — Fabric + Forge AYNI]: kökteki {@code onPress(JIII)V} 1.21.9'da
     * {@code private void onButton(long handle, MouseButtonInfo rawButtonInfo, int action)} oldu,
     * descriptor {@code (JLnet/minecraft/client/input/MouseButtonInfo;I)V}. Kökteki {@code button}
     * = {@code buttonInfo.button()} (ham GLFW düğmesi; vanilla'nın simulateRightClick'inden ÖNCE —
     * kökteki onPress de ham düğmeyi görüyordu), {@code action} aynı 3. parametre.
     * (Forge yaması gövdeye MouseButton.Pre olayını ekler; HEAD ondan önce çalışır.)
     */
    @Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("HEAD"), cancellable = true)
    private void arcanum$lockClick(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        Minecraft mc = ClientCompat.mc();
        // CRUCIO: kıvranırken sağ/sol tık İŞE YARAMAZ — ekran kapalıyken tüm basışları iptal et.
        if (ClientCompat.screen() == null && action == GLFW.GLFW_PRESS && mc.player != null
                && mc.player.hasEffect(ModMobEffects.holderOf(ModMobEffects.CRUCIO))) {
            ci.cancel();
            return;
        }
        // Yalnız YEREL oyuncu bir düelloda ise (izleyici DEĞİL) sol-tık "asayı it"e döner.
        if (ClientCompat.screen() != null || ClientLockState.myView() == null) {
            return;
        }
        if (buttonInfo.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || action != GLFW.GLFW_PRESS) {
            return;
        }
        ClientLockState.noteLocalClick();                      // HUD elmas pulse'u
        ArcanumNetwork.sendToServer(new LockPushPayload());     // ham tık → sunucu sayar
        ci.cancel();                                            // normal saldırı/blok-kırma iptal
    }

    private static boolean holdsWand(LocalPlayer p) {
        return p.getMainHandItem().getItem() instanceof WandItem
                || p.getOffhandItem().getItem() instanceof WandItem;
    }
}
