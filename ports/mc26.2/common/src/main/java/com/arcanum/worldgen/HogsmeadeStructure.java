package com.arcanum.worldgen;

import com.arcanum.registry.ModStructures;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * Hogsmeade — YÜZEY büyücü köyü, MEGA sürüm (~89x89 parsel: kuyu+meydan,
 * kıvrımlı sokaklar ve 11 bina — han, şekerci, asa dükkânı, İKSİRCİ,
 * baykuşhane, kitapçı, cübbeci, 2 çarpık büyücü evi, köy evi, çeşme köşesi).
 *
 * <p>{@link ForgottenChamberStructure} ile aynı mimari: gerçek bir {@link Structure}
 * (yani {@code /locate structure arcanum:hogsmeade} çalışır) ama üretim tamamen
 * PROSEDÜREL — bu sınıf yalnızca yüzeydeki yerleşim noktasını belirler, tüm
 * bloklar {@link HogsmeadePiece} tarafından kod-tabanlı inşa edilir.
 * NBT/jigsaw KULLANILMAZ.
 *
 * <p>Yerleşim: chunk merkezinin yüzey yüksekliği {@code WORLD_SURFACE_WG}
 * heightmap'i ile bulunur ve köy meydanının merkezi (origin) doğrudan bu yürüme
 * seviyesine oturtulur. Zemin düzleme (altını toprakla doldurma, üstünü
 * temizleme) piece içinde yapılır; JSON tarafındaki {@code beard_thin}
 * terrain adaptation da (köylerdeki gibi) eğimli arazide yapının altını besler.
 *
 * <p>Su üstüne köy kurulmasın diye deniz seviyesinin altında/hizasında kalan
 * yüzeyler (okyanus/göl/nehir kolonları) reddedilir — structure o chunk'ta
 * üretilmez.
 */
public class HogsmeadeStructure extends Structure {

    /**
     * {@code simpleCodec} yalnızca {@link Structure.StructureSettings}'i okur
     * (biyomlar, step, spawn_overrides, terrain_adaptation). Ekstra alan yok.
     */
    public static final MapCodec<HogsmeadeStructure> CODEC =
            simpleCodec(HogsmeadeStructure::new);

    public HogsmeadeStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        // ---- Kullanıcı config'i: yapı sıklığı kapısı (oyuncu isteği: "make all
        // structures ... configurable"). Seyreltme DETERMİNİSTİK: dünya seed'i +
        // chunk koordinatı hash'i (bkz. StructureGate); Math.random() KULLANILMAZ,
        // aynı seed + aynı ayar her zaman aynı dünyayı üretir. chance=0.0 → yapı yok.
        com.arcanum.config.ArcanumConfig arcanumCfg = com.arcanum.config.ArcanumConfig.get();
        if (!StructureGate.allow(context.seed(), context.chunkPos().x(), context.chunkPos().z(),
                0x486F67736DL, arcanumCfg.structureSpawnChance * arcanumCfg.hogsmeadeSpawnChance)) {
            return Optional.empty();
        }

        ChunkGenerator generator = context.chunkGenerator();
        LevelHeightAccessor heightAccessor = context.heightAccessor();

        int cx = context.chunkPos().getMiddleBlockX();
        int cz = context.chunkPos().getMiddleBlockZ();

        // Yüzeyin ilk boş (hava) Y'si — worldgen heightmap'i, dekorasyon öncesi güvenli.
        int surface = generator.getFirstFreeHeight(cx, cz,
                Heightmap.Types.WORLD_SURFACE_WG, heightAccessor, context.randomState());

        // Su kolonlarını ele: WORLD_SURFACE_WG su yüzeyini de "yüzey" sayar; deniz
        // seviyesi hizasında/altında kalan ilk-boş-Y okyanus/göl demektir — köy kurma.
        if (surface <= generator.getSeaLevel()) {
            return Optional.empty();
        }

        // Origin = meydan merkezi, yürüme seviyesi (ilk hava katı). Zemin düzleme
        // ve inşa HogsmeadePiece'te.
        BlockPos pos = new BlockPos(cx, surface, cz);
        return Optional.of(new Structure.GenerationStub(pos,
                builder -> builder.addPiece(new HogsmeadePiece(pos))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.HOGSMEADE.get();
    }
}
