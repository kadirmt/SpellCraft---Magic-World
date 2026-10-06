package com.arcanum.registry;

import java.util.Set;

import com.arcanum.Arcanum;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Wizard köylü mesleği — iş istasyonu Büyü Masası (arcanum:spell_table).
 * Vanilla Librarian+Lectern ile birebir aynı ticketCount/searchDistance (1, 1)
 * kullanılır. Ticaret teklifleri (Başlangıç Büyü Kitabı satışı) loader
 * katmanında (ArcanumForge, VillagerTradesEvent) kaydedilir — bu sınıf
 * yalnızca POI + profession kaydını içerir.
 *
 * <p>WIZARD_POI supplier'ındaki {@code ModBlocks.SPELL_TABLE.get()} güvenlidir:
 * Forge, BLOCK registry'sini her şeyden önce doldurur; POI kaydı çok sonra gelir.
 */
public final class ModVillagers {
    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, Arcanum.MODID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, Arcanum.MODID);

    public static final ResourceKey<PoiType> WIZARD_POI_KEY = ResourceKey.create(
            Registries.POINT_OF_INTEREST_TYPE, ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "wizard"));

    /** Büyü Masası'nın olası tüm blockstate'lerini (4 facing) POI eşleşen state seti yapar. */
    public static final RegistryObject<PoiType> WIZARD_POI = POI_TYPES.register("wizard",
            () -> new PoiType(Set.copyOf(ModBlocks.SPELL_TABLE.get().getStateDefinition().getPossibleStates()), 1, 1));

    /** Wizard mesleği — Büyü Masası'nı iş istasyonu olarak talep eder (vanilla Librarian paritesi). */
    public static final RegistryObject<VillagerProfession> WIZARD = PROFESSIONS.register("wizard",
            () -> new VillagerProfession(
                    "wizard",
                    holder -> holder.is(WIZARD_POI_KEY),
                    holder -> holder.is(WIZARD_POI_KEY),
                    com.google.common.collect.ImmutableSet.of(),
                    com.google.common.collect.ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_LIBRARIAN));

    private ModVillagers() {}

    public static void register(IEventBus bus) {
        POI_TYPES.register(bus);
        PROFESSIONS.register(bus);
    }
}
