package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.BroomEntity;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;

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
 * <p>
 * GeckoLib 5: model/doku çözümü render sırasında entity'ye erişemez → kademe
 * {@link #addAdditionalStateData} içinde {@link #TIER} veri biletine yazılır, model/doku
 * seçimi render-state'ten okunur. Model/animasyon kimlikleri {@code geckolib/models|animations/}
 * altındaki öneksiz+uzantısız yoldur ({@code arcanum:entity/broom}); doku tam yoldur.
 */
public class BroomModel extends GeoModel<BroomEntity> {

    /** Render-state'e taşınan süpürge kademesi ({@link BroomEntity#getTier()}). */
    public static final DataTicket<Byte> TIER = DataTicket.create("arcanum_broom_tier", Byte.class);

    private static byte tier(GeoRenderState renderState) {
        Byte t = renderState.getGeckolibData(TIER);
        return t == null ? BroomEntity.TIER_CLEANSWEEP : t;
    }

    @Override
    public void addAdditionalStateData(BroomEntity entity, Object relatedObject, GeoRenderState renderState) {
        renderState.addGeckolibData(TIER, entity.getTier());
    }

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return tier(renderState) == BroomEntity.TIER_OAKSHAFT
                ? Arcanum.id("entity/broom_oakshaft")
                : Arcanum.id("entity/broom");
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return switch (tier(renderState)) {
            case BroomEntity.TIER_OAKSHAFT -> Arcanum.id("textures/entity/broom_oakshaft.png");
            case BroomEntity.TIER_COMET -> Arcanum.id("textures/entity/broom_comet.png");
            default -> Arcanum.id("textures/entity/broom.png");
        };
    }

    @Override
    public Identifier getAnimationResource(BroomEntity entity) {
        return entity.getTier() == BroomEntity.TIER_OAKSHAFT
                ? Arcanum.id("entity/broom_oakshaft")
                : Arcanum.id("entity/broom");
    }
}
