package com.arcanum.worldgen;

import com.arcanum.Arcanum;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Arcane Ruin — kadim büyücülerden kalma yıkık taş çemberi.
 * Origin çevresinde 4-5 yarıçaplı çember üzerinde 5-6 kırık deepslate sütunu,
 * merkezde oymalı deepslate kaidesi (üstünde bazen ametist kümesi), yanında
 * arcanum:chests/arcane_ruin loot'lu bir sandık. Zemin eğimi ve sıvı kontrolü
 * yapılır; okyanus/nehir ortasında kendini iptal eder.
 */
public class ArcaneRuinFeature extends Feature<NoneFeatureConfiguration> {

    /** Sandık loot tablosu (data/arcanum/loot_tables/chests/arcane_ruin.json — 1.20.1'de klasör adı ÇOĞUL). */
    private static final ResourceLocation ARCANE_RUIN_LOOT =
            new ResourceLocation(Arcanum.MODID, "chests/arcane_ruin");

    public ArcaneRuinFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin(); // heightmap modifier'ı sayesinde yüzeydeki ilk hava bloğu

        // ---- Kullanıcı config'i: yapı sıklığı kapısı (oyuncu isteği: "make all
        // structures ... configurable"). Seyreltme DETERMİNİSTİK: dünya seed'i +
        // chunk koordinatı hash'i (bkz. StructureGate); Math.random() KULLANILMAZ,
        // aynı seed + aynı ayar her zaman aynı dünyayı üretir. chance=0.0 → yapı yok.
        com.arcanum.config.ArcanumConfig arcanumCfg = com.arcanum.config.ArcanumConfig.get();
        if (!StructureGate.allow(level.getSeed(), origin.getX() >> 4, origin.getZ() >> 4,
                0x5275696E73L, arcanumCfg.structureSpawnChance * arcanumCfg.arcaneRuinSpawnChance)) {
            return false;
        }


        // Merkez zemin uygunluğu: sıvı üstüne veya yaprak/çalı tepesine kurulmaz
        BlockPos originGround = origin.below();
        if (!level.getFluidState(originGround).isEmpty()) {
            return false;
        }
        if (!level.getBlockState(originGround).isFaceSturdy(level, originGround, Direction.UP)) {
            return false;
        }

        int originY = origin.getY();
        boolean placedColumn = false;

        // 5-6 sütun, 4-5 yarıçaplı çember üzerinde hafif açı sapmasıyla
        int columnCount = 5 + random.nextInt(2);
        double startAngle = random.nextDouble() * Math.PI * 2.0;
        for (int i = 0; i < columnCount; i++) {
            double angle = startAngle + (Math.PI * 2.0 * i) / columnCount + (random.nextDouble() - 0.5) * 0.35;
            int radius = 4 + random.nextInt(2);
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * radius);

            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            if (Math.abs(surfaceY - originY) > 3) {
                continue; // zemin çok engebeli — bu sütunu atla
            }
            BlockPos base = new BlockPos(x, surfaceY, z);
            BlockPos ground = base.below();
            if (!level.getFluidState(ground).isEmpty()) {
                continue; // sıvı üstüne sütun kurma
            }
            if (!level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) {
                continue;
            }

            int height = 2 + random.nextInt(3); // 2-4 blok
            for (int dy = 0; dy < height; dy++) {
                setBlock(level, base.above(dy), columnBlock(random));
            }
            // Tepesine %40 slab, yoksa %30 sönmüş mum
            BlockPos top = base.above(height);
            if (random.nextFloat() < 0.4F) {
                setBlock(level, top, Blocks.DEEPSLATE_BRICK_SLAB.defaultBlockState());
            } else if (random.nextFloat() < 0.3F) {
                setBlock(level, top, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, Boolean.FALSE));
            }
            placedColumn = true;
        }

        if (!placedColumn) {
            return false; // hiç sütun dikilemedi — çemberi tamamen iptal et
        }

        // Merkez kaide + %60 ametist kümesi
        setBlock(level, origin, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
        if (random.nextFloat() < 0.6F) {
            setBlock(level, origin.above(), Blocks.AMETHYST_CLUSTER.defaultBlockState());
        }

        // Kaidenin yanına %40 şansla loot sandığı — her ruin hazine vermesin
        // (progresyon dengesi: kitaplar esas olarak yapı sandıklarından gelmeli)
        if (random.nextFloat() < 0.4F)
        for (Direction dir : Direction.Plane.HORIZONTAL.shuffledCopy(random)) {
            int cx = origin.getX() + dir.getStepX();
            int cz = origin.getZ() + dir.getStepZ();
            int cy = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx, cz);
            if (Math.abs(cy - originY) > 1) {
                continue;
            }
            BlockPos chestPos = new BlockPos(cx, cy, cz);
            BlockPos chestGround = chestPos.below();
            if (!level.getFluidState(chestGround).isEmpty()) {
                continue;
            }
            if (!level.getBlockState(chestGround).isFaceSturdy(level, chestGround, Direction.UP)) {
                continue;
            }
            if (!level.getBlockState(chestPos).isAir()) {
                continue;
            }
            setBlock(level, chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, dir));
            RandomizableContainerBlockEntity.setLootTable(level, random, chestPos, ARCANE_RUIN_LOOT);
            break;
        }

        // Çevreye 2-3 glow lichen (zemine yapışık, multiface DOWN yüzü)
        int lichenCount = 2 + random.nextInt(2);
        for (int i = 0; i < lichenCount; i++) {
            int lx = origin.getX() + random.nextInt(9) - 4;
            int lz = origin.getZ() + random.nextInt(9) - 4;
            int ly = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lx, lz);
            if (Math.abs(ly - originY) > 3) {
                continue;
            }
            BlockPos lichenPos = new BlockPos(lx, ly, lz);
            BlockPos lichenGround = lichenPos.below();
            if (!level.getBlockState(lichenPos).isAir()) {
                continue;
            }
            if (!level.getFluidState(lichenGround).isEmpty()) {
                continue;
            }
            if (!level.getBlockState(lichenGround).isFaceSturdy(level, lichenGround, Direction.UP)) {
                continue;
            }
            setBlock(level, lichenPos, Blocks.GLOW_LICHEN.defaultBlockState()
                    .setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), Boolean.TRUE));
        }

        return true;
    }

    /** Sütun gövdesi için yıpranmış deepslate karışımı seçer. */
    private static BlockState columnBlock(RandomSource random) {
        int roll = random.nextInt(10);
        if (roll < 4) {
            return Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        }
        if (roll < 7) {
            return Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
        }
        return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
    }
}
