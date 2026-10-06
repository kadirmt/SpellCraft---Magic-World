package com.arcanum.fabric.mixin;

import com.arcanum.fabric.client.ClientLockState;
import com.arcanum.fabric.client.ClientSpellData;
import com.arcanum.item.WandItem;
import com.arcanum.network.LockPushPayload;
import com.arcanum.network.SetActivePayload;
import com.arcanum.registry.ModMobEffects;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
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
 * <p>Hedef metod (javap ile doğrulandı):
 *   {@code MouseHandler#onScroll(long window, double xOffset, double yOffset)}
 *   descriptor: {@code (JDD)V}. Yukarı kaydırma (yOffset > 0) önceki slota,
 *   aşağı sonraki slota geçer; sunucuya {@link SetActivePayload} gönderilir.
 */
@Mixin(net.minecraft.client.MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void arcanum$loadoutScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || yOffset == 0.0) {
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
        // için bkz. ArcanumFabricClient.isScrollModifierDown.
        if (p == null || !com.arcanum.fabric.client.ArcanumFabricClient.isScrollModifierDown(mc)
                || !holdsWand(p)) {
            return;
        }
        int ns = Math.floorMod(ClientSpellData.activeSlot() + (yOffset > 0 ? -1 : 1), ClientSpellData.SLOTS);
        ClientPlayNetworking.send(new SetActivePayload(ClientSpellData.activePage(), ns));
        ci.cancel(); // vanilla hotbar kaydırmasını engelle
    }

    /**
     * ASA KENETLENMESİ sırasında SOL-TIK "asayı it" girdisine dönüşür: her fiziksel basış
     * bir kez sunucuya {@link LockPushPayload} yollar (tık SAYIMI + CPS TAVANI 18 otoritesi
     * sunucuda) ve normal saldırı/blok-kırma iptal edilir. Kenetlenme yokken hiçbir şey
     * yapmaz (vanilla davranış). Hedef: {@code MouseHandler#onPress(long,int,int,int)}
     * descriptor {@code (JIII)V}.
     */
    @Inject(method = "onPress(JIII)V", at = @At("HEAD"), cancellable = true)
    private void arcanum$lockClick(long window, int button, int action, int mods, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        // CRUCIO: kıvranırken sağ/sol tık İŞE YARAMAZ — ekran kapalıyken tüm basışları iptal et.
        if (mc.screen == null && action == GLFW.GLFW_PRESS && mc.player != null
                && mc.player.hasEffect(ModMobEffects.holderOf(ModMobEffects.CRUCIO))) {
            ci.cancel();
            return;
        }
        // Yalnız YEREL oyuncu bir düelloda ise (izleyici DEĞİL) sol-tık "asayı it"e döner.
        if (mc.screen != null || ClientLockState.myView() == null) {
            return;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || action != GLFW.GLFW_PRESS) {
            return;
        }
        ClientLockState.noteLocalClick();               // HUD elmas pulse'u
        ClientPlayNetworking.send(new LockPushPayload()); // ham tık → sunucu sayar
        ci.cancel();                                     // normal saldırı/blok-kırma iptal
    }

    private static boolean holdsWand(LocalPlayer p) {
        return p.getMainHandItem().getItem() instanceof WandItem
                || p.getOffhandItem().getItem() instanceof WandItem;
    }
}
