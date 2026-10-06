package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.item.WandItem;
import com.geckolib.model.DefaultedItemGeoModel;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import java.util.Map;

/**
 * Asa için GeckoLib item renderer'ı. Animasyon paylaşımlıdır
 * (animations/item/wand.animation.json); texture kademeye göre değişir
 * (textures/item/&lt;assetId&gt;.png + _glowmask emissive). Geometri de
 * varsayılan olarak paylaşımlıdır (geo/item/wand.geo.json) — özel modele
 * sahip kademeler ALT_MODELS haritasından kendi geo dosyasını alır
 * (arcanewood: topluluk sanatçısı modeli; troll: Meshy "Crimson Rail Wand"
 * tasarımından el yapımı küp uyarlaması; phoenix: Kasu'nun wand_lord
 * bbmodel dönüşümü; elder: wand_eldergecko bbmodel dönüşümü; thunderbird:
 * el yapımı "Storm Spire" tasarımı). Kemik adları her modelde
 * wand_root + tip aynı olduğundan paylaşımlı animasyon hepsini
 * değiştirmeden sürer.
 *
 * <p>GeckoLib 5.5.2: paket {@code com.geckolib}; {@code addRenderLayer} → {@code withRenderLayer};
 * kimlikler aynı ({@code DefaultedItemGeoModel} "item/" önekini kendisi ekler) — dosyalar
 * {@code assets/arcanum/geckolib/models/item/*.geo.json} ve {@code geckolib/animations/item/wand.animation.json}
 * altında aranır. Item'ın {@code items/<id>.json} tanımı {@code minecraft:special} + {@code geckolib:geckolib}
 * (kaynak tarafı); burada ek kayıt YOK.
 */
public class WandItemRenderer extends GeoItemRenderer<WandItem> {
    /** assetId -> özel geo dosya adı (geckolib/models/item/&lt;değer&gt;.geo.json). */
    private static final Map<String, String> ALT_MODELS = Map.of(
            "arcanewood_wand", "wand_arcanewood",
            "troll_wand", "wand_troll",
            "phoenix_wand", "wand_phoenix",
            "elder_wand", "wand_elder",
            "thunderbird_wand", "wand_thunderbird");

    public WandItemRenderer(String assetId) {
        super(buildModel(assetId));
        withRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    private static DefaultedItemGeoModel<WandItem> buildModel(String assetId) {
        DefaultedItemGeoModel<WandItem> model = new DefaultedItemGeoModel<WandItem>(Arcanum.id("wand"))
                .withAltTexture(Arcanum.id(assetId));
        String altModel = ALT_MODELS.get(assetId);
        if (altModel != null) {
            // withAltModel yalnızca geo yolunu değiştirir; animasyon "wand"
            // olarak kalır, texture mantığına dokunmaz.
            model = model.withAltModel(Arcanum.id(altModel));
        }
        return model;
    }
}
