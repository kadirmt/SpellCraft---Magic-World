package com.arcanum.forge.client.render.renderer;

import com.arcanum.Arcanum;
import com.arcanum.entity.WizardTraderEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Hogsmeade büyücü tüccarı GeckoLib renderer'ı. DefaultedEntityGeoModel otomatik yollar:
 * geo/entity/wizard_trader.geo.json, animations/entity/wizard_trader.animation.json,
 * textures/entity/wizard_trader.png
 * <p>
 * Varyant 1 (İKSİRCİ) aynı geo/animasyonu kullanır ama yeşil cüppeli simyacı
 * dokusuna geçer — {@link #getTextureLocation} senkronize varyant baytına bakar
 * (1.20.1 GeckoLib 4.8.4'te de {@code getTextureLocation(T)} jenerik imzalıdır).
 */
public class WizardTraderRenderer extends GeoEntityRenderer<WizardTraderEntity> {
    /** İksirci (varyant 1) dokusu — mor cüppenin koyu simyacı yeşiline kaydırılmış hali. */
    private static final ResourceLocation POTION_SELLER_TEXTURE =
            new ResourceLocation(Arcanum.MODID, "textures/entity/wizard_trader_potion.png");

    public WizardTraderRenderer(EntityRendererProvider.Context context) {
        // turnsHead=true: geo'daki "head" kemiği bakış yönüne döner —
        // LookAtPlayerGoal'un müşteriye bakışı görsel olarak da yansır.
        super(context, new DefaultedEntityGeoModel<>(
                new ResourceLocation(Arcanum.MODID, "wizard_trader"), true));
        this.shadowRadius = 0.5f;
    }

    @Override
    public ResourceLocation getTextureLocation(WizardTraderEntity entity) {
        return entity.getVariant() == WizardTraderEntity.VARIANT_POTION
                ? POTION_SELLER_TEXTURE
                : super.getTextureLocation(entity);
    }
}
