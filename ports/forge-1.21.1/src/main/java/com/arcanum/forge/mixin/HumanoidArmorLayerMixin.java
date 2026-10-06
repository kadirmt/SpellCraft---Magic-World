package com.arcanum.forge.mixin;

import com.arcanum.forge.client.state.ClientUmbraForms;
import com.arcanum.item.CloakOfInvisibilityItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Görünmezlik Pelerini giyen varlık için zırh render katmanını tamamen iptal eder.
 * Böylece giyilen HİÇBİR zırh parçası (pelerinin kendi göğüslük modeli dahil, CHEST
 * slotu) çizilmez. Oyuncu gövdesi zaten vanilla INVISIBILITY efekti ile gizli olduğundan
 * bu katmanın kesilmesiyle TAM görünmezlik elde edilir.
 *
 * Forge 1.21.1 portu: kök fabric mixin'inin birebir kopyası — hedef vanilla sınıf,
 * MC sürümü aynı (1.21.1). Descriptor Forge recompiled.jar'da javap ile yeniden
 * doğrulandı (katman-bazlı iptal için Forge event'i yok; RenderPlayerEvent tüm
 * oyuncuyu keser, mobları kesmez → mixin olarak kalmalı).
 *
 * Hedef metod (javap ile doğrulandı):
 *   HumanoidArmorLayer#render(PoseStack, MultiBufferSource, int, T, float x6)
 *   descriptor: (Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V
 * T generic parametresi LivingEntity'ye erasure olur; explicit LivingEntity descriptor'ı
 * ile gerçek metod hedeflenir (sentetik Entity köprü metodu DEĞİL).
 *
 * remap = false: MinecraftForge 1.20.5+ çalışma zamanında resmi (mojmap) isimleri
 * kullanır; FG7'de reobf/refmap yoktur ve mixin annotation processor'a hiçbir
 * obfuscation eşleme verisi verilmez. Varsayılan remap=true bırakılırsa AP derlemede
 * "Unable to locate obfuscation mapping for @Inject target render" hatası üretir.
 * Derleme adı == çalışma adı olduğundan yeniden eşleme gereksizdir — resmi
 * MDKExamples mixins-only/fg7 örneği de üye anotasyonunda remap=false kullanır.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void arcanum$hideArmorWhenCloaked(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                              LivingEntity entity, float limbSwing, float limbSwingAmount,
                                              float partialTick, float ageInTicks, float netHeadYaw, float headPitch,
                                              CallbackInfo ci) {
        // Pelerin giyiliyse VEYA Umbravolo kara duman formundaysa (ClientUmbraForms
        // sunucu-senkronlu set) zırh katmanı çizilmez.
        if (CloakOfInvisibilityItem.isWearing(entity) || ClientUmbraForms.has(entity.getId())) {
            ci.cancel();
        }
    }
}
