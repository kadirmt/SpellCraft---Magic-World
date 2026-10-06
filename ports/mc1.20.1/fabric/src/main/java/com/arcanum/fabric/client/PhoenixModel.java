package com.arcanum.fabric.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.PhoenixEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Phoenix modeli — geo/animasyon iki varyant için ortak (kaynakta da öyleydi),
 * yalnızca doku {@link PhoenixEntity#getVariant()}'a göre değişir
 * (0 = textures/entity/phoenix.png, 1 = textures/entity/phoenix_v2.png).
 */
public class PhoenixModel extends GeoModel<PhoenixEntity> {
    @Override
    public ResourceLocation getModelResource(PhoenixEntity entity) {
        return new ResourceLocation(Arcanum.MODID, "geo/entity/phoenix.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PhoenixEntity entity) {
        String name = entity.getVariant() == 1 ? "phoenix_v2" : "phoenix";
        return new ResourceLocation(Arcanum.MODID, "textures/entity/" + name + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(PhoenixEntity entity) {
        return new ResourceLocation(Arcanum.MODID, "animations/entity/phoenix.animation.json");
    }
}
