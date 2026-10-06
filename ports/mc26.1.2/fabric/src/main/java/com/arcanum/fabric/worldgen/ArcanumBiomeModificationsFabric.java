package com.arcanum.fabric.worldgen;

import com.arcanum.server.ArcanumBiomeModifications;
import com.arcanum.server.ArcanumBiomeModifications.FeatureEntry;
import com.arcanum.server.ArcanumBiomeModifications.Selector;
import com.arcanum.server.ArcanumBiomeModifications.SpawnEntry;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;

import java.util.function.Predicate;

/**
 * Fabric: ortak {@link ArcanumBiomeModifications} tablosunu {@code BiomeModifications} API'siyle uygular.
 * Seçiciler kökle BİREBİR: {@code foundInOverworld()} ve {@code includeByKey(...)}; çağrı sırası kökle aynı
 * (kökte addSpawn ×24 ArcanumFabric'te, addFeature ×2 ArcanumWorldgenFabric.register()'da; ikisi de onInitialize içinde).
 * {@code .get()} burada — kayıtlar bağlandıktan sonra ({@code ArcanumFabric.onInitialize}, commonSetup sonrası).
 */
public final class ArcanumBiomeModificationsFabric {
    private ArcanumBiomeModificationsFabric() {}

    public static void register() {
        // Kök sırası: ArcanumWorldgenFabric.register() (addFeature ×2) onInitialize'da addSpawn bloğundan ÖNCE.
        for (FeatureEntry f : ArcanumBiomeModifications.features()) {
            BiomeModifications.addFeature(selector(f.biomes()), f.step(), f.feature());
        }
        for (SpawnEntry s : ArcanumBiomeModifications.spawns()) {
            BiomeModifications.addSpawn(selector(s.biomes()), s.category(), s.type().get(),
                    s.weight(), s.minCount(), s.maxCount());
        }
    }

    private static Predicate<BiomeSelectionContext> selector(Selector sel) {
        return sel.overworld() ? BiomeSelectors.foundInOverworld() : BiomeSelectors.includeByKey(sel.keys());
    }
}
