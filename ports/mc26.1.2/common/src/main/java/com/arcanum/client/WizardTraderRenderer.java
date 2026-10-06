package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.WizardTraderEntity;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Hogsmeade büyücü tüccarı GeckoLib renderer'ı. DefaultedEntityGeoModel otomatik kimlikler:
 * model arcanum:entity/wizard_trader (geckolib/models/entity/wizard_trader.geo.json),
 * animasyon arcanum:entity/wizard_trader (geckolib/animations/entity/wizard_trader.animation.json),
 * doku textures/entity/wizard_trader.png
 * <p>
 * Varyant 1 (İKSİRCİ) aynı geo/animasyonu kullanır ama yeşil cüppeli simyacı
 * dokusuna geçer — {@link #getTextureLocation} senkronize varyant baytına bakar.
 * GeckoLib 5'te render sırasında entity yok → varyant {@link #addRenderData} ile
 * {@link #VARIANT} biletine yazılır, doku seçimi render-state'ten okunur.
 */
public class WizardTraderRenderer<R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<WizardTraderEntity, R> {
    /** İksirci (varyant 1) dokusu — mor cüppenin koyu simyacı yeşiline kaydırılmış hali. */
    private static final Identifier POTION_SELLER_TEXTURE =
            Arcanum.id("textures/entity/wizard_trader_potion.png");

    /** Senkron tüccar varyantı — render-state'e çıkarım aşamasında yazılır. */
    public static final DataTicket<Integer> VARIANT =
            DataTicket.create("arcanum_wizard_trader_variant", Integer.class);

    public WizardTraderRenderer(EntityRendererProvider.Context context) {
        // turnsHead: geo'daki "head" kemiği bakış yönüne döner —
        // LookAtPlayerGoal'un müşteriye bakışı görsel olarak da yansır
        // (GeckoLib 5'te adjustModelBonesForRender ile).
        super(context, new DefaultedEntityGeoModel<>(Arcanum.id("wizard_trader")));
        this.shadowRadius = 0.5f;
    }

    @Override
    public void addRenderData(WizardTraderEntity animatable, Void relatedObject, R renderState, float partialTick) {
        renderState.addGeckolibData(VARIANT, animatable.getVariant());
    }

    @Override
    public Identifier getTextureLocation(R renderState) {
        Integer variant = renderState.getGeckolibData(VARIANT);
        return variant != null && variant == WizardTraderEntity.VARIANT_POTION
                ? POTION_SELLER_TEXTURE
                : super.getTextureLocation(renderState);
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> info, BoneSnapshots snapshots) {
        GeckoLib4HeadRotation.apply(info, snapshots, "head");
    }
}
