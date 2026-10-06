package com.arcanum.forge.client.render.model;

import com.arcanum.Arcanum;
import com.arcanum.entity.UnicornEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * Unicorn modeli — 7 rastgele doku varyantı ({@link UnicornEntity#getTextureVariant()},
 * 0-6, textures/entity/unicorn_0.png .. unicorn_6.png), geo/animasyon ortak.
 * Eyer, ana modeldeki "saddle" bone grubu {@link UnicornEntity#isSaddled()}'a göre
 * gizlenip/gösterilerek çözülür (ayrı bir eyer entity/model gerekmez — kaynaktaki
 * entity-swap hack'inin yerine geçen temiz çözüm).
 */
public class UnicornModel extends GeoModel<UnicornEntity> {
    @Override
    public ResourceLocation getModelResource(UnicornEntity entity) {
        return new ResourceLocation(Arcanum.MODID, "geo/entity/unicorn.geo.json");
    }

    @Override
    public void setCustomAnimations(UnicornEntity entity, long instanceId, AnimationState<UnicornEntity> state) {
        super.setCustomAnimations(entity, instanceId, state);
        getBone("saddle").ifPresent(bone -> bone.setHidden(!entity.isSaddled()));
    }

    @Override
    public ResourceLocation getTextureResource(UnicornEntity entity) {
        int v = Math.floorMod(entity.getTextureVariant(), 7);
        return new ResourceLocation(Arcanum.MODID, "textures/entity/unicorn_" + v + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(UnicornEntity entity) {
        return new ResourceLocation(Arcanum.MODID, "animations/entity/unicorn.animation.json");
    }
}
