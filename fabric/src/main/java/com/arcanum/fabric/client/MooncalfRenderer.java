package com.arcanum.fabric.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.MooncalfEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Mooncalf GeckoLib renderer'ı — geo/entity/mooncalf.geo.json + textures/entity/mooncalf.png.
 * Yavrular %50 ölçekte çizilir (scaleModelForRender kancası).
 */
public class MooncalfRenderer extends GeoEntityRenderer<MooncalfEntity> {
    public MooncalfRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(
                ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "mooncalf"), true));
        this.shadowRadius = 0.4f;
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack,
                                    MooncalfEntity animatable, BakedGeoModel model, boolean isReRender,
                                    float partialTick, int packedLight, int packedOverlay) {
        // Yavru mooncalf yarı boy
        float babyScale = animatable.isBaby() ? 0.5f : 1.0f;
        super.scaleModelForRender(widthScale * babyScale, heightScale * babyScale, poseStack,
                animatable, model, isReRender, partialTick, packedLight, packedOverlay);
    }
}
