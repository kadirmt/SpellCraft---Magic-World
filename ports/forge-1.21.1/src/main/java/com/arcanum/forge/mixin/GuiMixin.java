package com.arcanum.forge.mixin;

import com.arcanum.forge.client.ui.CastBarHud;
import com.arcanum.forge.client.ui.LockTugHud;
import com.arcanum.forge.client.ui.ManaHud;
import com.arcanum.forge.client.ui.SpellSlotsHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 4 Arcanum HUD'unu vanilla {@code Gui.render}'ın SONUNDA çizer — kök fabric
 * {@code HudRenderCallback}'in birebir karşılığı (Fabric API de aynı noktaya,
 * {@code Gui.render} TAIL'ine inject eder).
 *
 * <p><b>Neden Forge event'i DEĞİL:</b> Forge 1.21.1'de HUD overlay API'si 52.x
 * içinde değişti — {@code AddGuiOverlayLayersEvent}/{@code ForgeLayeredDraw}
 * yalnız GEÇ build'lerde var (52.1.15'te doğrulandı), erken 52.0.x kurulumlarında
 * YOK ({@code NoClassDefFoundError} ile mod yüklenmiyordu). Eski
 * {@code RenderGuiEvent} ise 1.21.1'de tamamen KALDIRILDI (52.1.15 universal
 * jar'ında sınıf yok — javap ile doğrulandı). Vanilla {@code Gui.render} imzası
 * {@code (GuiGraphics, DeltaTracker)} ise TÜM 1.21.1 build'lerinde aynıdır;
 * bu mixin her 52.x sürümünde çalışır.
 *
 * <p>Çizim sırası kök fabric kayıt sırasıyla birebir: Mana → SpellSlots →
 * CastBar → LockTug. TAIL = tüm vanilla katmanların üstü (eski ForgeLayeredDraw
 * sonuna ekleme ile aynı görsel sonuç). Her HUD kendi null/hideGui kontrolünü
 * yapar.
 *
 * <p>remap = false zorunlu (bkz. HumanoidArmorLayerMixin notu: FG7'de
 * refmap/obf eşlemesi yok, resmi isimler çalışma adıdır).
 *
 * <p>Hedef metod (javap, recompiled 1.21.1-52.1.15 jar):
 *   {@code public void render(net.minecraft.client.gui.GuiGraphics, net.minecraft.client.DeltaTracker)}
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("TAIL"),
            remap = false
    )
    private void arcanum$renderArcanumHuds(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        // Sol-alt HUD: mana barı + büyü dizilim slotları
        ManaHud.render(guiGraphics, deltaTracker);
        SpellSlotsHud.render(guiGraphics, deltaTracker);
        // İmleç altı minik cast göstergesi
        CastBarHud.render(guiGraphics, deltaTracker);
        // Asa kenetlenmesi tug-of-war HUD'u (crosshair üstü denge çubuğu)
        LockTugHud.render(guiGraphics, deltaTracker);
    }
}
