package com.arcanum.fabric.worldgen;

import java.util.function.Consumer;

import com.arcanum.Arcanum;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import terrablender.api.ParameterUtils;
import terrablender.api.Region;
import terrablender.api.RegionType;

/**
 * TerraBlender bölgesi — arcanum biyomlarını overworld iklim uzayına ekler.
 * <p>
 * arcanewood_grove: ılıman-nemli iç bölgeler (mevcut davranış korunur).
 * <p>
 * gloomwood: vanilla dark_forest'ın yaşadığı NEUTRAL/HUMID kuşağının çevresine
 * tek iklim varyasyonuyla (yükselen + alçalan mid-slice = 2 ParameterPoint) yayılır.
 * Bölge ağırlığı 2, vanilla overworld bölgesinin ağırlığı 10 olduğundan
 * (BOP da eklendiğinde 2/(10+10+2) ≈ %9) gloomwood nadir bir özel biyom olarak kalır.
 */
public class ArcanumRegion extends Region {
    public static final ResourceKey<Biome> ARCANEWOOD_GROVE = ResourceKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "arcanewood_grove"));
    public static final ResourceKey<Biome> GLOOMWOOD = ResourceKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "gloomwood"));

    public ArcanumRegion(ResourceLocation name, int weight) {
        super(name, RegionType.OVERWORLD, weight);
    }

    @Override
    public void addBiomes(Registry<Biome> registry,
                          Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        // --- arcanewood_grove: ılıman-nemli orta iç kesim ---
        this.addBiome(mapper,
                ParameterUtils.Temperature.NEUTRAL,
                ParameterUtils.Humidity.HUMID,
                ParameterUtils.Continentalness.MID_INLAND,
                ParameterUtils.Erosion.EROSION_2,
                ParameterUtils.Weirdness.MID_SLICE_NORMAL_ASCENDING,
                ParameterUtils.Depth.SURFACE,
                0.0F,
                ARCANEWOOD_GROVE);
        this.addBiome(mapper,
                ParameterUtils.Temperature.NEUTRAL,
                ParameterUtils.Humidity.HUMID,
                ParameterUtils.Continentalness.MID_INLAND,
                ParameterUtils.Erosion.EROSION_2,
                ParameterUtils.Weirdness.MID_SLICE_NORMAL_DESCENDING,
                ParameterUtils.Depth.SURFACE,
                0.0F,
                ARCANEWOOD_GROVE);

        // --- gloomwood: karanlık koru — dark_forest benzeri iklim, tek varyasyon (V1) ---
        // V1: ılıman/nemli iç kesim (NEAR_INLAND DEĞİL — kıyıya çok yakın olduğu
        // için ağaçlar kumsal/deniz kenarına taşıyordu, MID_INLAND'a çekildi)
        addGloomwoodPair(mapper,
                ParameterUtils.Temperature.NEUTRAL, ParameterUtils.Humidity.HUMID,
                ParameterUtils.Continentalness.MID_INLAND, ParameterUtils.Erosion.EROSION_3,
                false);
        // V2 ve V3 KALDIRILDI (oyuncu şikayeti: "the biome itself is also generating
        // way too often"). Gloomwood 3 iklim varyasyonuyla 6 ParameterPoint kaplıyordu —
        // arcanewood_grove'un (2 nokta) TAM 3 KATI iklim uzayı. Artık yalnız V1 var:
        // gloomwood 6 → 2 nokta, bölge içindeki kapsama ~1/3'e indi. Bölge ağırlığının
        // 4 → 2 düşüşüyle birleşince gloomwood eski sıklığının ~1/6'sına iner.
    }

    /** gloomwood'u verilen iklimde hem yükselen hem alçalan mid-slice'a ekler. */
    private void addGloomwoodPair(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper,
                                  ParameterUtils.Temperature temperature,
                                  ParameterUtils.Humidity humidity,
                                  ParameterUtils.Continentalness continentalness,
                                  ParameterUtils.Erosion erosion,
                                  boolean variantSlice) {
        ParameterUtils.Weirdness ascending = variantSlice
                ? ParameterUtils.Weirdness.MID_SLICE_VARIANT_ASCENDING
                : ParameterUtils.Weirdness.MID_SLICE_NORMAL_ASCENDING;
        ParameterUtils.Weirdness descending = variantSlice
                ? ParameterUtils.Weirdness.MID_SLICE_VARIANT_DESCENDING
                : ParameterUtils.Weirdness.MID_SLICE_NORMAL_DESCENDING;
        this.addBiome(mapper, temperature, humidity, continentalness, erosion,
                ascending, ParameterUtils.Depth.SURFACE, 0.0F, GLOOMWOOD);
        this.addBiome(mapper, temperature, humidity, continentalness, erosion,
                descending, ParameterUtils.Depth.SURFACE, 0.0F, GLOOMWOOD);
    }
}
