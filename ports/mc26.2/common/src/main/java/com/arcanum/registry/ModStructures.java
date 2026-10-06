package com.arcanum.registry;

import com.arcanum.Arcanum;
import com.arcanum.worldgen.AzkabanPiece;
import com.arcanum.worldgen.AzkabanStructure;
import com.arcanum.worldgen.ForgottenChamberPiece;
import com.arcanum.worldgen.ForgottenChamberStructure;
import com.arcanum.worldgen.HogsmeadePiece;
import com.arcanum.worldgen.HogsmeadeStructure;
import com.arcanum.platform.registry.DeferredRegister;
import com.arcanum.platform.registry.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Özel STRUCTURE kayıtları. Unutulmuş Oda artık bir worldgen FEATURE değil,
 * gerçek bir {@link net.minecraft.world.level.levelgen.structure.Structure} —
 * böylece {@code /locate structure arcanum:forgotten_chamber} çalışır.
 *
 * <p>İki registry:
 * <ul>
 *   <li>{@link Registries#STRUCTURE_TYPE} — Structure'ın codec fabrikası
 *       ({@link StructureType} fonksiyonel arayüzdür: tek metot {@code codec()}).</li>
 *   <li>{@link Registries#STRUCTURE_PIECE} — Piece'in NBT'den yeniden yükleme
 *       fabrikası ({@link StructurePieceType} fonksiyonel: tek metot
 *       {@code load(StructurePieceSerializationContext, CompoundTag)}).</li>
 * </ul>
 *
 * <p>Prosedürel üretim korunur: Structure yerleşim noktasını bulur, Piece tüm
 * blokları kod-tabanlı inşa eder (NBT/jigsaw YOK).
 */
public final class ModStructures {

    /** Structure tipi registry'si (Structure alt-sınıfının codec'ini taşır). */
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Arcanum.MODID, Registries.STRUCTURE_TYPE);

    /** Structure piece tipi registry'si (Piece'in NBT yükleyicisini taşır). */
    public static final DeferredRegister<StructurePieceType> PIECE_TYPES =
            DeferredRegister.create(Arcanum.MODID, Registries.STRUCTURE_PIECE);

    /**
     * Unutulmuş Oda structure tipi. {@code StructureType} fonksiyonel arayüz olduğundan
     * {@code () -> CODEC} lambda'sı doğrudan bir {@code StructureType<ForgottenChamberStructure>}
     * örneği verir.
     */
    public static final RegistrySupplier<StructureType<ForgottenChamberStructure>> FORGOTTEN_CHAMBER =
            STRUCTURE_TYPES.register("forgotten_chamber",
                    () -> (StructureType<ForgottenChamberStructure>) () -> ForgottenChamberStructure.CODEC);

    /**
     * Unutulmuş Oda piece tipi. Piece'in {@code (StructurePieceSerializationContext, CompoundTag)}
     * constructor'ı var; {@code StructurePieceType} SAM'i tam da bu imzadır.
     */
    public static final RegistrySupplier<StructurePieceType> FORGOTTEN_CHAMBER_PIECE =
            PIECE_TYPES.register("forgotten_chamber",
                    () -> (StructurePieceType) ForgottenChamberPiece::new);

    /**
     * Azkaban structure tipi. {@code StructureType} fonksiyonel arayüz olduğundan
     * {@code () -> CODEC} lambda'sı doğrudan bir {@code StructureType<AzkabanStructure>}
     * örneği verir.
     */
    public static final RegistrySupplier<StructureType<AzkabanStructure>> AZKABAN =
            STRUCTURE_TYPES.register("azkaban",
                    () -> (StructureType<AzkabanStructure>) () -> AzkabanStructure.CODEC);

    /**
     * Azkaban piece tipi. Piece'in {@code (StructurePieceSerializationContext, CompoundTag)}
     * constructor'ı var; {@code StructurePieceType} SAM'i tam da bu imzadır.
     */
    public static final RegistrySupplier<StructurePieceType> AZKABAN_PIECE =
            PIECE_TYPES.register("azkaban",
                    () -> (StructurePieceType) AzkabanPiece::new);

    /** Hogsmeade büyücü köyü structure tipi (bkz. HogsmeadeStructure). */
    public static final RegistrySupplier<StructureType<HogsmeadeStructure>> HOGSMEADE =
            STRUCTURE_TYPES.register("hogsmeade",
                    () -> (StructureType<HogsmeadeStructure>) () -> HogsmeadeStructure.CODEC);

    /** Hogsmeade piece tipi (NBT'den yeniden yükleme fabrikası). */
    public static final RegistrySupplier<StructurePieceType> HOGSMEADE_PIECE =
            PIECE_TYPES.register("hogsmeade",
                    () -> (StructurePieceType) HogsmeadePiece::new);

    private ModStructures() {}

    public static void init() {
        STRUCTURE_TYPES.register();
        PIECE_TYPES.register();
    }
}
