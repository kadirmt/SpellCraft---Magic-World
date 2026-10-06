package com.arcanum.registry;

import com.arcanum.Arcanum;
import com.arcanum.worldgen.ArcaneRuinFeature;
import com.arcanum.worldgen.DeathEaterCampFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Özel worldgen feature kayıtları (ör. arcane ruin).
 */
public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, Arcanum.MODID);

    /** Kadim büyücü taş çemberi — configured/placed JSON'ları data/arcanum/worldgen altında. */
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> ARCANE_RUIN =
            FEATURES.register("arcane_ruin", () -> new ArcaneRuinFeature(NoneFeatureConfiguration.CODEC));

    /** Ölümyiyen kampı — çadırlar + soul campfire + loot sandığı + 2-3 Ölümyiyen spawn'u. */
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> DEATH_EATER_CAMP =
            FEATURES.register("death_eater_camp", () -> new DeathEaterCampFeature(NoneFeatureConfiguration.CODEC));

    // NOT: Unutulmuş Oda artık bir FEATURE değil, gerçek bir Structure. Bkz.
    // com.arcanum.registry.ModStructures + com.arcanum.worldgen.ForgottenChamberStructure.

    private ModFeatures() {}

    public static void register(IEventBus bus) {
        FEATURES.register(bus);
    }
}
