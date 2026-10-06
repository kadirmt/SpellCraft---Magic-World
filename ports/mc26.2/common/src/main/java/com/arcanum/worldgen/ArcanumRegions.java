package com.arcanum.worldgen;

import com.arcanum.Arcanum;
import com.arcanum.registry.ModBlocks;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.SurfaceRules;
import terrablender.api.Regions;
import terrablender.api.SurfaceRuleManager;

/**
 * TerraBlender kaydı (loader-bağımsız) — kök {@code fabric/worldgen/ArcanumTerraBlender} mantığı ikiye bölündü:
 * <ul>
 *   <li>{@link #registerRegions()} — YALNIZ {@code ResourceKey}/sabitlerle çalışır; Platform/registry erişimi YOK.
 *       Fabric: {@code "terrablender"} entrypoint'i ({@code ArcanumTerraBlender}) çağırır — bu entrypoint arcanum'un
 *       ana entrypoint'inden ÖNCE koşabilir. Forge: {@code FMLCommonSetupEvent.enqueueWork}.</li>
 *   <li>{@link #registerSurfaceRules()} — blokları ister ({@code .get()}), bu yüzden blok kaydı bittikten SONRA:
 *       {@code Arcanum.commonSetup()} (iki loader). Kökteki {@code GLOOM_GRASS_BLOCK.listen(...)} deseninin
 *       eşdeğeri; TerraBlender kuralları statik haritada tutar ve dünya açılışında okur → sıra bağımsız.</li>
 * </ul>
 * 26.2 notu (res-delta-26.2 §1 #8): {@code SurfaceRules.isBiome(HolderGetter, keys)} + TerraBlender
 * {@code addSurfaceRules(…, RuleBuilder)} değişikliği YALNIZ bu dosyayı etkiler.
 */
public final class ArcanumRegions {
    /**
     * Bölge ağırlığı 2 (ESKİDEN 4) — oyuncu şikayeti: "the biome itself is also
     * generating way too often, even when using Biomes O' Plenty".
     * Alan payı: yalnız vanilla varken 2/(10+2) ≈ %17 (eskiden 4/14 ≈ %29);
     * BOP da kendi bölgesini eklediğinde 2/(10+10+2) ≈ %9 → "nadir özel biyom".
     * Daha da düşürmek biyomu pratikte bulunamaz kılardı (mod içeriğinin bir
     * kısmı — gloom ağaçları, mooncalf/dementor yoğunluğu — oraya bağlı).
     */
    public static final int REGION_WEIGHT = 2;

    private ArcanumRegions() {}

    /** Arcanum biyom bölgesini overworld'e kaydeder. */
    public static void registerRegions() {
        Regions.register(new ArcanumRegion(
                Identifier.fromNamespaceAndPath(Arcanum.MODID, "overworld"), REGION_WEIGHT));
    }

    /** Gloomwood'un özel zemin (yüzey kuralı) katmanlarını tanımlar. Bloklar kayıtlı OLMALI. */
    public static void registerSurfaceRules() {
        // Bloklar KAYIT anında çözülür (26.1.2 ile aynı zamanlama; kayıtsızsa burada erken patlar).
        // 26.2 TerraBlender: kural nesnesi değil RuleBuilder (HolderGetter<Biome> -> RuleSource) saklanır;
        // TerraBlender onu SurfaceRuleManager.repopulateRules(biomes) anında (biyom kaydı hazırken) çağırır.
        final Block grass = ModBlocks.GLOOM_GRASS_BLOCK.get();
        final Block soil = ModBlocks.GLOOM_SOIL.get();
        SurfaceRuleManager.addSurfaceRules(
                SurfaceRuleManager.RuleCategory.OVERWORLD, Arcanum.MODID,
                biomes -> makeGloomwoodRules(biomes, grass, soil));
    }

    /**
     * Gloomwood biyomunda üst blok kasvet çimi, altındaki katman kasvet toprağı.
     * ON_FLOOR + waterBlockCheck: su altında kalmayan yüzeye çim (vanilla grass kalıbı);
     * UNDER_FLOOR: vanilla dirt katmanı gibi yüzey derinliği kadar (3-4 blok) toprak.
     * abovePreliminarySurface: kural mağara taban/tavanlarına sızmasın.
     */
    private static SurfaceRules.RuleSource makeGloomwoodRules(HolderGetter<Biome> biomes, Block grass, Block soil) {
        return SurfaceRules.ifTrue(
                SurfaceRules.isBiome(biomes, ArcanumRegion.GLOOMWOOD),
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
