package com.arcanum.fabric.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.BowtruckleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Bowtruckle GeckoLib renderer'ı — geo/entity/bowtruckle.geo.json + textures/entity/bowtruckle.png.
 * Yavrular %50 ölçekte çizilir (scaleModelForRender kancası).
 */
public class BowtruckleRenderer extends GeoEntityRenderer<BowtruckleEntity> {
    public BowtruckleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(
                new ResourceLocation(Arcanum.MODID, "bowtruckle"), true));
        this.shadowRadius = 0.2f;
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack,
                                    BowtruckleEntity animatable, BakedGeoModel model, boolean isReRender,
                                    float partialTick, int packedLight, int packedOverlay) {
        // Yavru bowtruckle yarı boy
        float babyScale = animatable.isBaby() ? 0.5f : 1.0f;
        super.scaleModelForRender(widthScale * babyScale, heightScale * babyScale, poseStack,
                animatable, model, isReRender, partialTick, packedLight, packedOverlay);
    }
}
