package com.arcanum.worldgen;

import com.arcanum.registry.ModStructures;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * Unutulmuş Oda — yeraltı BOSS zindanı (Basilisk) + 3 loot sandığı.
 *
 * <p>Eskiden bir worldgen FEATURE'dı; artık gerçek bir {@link Structure}. Bu sayede
 * {@code /locate structure arcanum:forgotten_chamber} onu bulabilir (tıpkı
 * trial_chambers gibi). Üretim yine tamamen PROSEDÜREL: bu sınıf yalnızca yerleşim
 * noktasını (yeraltı Y'si) belirler; tüm bloklar {@link ForgottenChamberPiece}
 * tarafından kod-tabanlı inşa edilir. NBT/jigsaw KULLANILMAZ.
 *
 * <p>Yerleşim: chunk merkezini alır, yüzeyi {@code WORLD_SURFACE_WG} ile bulur ve
 * odayı yüzeyin ~38 blok altına — MADEN/MINESHAFT bandına — oturtur. Origin (oda
 * merkezi) Y'si güvenli banda kırpılır: taban {@code minBuildHeight+2}'nin altına
 * inmez, salon tavanı yüzeyin altında kalır. Kabuk/duvarlar mağara/lava/su'yu
 * mühürlediği için ek uygunluk taraması gerekmez (structure her zaman üretir).
 */
public class ForgottenChamberStructure extends Structure {

    /**
     * {@code simpleCodec} yalnızca {@link Structure.StructureSettings}'i okur (biyomlar,
     * step, spawn_overrides, terrain_adaptation). Ekstra özel alan yoktur.
     */
    public static final MapCodec<ForgottenChamberStructure> CODEC =
            simpleCodec(ForgottenChamberStructure::new);

    public ForgottenChamberStructure(Structure.StructureSettings settings) {
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
                0x466F726743L, arcanumCfg.structureSpawnChance * arcanumCfg.forgottenChamberSpawnChance)) {
            return Optional.empty();
        }

        ChunkGenerator generator = context.chunkGenerator();
        LevelHeightAccessor heightAccessor = context.heightAccessor();

        int cx = context.chunkPos().getMiddleBlockX();
        int cz = context.chunkPos().getMiddleBlockZ();

        // Yüzey yüksekliği (worldgen heightmap'i — henüz dekorasyon yokken güvenli).
        int surface = generator.getFirstFreeHeight(cx, cz,
                Heightmap.Types.WORLD_SURFACE_WG, heightAccessor, context.randomState());

        // Odayı yüzeyin ~34 blok altına — MADEN/MINESHAFT bandına — oturt; güvenli
        // banda kırp. Piece geometrisi origin'e göre dikeyde [origin.y-3 .. origin.y+15]
        // arasını kaplar (salon zemini origin.y-2, salon tavanı origin.y+14; bkz.
        // ForgottenChamberPiece FLOOR_DY/CEIL_DY). Taban minBuildHeight+2'nin üstünde,
        // salon tavanı yüzeyin altında kalsın diye üst/alt sınır buna göre seçilir.
        int minY = heightAccessor.getMinY();
        int lower = minY + 8;               // taban (origin.y-3) bedrock bandının üstünde kalsın
        int upper = surface - 18;           // salon tavanı (origin.y+14 + kabuk) yüzeyin altında kalsın
        if (upper < lower) {
            upper = lower;                  // çok sığ dünyalarda banı çökertme
        }
        // Yüzeyin ~38 blok altı -> mineshaft'larla aynı derin katman.
        int y = Mth.clamp(surface - 38, lower, upper);

        BlockPos pos = new BlockPos(cx, y, cz);
        return Optional.of(new Structure.GenerationStub(pos,
                builder -> builder.addPiece(new ForgottenChamberPiece(pos))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.FORGOTTEN_CHAMBER.get();
    }
}
