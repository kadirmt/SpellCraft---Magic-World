package com.arcanum.fabric.worldgen;

import com.arcanum.Arcanum;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Fabric worldgen kancaları (BiomeModifications ile vanilla biyomlara feature ekleme).
 */
public final class ArcanumWorldgenFabric {

    /** Arcane ruin placed feature anahtarı (data/arcanum/worldgen/placed_feature/arcane_ruin.json). */
    private static final ResourceKey<PlacedFeature> ARCANE_RUIN = ResourceKey.create(Registries.PLACED_FEATURE,
            new ResourceLocation(Arcanum.MODID, "arcane_ruin"));

    /** Ölümyiyen kampı placed feature anahtarı (data/arcanum/worldgen/placed_feature/death_eater_camp.json). */
    private static final ResourceKey<PlacedFeature> DEATH_EATER_CAMP = ResourceKey.create(Registries.PLACED_FEATURE,
            new ResourceLocation(Arcanum.MODID, "death_eater_camp"));

    // NOT: Unutulmuş Oda artık BiomeModifications ile eklenen bir feature DEĞİL;
    // gerçek bir Structure (data/arcanum/worldgen/structure + structure_set ile
    // vanilla structure yerleştirme sistemi tarafından üretilir). Bu yüzden burada
    // herhangi bir placed_feature enjeksiyonu yoktur.

    private ArcanumWorldgenFabric() {}

    public static void register() {
        // Kadim büyücü taş çemberi — tüm overworld biyomlarına eklenir; feature kendi
        // zeminini doğruladığı için okyanus/nehir ortasında zaten kendini iptal eder.
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.SURFACE_STRUCTURES,
                ARCANE_RUIN);

        // Ölümyiyen kampı — tüm overworld biyomları; zemin/sıvı doğrulamasını
        // feature kendi içinde yaptığı için ek biyom filtresi gerekmez.
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.SURFACE_STRUCTURES,
                DEATH_EATER_CAMP);
    }
}
