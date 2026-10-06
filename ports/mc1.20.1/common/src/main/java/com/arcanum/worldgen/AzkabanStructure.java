package com.arcanum.worldgen;

import com.arcanum.registry.ModStructures;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * AZKABAN — okyanusun ortasında yükselen, Ruh Emici (Dementor) dolu hapishane kulesi.
 *
 * <p>{@link ForgottenChamberStructure} ile aynı desendedir: gerçek bir {@link Structure}
 * ({@code /locate structure arcanum:azkaban} çalışır), üretim tamamen PROSEDÜREL —
 * bu sınıf yalnızca yerleşim noktasını (okyanus TABANI Y'si) belirler; tüm bloklar
 * {@link AzkabanPiece} tarafından kod-tabanlı inşa edilir. NBT/jigsaw KULLANILMAZ.
 *
 * <p>Yerleşim: chunk merkezini alır, okyanus tabanını {@code OCEAN_FLOOR_WG} ile bulur
 * (su sütunu yok sayılır) ve kule origin'ini oraya oturtur. Piece, tabandan y=50'ye
 * dek masif bir temel kolonu döker, kule y=50..94 arasında yükselir (deniz 63 →
 * tepe deniz seviyesinin ~30 üstünde). Origin Y'si güvenli banda kırpılır ki çok
 * derin/sığ uç noktalarda bounding box çökmesin.
 *
 * <p>İMZA ÖZELLİK: structure JSON'undaki {@code spawn_overrides} ile piece kutusu
 * içinde YALNIZCA {@code arcanum:dementor} doğar — kule içi neredeyse tamamen
 * karanlık bırakıldığından hücreler Ruh Emici kaynar.
 */
public class AzkabanStructure extends Structure {

    /**
     * {@code simpleCodec} yalnızca {@link Structure.StructureSettings}'i okur (biyomlar,
     * step, spawn_overrides, terrain_adaptation). Ekstra özel alan yoktur.
     * 1.20.1'de {@code simpleCodec} {@code Codec} döner ({@code MapCodec} 1.21+).
     */
    public static final Codec<AzkabanStructure> CODEC =
            simpleCodec(AzkabanStructure::new);

    public AzkabanStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        // ---- Kullanıcı config'i: yapı sıklığı kapısı (oyuncu isteği: "make all
        // structures ... configurable"). Seyreltme DETERMİNİSTİK: dünya seed'i +
        // chunk koordinatı hash'i (bkz. StructureGate); Math.random() KULLANILMAZ,
        // aynı seed + aynı ayar her zaman aynı dünyayı üretir. chance=0.0 → yapı yok.
        com.arcanum.config.ArcanumConfig arcanumCfg = com.arcanum.config.ArcanumConfig.get();
        if (!StructureGate.allow(context.seed(), context.chunkPos().x, context.chunkPos().z,
                0x417A6B61L, arcanumCfg.structureSpawnChance * arcanumCfg.azkabanSpawnChance)) {
            return Optional.empty();
        }

        ChunkGenerator generator = context.chunkGenerator();
        LevelHeightAccessor heightAccessor = context.heightAccessor();

        int cx = context.chunkPos().getMiddleBlockX();
        int cz = context.chunkPos().getMiddleBlockZ();

        // Okyanus TABANI yüksekliği (worldgen heightmap'i; su sütunu katı sayılmaz).
        // Kule temeli bu Y'den başlar, gövde sabit y=50..94 bandında yükselir.
        int seabed = generator.getFirstOccupiedHeight(cx, cz,
                Heightmap.Types.OCEAN_FLOOR_WG, heightAccessor, context.randomState());

        // Güvenli banda kırp: taban çok derinse bedrock bandına inme, çok sığsa
        // (nadir sığlık) kule tabanının biraz üstünde kalabilir — temel döngüsü
        // bu durumda kendiliğinden boş kalır (AzkabanPiece bunu tolere eder).
        int minY = heightAccessor.getMinBuildHeight();
        int y = Mth.clamp(seabed, minY + 6, AzkabanPiece.TOWER_BASE_Y + 6);

        BlockPos pos = new BlockPos(cx, y, cz);
        return Optional.of(new Structure.GenerationStub(pos,
                builder -> builder.addPiece(new AzkabanPiece(pos))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.AZKABAN.get();
    }
}
