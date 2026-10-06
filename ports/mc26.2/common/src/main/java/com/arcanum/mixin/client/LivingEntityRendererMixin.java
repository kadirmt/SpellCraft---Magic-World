package com.arcanum.mixin.client;

import com.arcanum.client.ClientUmbraForms;
import com.arcanum.client.duck.ArcanumRenderStateFlags;
import com.arcanum.item.CloakOfInvisibilityItem;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Render-state çıkarımı (extract) anında, entity hâlâ elimizdeyken kökteki katman-gizleme
 * koşulunun iki parçasını state'e yazar:
 * <ul>
 *   <li>{@code CloakOfInvisibilityItem.isWearing(entity)} → pelerin bayrağı</li>
 *   <li>{@code ClientUmbraForms.has(entity.getId())} → Umbravolo kara duman bayrağı</li>
 * </ul>
 * Katman mixin'leri (zırh/kafa/el/kanat) {@link ArcanumRenderStateFlags#shouldHideLayers} ile okur.
 *
 * <p>Hedef [javap 26.1.2, Fabric merged jar + Forge 64.1.3 yamalı jar — İKİSİNDE AYNI]:
 * {@code public void extractRenderState(T, S, float)}, descriptor
 * {@code (Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V}.
 * Generic T/S sınırlarına silinmiş GERÇEK metod hedeflenir; {@code EntityRenderer}'dan gelen
 * {@code (Entity, EntityRenderState, F)} sentetik köprü metodu DEĞİL.
 *
 * <p>Kapsam: {@code AvatarRenderer} (oyuncu/manken) ve {@code HumanoidMobRenderer} kendi
 * extractRenderState'lerinin ilk satırında {@code super.extractRenderState} çağırır (Forge yamalı
 * bytecode'da da invokespecial doğrulandı) → tüm canlı renderer'ları bu tek kancadan geçer.
 * HEAD seçildi: bayraklar yalnız entity'ye bağlı, metodun erken dönüşlerinden etkilenmez;
 * {@code createRenderState} her karede yeni state yarattığından ve iki bayrak da her çağrıda
 * (true/false) yeniden yazıldığından bayat değer kalmaz.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("HEAD")
    )
    private void arcanum$extractCloakFlags(LivingEntity entity, LivingEntityRenderState state, float partialTicks,
                                           CallbackInfo ci) {
        ArcanumRenderStateFlags flags = (ArcanumRenderStateFlags) state;
        flags.arcanum$setCloaked(CloakOfInvisibilityItem.isWearing(entity));
        flags.arcanum$setUmbraForm(ClientUmbraForms.has(entity.getId()));
    }
}
