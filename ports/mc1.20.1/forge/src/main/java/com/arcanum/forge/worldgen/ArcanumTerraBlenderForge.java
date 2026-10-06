package com.arcanum.forge.worldgen;

import com.arcanum.Arcanum;
import com.arcanum.registry.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.SurfaceRules;
import terrablender.api.Regions;
import terrablender.api.SurfaceRuleManager;

/**
 * TerraBlender kurulumu — fabric'te "terrablender" entrypoint'iydi
 * (ArcanumTerraBlender implements TerraBlenderApi). Forge'da bu entrypoint
 * YOK; TerraBlender'ın Forge kalıbı {@code FMLCommonSetupEvent.enqueueWork}
 * içinde {@code Regions.register} + {@code SurfaceRuleManager.addSurfaceRules}
 * çağırmaktır (Getting-started wiki + TerraBlender-forge-1.20.1-3.0.1.10 javap:
 * terrablender.api.Regions.register(Region) / SurfaceRuleManager mevcut).
 * {@link com.arcanum.forge.ArcanumForge} commonSetup buradaki {@link #init()}'i çağırır.
 *
 * <p>Fabric'teki {@code ModBlocks.GLOOM_GRASS_BLOCK.listen(...)} sıralı-yükleme
 * hilesi Forge'da GEREKMEZ: commonSetup, registry kayıtlarından sonra koşar →
 * {@code .get()} doğrudan güvenli.
 */
public final class ArcanumTerraBlenderForge {
    private ArcanumTerraBlenderForge() {}

    public static void init() {
        // Bölge ağırlığı 2 (ESKİDEN 4) — oyuncu şikayeti: "the biome itself is also
        // generating way too often, even when using Biomes O' Plenty".
        // Alan payı: yalnız vanilla varken 2/(10+2) ≈ %17 (eskiden 4/14 ≈ %29);
        // BOP da kendi bölgesini eklediğinde 2/(10+10+2) ≈ %9 → "nadir özel biyom".
        // Daha da düşürmek biyomu pratikte bulunamaz kılardı (mod içeriğinin bir
        // kısmı — gloom ağaçları, mooncalf/dementor yoğunluğu — oraya bağlı).
        Regions.register(new ArcanumRegion(
                new ResourceLocation(Arcanum.MODID, "overworld"), 2));

        // Gloomwood yüzey kuralları: üst blok kasvet çimi, altındaki katman kasvet toprağı.
        SurfaceRuleManager.addSurfaceRules(
                SurfaceRuleManager.RuleCategory.OVERWORLD, Arcanum.MODID,
                makeGloomwoodRules(ModBlocks.GLOOM_GRASS_BLOCK.get(), ModBlocks.GLOOM_SOIL.get()));
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
