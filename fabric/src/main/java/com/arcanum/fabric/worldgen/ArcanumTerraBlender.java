package com.arcanum.fabric.worldgen;

import com.arcanum.Arcanum;
import com.arcanum.registry.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.SurfaceRules;
import terrablender.api.Regions;
import terrablender.api.SurfaceRuleManager;
import terrablender.api.TerraBlenderApi;

/**
 * TerraBlender giriş noktası (fabric.mod.json "terrablender" entrypoint).
 * Arcanum biyom bölgesini overworld'e kaydeder ve gloomwood'un özel zemin
 * (yüzey kuralı) katmanlarını tanımlar.
 */
public class ArcanumTerraBlender implements TerraBlenderApi {
    @Override
    public void onTerraBlenderInitialized() {
        // Bölge ağırlığı 2 (ESKİDEN 4) — oyuncu şikayeti: "the biome itself is also
        // generating way too often, even when using Biomes O' Plenty".
        // Alan payı: yalnız vanilla varken 2/(10+2) ≈ %17 (eskiden 4/14 ≈ %29);
        // BOP da kendi bölgesini eklediğinde 2/(10+10+2) ≈ %9 → "nadir özel biyom".
        // Daha da düşürmek biyomu pratikte bulunamaz kılardı (mod içeriğinin bir
        // kısmı — gloom ağaçları, mooncalf/dementor yoğunluğu — oraya bağlı).
        Regions.register(new ArcanumRegion(
                ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "overworld"), 2));

        // Gloomwood yüzey kuralları: bloklar Architectury DeferredRegister ile arcanum'un
        // ANA entrypoint'inde kaydedilir; TerraBlender bağımlılık olduğu için bu entrypoint
        // ondan ÖNCE çalışabilir. listen(...) giriş kaydedildiğinde (zaten kayıtlıysa hemen)
        // tetiklenir; GLOOM_GRASS_BLOCK, GLOOM_SOIL'den SONRA bildirildiği için bu callback
        // çalıştığında ikisi de hazırdır.
        ModBlocks.GLOOM_GRASS_BLOCK.listen(grass ->
                SurfaceRuleManager.addSurfaceRules(
                        SurfaceRuleManager.RuleCategory.OVERWORLD, Arcanum.MODID,
                        makeGloomwoodRules(grass, ModBlocks.GLOOM_SOIL.get())));
    }

    /**
     * Gloomwood biyomunda üst blok kasvet çimi, altındaki katman kasvet toprağı.
     * ON_FLOOR + waterBlockCheck: su altında kalmayan yüzeye çim (vanilla grass kalıbı);
     * UNDER_FLOOR: vanilla dirt katmanı gibi yüzey derinliği kadar (3-4 blok) toprak.
     * abovePreliminarySurface: kural mağara taban/tavanlarına sızmasın.
     */
    private static SurfaceRules.RuleSource makeGloomwoodRules(Block grass, Block soil) {
        return SurfaceRules.ifTrue(
                SurfaceRules.isBiome(ArcanumRegion.GLOOMWOOD),
                SurfaceRules.ifTrue(
                        SurfaceRules.abovePreliminarySurface(),
                        SurfaceRules.sequence(
                                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR,
                                        SurfaceRules.ifTrue(SurfaceRules.waterBlockCheck(-1, 0),
                                                makeStateRule(grass))),
                                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR,
                                        makeStateRule(soil)))));
    }

    /** Bloğun varsayılan durumunu döndüren yüzey kuralı (TerraBlender örnek kalıbı). */
    private static SurfaceRules.RuleSource makeStateRule(Block block) {
        return SurfaceRules.state(block.defaultBlockState());
    }
}
