package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.item.WandItem;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

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
 */
public class WandItemRenderer extends GeoItemRenderer<WandItem> {
    /** assetId -> özel geo dosya adı (geo/item/&lt;değer&gt;.geo.json). */
    private static final Map<String, String> ALT_MODELS = Map.of(
            "arcanewood_wand", "wand_arcanewood",
            "troll_wand", "wand_troll",
            "phoenix_wand", "wand_phoenix",
            "elder_wand", "wand_elder",
            "thunderbird_wand", "wand_thunderbird");

    public WandItemRenderer(String assetId) {
        super(buildModel(assetId));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    private static DefaultedItemGeoModel<WandItem> buildModel(String assetId) {
        DefaultedItemGeoModel<WandItem> model = new DefaultedItemGeoModel<WandItem>(
                ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "wand"))
                .withAltTexture(ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, assetId));
        String altModel = ALT_MODELS.get(assetId);
        if (altModel != null) {
            // withAltModel yalnızca geo yolunu değiştirir; animasyon "wand"
            // olarak kalır, texture mantığına dokunmaz.
            model = model.withAltModel(
                    ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, altModel));
        }
        return model;
    }
}
