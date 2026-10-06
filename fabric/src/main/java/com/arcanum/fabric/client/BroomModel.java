package com.arcanum.fabric.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.BroomEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Süpürge modeli — kademe synched byte'ına göre geo + doku + animasyon seçer
 * (PatronusModel deseni):
 * <ul>
 *   <li>0 Oakshaft 79 — kendi geo'su ({@code geometry.broom_oakshaft}, eski
 *       süpürge modeli) + kendi dokusu</li>
 *   <li>1 Comet 260 — Cleansweep geo'sunu paylaşır, dokusu mavi-gümüş recolor</li>
 *   <li>2 Cleansweep — mevcut broom geo + doku</li>
 * </ul>
 * Animasyon klip adları TÜM kademelerde ORTAKTIR ({@code animation.broom.idle/.fly})
 * — böylece {@link BroomEntity}'deki tek animasyon controller'ı üçüne de hizmet eder.
 */
public class BroomModel extends GeoModel<BroomEntity> {

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, path);
    }

    @Override
    public ResourceLocation getModelResource(BroomEntity entity) {
        return entity.getTier() == BroomEntity.TIER_OAKSHAFT
                ? rl("geo/entity/broom_oakshaft.geo.json")
                : rl("geo/entity/broom.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(BroomEntity entity) {
        return switch (entity.getTier()) {
            case BroomEntity.TIER_OAKSHAFT -> rl("textures/entity/broom_oakshaft.png");
            case BroomEntity.TIER_COMET -> rl("textures/entity/broom_comet.png");
            default -> rl("textures/entity/broom.png");
        };
    }

    @Override
    public ResourceLocation getAnimationResource(BroomEntity entity) {
        return entity.getTier() == BroomEntity.TIER_OAKSHAFT
                ? rl("animations/entity/broom_oakshaft.animation.json")
                : rl("animations/entity/broom.animation.json");
    }
}
