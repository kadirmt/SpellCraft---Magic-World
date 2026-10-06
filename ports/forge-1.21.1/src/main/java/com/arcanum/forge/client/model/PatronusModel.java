package com.arcanum.forge.client.model;

import com.arcanum.Arcanum;
import com.arcanum.entity.PatronusEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Patronus modeli — üç görsel varyantın (0=baykuş, 1=kurt, 2=geyik) geo/doku/animasyon
 * dosyaları AYRIDIR ama animasyon klip adları ORTAKTIR
 * ({@code animation.patronus.idle/.walk/.attack}) — böylece {@link PatronusEntity}'deki
 * tek animasyon controller'ı üç varyanta da hizmet eder (PhoenixModel deseni).
 */
public class PatronusModel extends GeoModel<PatronusEntity> {

    /** Varyant → asset kimliği (geo/animasyon/doku dosya adlarının ortak kökü). */
    private static String variantName(PatronusEntity entity) {
        return switch (entity.getVariant()) {
            case PatronusEntity.VARIANT_WOLF -> "patronus_wolf";
            case PatronusEntity.VARIANT_STAG -> "patronus_stag";
            default -> "patronus_owl";
        };
    }

    @Override
    public ResourceLocation getModelResource(PatronusEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(Arcanum.MODID,
                "geo/entity/" + variantName(entity) + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PatronusEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(Arcanum.MODID,
                "textures/entity/" + variantName(entity) + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(PatronusEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(Arcanum.MODID,
                "animations/entity/" + variantName(entity) + ".animation.json");
    }
}
