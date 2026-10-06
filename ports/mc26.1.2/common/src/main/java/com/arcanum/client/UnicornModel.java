package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.UnicornEntity;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;

/**
 * Unicorn modeli — 7 rastgele doku varyantı ({@link UnicornEntity#getTextureVariant()},
 * 0-6, textures/entity/unicorn_0.png .. unicorn_6.png), geo/animasyon ortak.
 * Eyer, ana modeldeki "saddle" bone grubu {@link UnicornEntity#isSaddled()}'a göre
 * gizlenip/gösterilerek çözülür (ayrı bir eyer entity/model gerekmez — kaynaktaki
 * entity-swap hack'inin yerine geçen temiz çözüm).
 * <p>
 * GeckoLib 5: kemikler immutable ve {@code setCustomAnimations} yok → eyer durumu ile
 * doku varyantı {@link #addAdditionalStateData} içinde render-state'e yazılır; kemik
 * gizleme {@link UnicornRenderer#adjustModelBonesForRender}'da yapılır.
 */
public class UnicornModel extends GeoModel<UnicornEntity> {
    /** Eyer takılı mı — "saddle" kemiğinin görünürlüğü buna bağlı. */
    public static final DataTicket<Boolean> SADDLED =
            DataTicket.create("arcanum_unicorn_saddled", Boolean.class);
    /** Doku varyantı (0-6, floorMod 7 uygulanmış hali). */
    public static final DataTicket<Integer> TEXTURE_VARIANT =
            DataTicket.create("arcanum_unicorn_texture_variant", Integer.class);

    private static final Identifier MODEL = Arcanum.id("entity/unicorn");
    private static final Identifier ANIMATION = Arcanum.id("entity/unicorn");
    private static final Identifier[] TEXTURES = new Identifier[7];

    static {
        for (int i = 0; i < TEXTURES.length; i++) {
            TEXTURES[i] = Arcanum.id("textures/entity/unicorn_" + i + ".png");
        }
    }

    @Override
    public void addAdditionalStateData(UnicornEntity entity, Object relatedObject, GeoRenderState renderState) {
        renderState.addGeckolibData(SADDLED, entity.isSaddled());
        renderState.addGeckolibData(TEXTURE_VARIANT, Math.floorMod(entity.getTextureVariant(), 7));
    }

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        Integer v = renderState.getGeckolibData(TEXTURE_VARIANT);
        return TEXTURES[v == null ? 0 : Math.floorMod(v, 7)];
    }

    @Override
    public Identifier getAnimationResource(UnicornEntity entity) {
        return ANIMATION;
    }
}
