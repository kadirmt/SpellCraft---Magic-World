package com.arcanum.fabric.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.BasiliskEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Basilisk BOSS renderer. GeckoLib geo/animation/texture dosyaları AYRI ajan
 * tarafından "basilisk" adıyla üretiliyor; burada yalnızca model bağlanır.
 */
public class BasiliskRenderer extends GeoEntityRenderer<BasiliskEntity> {
    public BasiliskRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<BasiliskEntity>(
                new ResourceLocation(Arcanum.MODID, "basilisk"), true));
        this.shadowRadius = 1.6f;
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack,
                                    BasiliskEntity animatable, BakedGeoModel model, boolean isReRender,
                                    float partialTick, int packedLight, int packedOverlay) {
        // Boss daha iri/heybetli görünsün — modeli %40 büyüt.
        super.scaleModelForRender(widthScale * 1.4f, heightScale * 1.4f, poseStack,
                animatable, model, isReRender, partialTick, packedLight, packedOverlay);
    }
}
