package com.arcanum.worldgen;

import com.arcanum.Arcanum;
import com.arcanum.entity.DeathEaterEntity;
import com.arcanum.registry.ModEntities;
import com.arcanum.util.VanillaBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Ölümyiyen Kampı — karanlık büyücülerin geçici konaklama alanı.
 * Merkezde yanan bir SOUL_CAMPFIRE, çevresinde 2 çadır (4 köşe dark oak çit
 * direği h2 + 3x3 siyah/gri yün çatı + içeride gri halı). Birinci çadırda
 * arcanum:chests/death_eater_camp loot'lu SANDIK (her kampta garanti —
 * çadır kurulamazsa ateşin yanına konur), ikinci çadırda aynı loot'lu BARREL.
 * Kenarda soul lantern direği ve 1-2 oturma taşı. Kamp çevresine 2-3
 * Ölümyiyen STRUCTURE spawn'u yapılır (persistence'lı — despawn olmazlar).
 * Zemin eğimi ve sıvı kontrolleri ArcaneRuinFeature disipliniyle yapılır;
 * merkez + en az 1 çadır kurulamazsa feature kendini iptal eder.
 */
public class DeathEaterCampFeature extends Feature<NoneFeatureConfiguration> {

    /** Sandık loot tablosu — garanti büyü kitabı burada (yalnızca BİR konteyner). */
    private static final ResourceKey<LootTable> CAMP_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(Arcanum.MODID, "chests/death_eater_camp"));

    /** Varil loot tablosu — kitap YOK, yalnızca sıradan malzeme (2. konteynerde de kitap çıkmasın diye). */
    private static final ResourceKey<LootTable> BARREL_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(Arcanum.MODID, "chests/death_eater_camp_barrel"));

    public DeathEaterCampFeature(Codec<NoneFeatureConfiguration> codec) {
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
                0x4445436D70L, arcanumCfg.structureSpawnChance * arcanumCfg.deathEaterCampSpawnChance)) {
            return false;
        }


        // Merkez zemin uygunluğu: sıvı üstüne veya gevşek zemine kamp kurulmaz
        BlockPos originGround = origin.below();
        if (!level.getFluidState(originGround).isEmpty()) {
            return false;
        }
        if (!level.getBlockState(originGround).isFaceSturdy(level, originGround, Direction.UP)) {
            return false;
        }

        int originY = origin.getY();

        // --- 2 çadır: karşılıklı yönlerde, hafif açı sapmasıyla, mesafe 4-5 ---
        // Önce çadırları dene; hiçbiri kurulamazsa hiçbir blok konmadan iptal et.
        double baseAngle = random.nextDouble() * Math.PI * 2.0;
        int tentsBuilt = 0;
        boolean chestPlaced = false;
        for (int t = 0; t < 2; t++) {
            double angle = baseAngle + t * Math.PI + (random.nextDouble() - 0.5) * 0.5;
            int dist = 4 + random.nextInt(2);
            int cx = origin.getX() + (int) Math.round(Math.cos(angle) * dist);
            int cz = origin.getZ() + (int) Math.round(Math.sin(angle) * dist);
            int cy = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx, cz);
            if (Math.abs(cy - originY) > 2) {
                continue; // zemin çok engebeli — bu çadırı atla
            }
            BlockPos tentCenter = new BlockPos(cx, cy, cz);
            BlockPos tentGround = tentCenter.below();
            if (!level.getFluidState(tentGround).isEmpty()) {
                continue;
            }
            if (!level.getBlockState(tentGround).isFaceSturdy(level, tentGround, Direction.UP)) {
                continue;
            }

            // İlk kurulan çadıra sandık, ikincisine varil
            boolean wantChest = !chestPlaced;
            buildTent(level, random, tentCenter, wantChest);
            if (wantChest) {
                chestPlaced = true;
            }
            tentsBuilt++;
        }

        if (tentsBuilt == 0) {
            return false; // merkez + en az 1 çadır şartı sağlanamadı
        }

        // --- Merkez: yanan soul campfire ---
        setBlock(level, origin, Blocks.SOUL_CAMPFIRE.defaultBlockState()
                .setValue(CampfireBlock.LIT, Boolean.TRUE));

        // --- Sandık garantisi: çadırda yer bulunamadıysa ateşin yanına koy ---
        if (!chestPlaced) {
            chestPlaced = placeChestNearCampfire(level, random, origin, originY);
        }
        if (!chestPlaced) {
            // Son çare: yön aramaları da başarısızsa doğuya zorla yerleştir —
            // sandık HER kampta kesin var olmalı (progresyon kaynağı).
            BlockPos forced = origin.east();
            setBlock(level, forced, Blocks.CHEST.defaultBlockState()
                    .setValue(ChestBlock.FACING, Direction.WEST));
            RandomizableContainer.setBlockEntityLootTable(level, random, forced, CAMP_LOOT);
        }

        // --- Soul lantern direği: çadır ekseninin dikeyinde, mesafe 3 ---
        double lanternAngle = baseAngle + Math.PI / 2.0 + (random.nextDouble() - 0.5) * 0.4;
        placeLanternPost(level, origin, originY,
                origin.getX() + (int) Math.round(Math.cos(lanternAngle) * 3.0),
                origin.getZ() + (int) Math.round(Math.sin(lanternAngle) * 3.0));

        // --- 1-2 oturma taşı: ateşin çevresine cobbled deepslate ---
        int seatCount = 1 + random.nextInt(2);
        for (int i = 0; i < seatCount; i++) {
            double seatAngle = baseAngle + Math.PI / 2.0 + Math.PI * i + (random.nextDouble() - 0.5) * 0.9;
            int sx = origin.getX() + (int) Math.round(Math.cos(seatAngle) * 2.0);
            int sz = origin.getZ() + (int) Math.round(Math.sin(seatAngle) * 2.0);
            int sy = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, sx, sz);
            if (Math.abs(sy - originY) > 2) {
                continue;
            }
            BlockPos seatPos = new BlockPos(sx, sy, sz);
            BlockPos seatGround = seatPos.below();
            if (!level.getFluidState(seatGround).isEmpty()) {
                continue;
            }
            if (!level.getBlockState(seatGround).isFaceSturdy(level, seatGround, Direction.UP)) {
                continue;
            }
            if (!level.getBlockState(seatPos).isAir()) {
                continue;
            }
            setBlock(level, seatPos, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        }

        // --- Ölümyiyen spawn: 2-3 adet, kamp çevresinde ---
        spawnDeathEaters(level, random, origin, originY);

        return true;
    }

    /**
     * 3x3 çadır kurar: 4 köşe dark oak çit direği (h2), üstte 3x3 siyah/gri
     * yün çatı, içeride gri halı + loot konteyneri (chest ya da barrel).
     */
    private void buildTent(WorldGenLevel level, RandomSource random, BlockPos center, boolean chest) {
        // 4 köşe direği: 2 blok yüksek dark oak çit
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                BlockPos post = center.offset(dx, 0, dz);
                setBlock(level, post, Blocks.DARK_OAK_FENCE.defaultBlockState());
                setBlock(level, post.above(), Blocks.DARK_OAK_FENCE.defaultBlockState());
            }
        }
        // 3x3 yün çatı: siyah ağırlıklı, gri serpiştirme (karanlık kamp paleti)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                setBlock(level, center.offset(dx, 2, dz), roofBlock(random));
            }
        }
        // Loot konteyneri çadırın ortasına — sandık ateşe doğru baksın diye
        // rastgele yatay yön yeterli (çadır içi, estetik fark önemsiz)
        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        BlockState container = chest
                ? Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing)
                : Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, facing);
        setBlock(level, center, container);
        RandomizableContainer.setBlockEntityLootTable(level, random, center, chest ? CAMP_LOOT : BARREL_LOOT);

        // Gri halı: konteynerin yanındaki iç hücrelerden birine (köşeler direk)
        Direction carpetDir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        BlockPos carpetPos = center.relative(carpetDir);
        if (level.getBlockState(carpetPos).isAir()
                && level.getBlockState(carpetPos.below()).isFaceSturdy(level, carpetPos.below(), Direction.UP)) {
            setBlock(level, carpetPos, VanillaBlocks.carpet(DyeColor.GRAY).defaultBlockState());
        }
    }

    /** Çatı için siyah/gri yün karışımı seçer (60/40). */
    private static BlockState roofBlock(RandomSource random) {
        return random.nextInt(10) < 6
                ? VanillaBlocks.wool(DyeColor.BLACK).defaultBlockState()
                : VanillaBlocks.wool(DyeColor.GRAY).defaultBlockState();
    }

    /** Campfire'ın yanında uygun bir hücreye loot sandığı koymayı dener. */
    private boolean placeChestNearCampfire(WorldGenLevel level, RandomSource random,
                                           BlockPos origin, int originY) {
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
            setBlock(level, chestPos, Blocks.CHEST.defaultBlockState()
                    .setValue(ChestBlock.FACING, dir));
            RandomizableContainer.setBlockEntityLootTable(level, random, chestPos, CAMP_LOOT);
            return true;
        }
        return false;
    }

    /** Kenara soul lantern direği: 2 blok çit + üstünde fener. */
    private void placeLanternPost(WorldGenLevel level, BlockPos origin, int originY, int x, int z) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        if (Math.abs(y - originY) > 2) {
            return; // zemin uygun değil — direksiz kamp da olur
        }
        BlockPos base = new BlockPos(x, y, z);
        BlockPos ground = base.below();
        if (!level.getFluidState(ground).isEmpty()) {
            return;
        }
        if (!level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) {
            return;
        }
        setBlock(level, base, Blocks.DARK_OAK_FENCE.defaultBlockState());
        setBlock(level, base.above(), Blocks.DARK_OAK_FENCE.defaultBlockState());
        setBlock(level, base.above(2), Blocks.SOUL_LANTERN.defaultBlockState());
    }

    /**
     * Kamp çevresine 2-3 Ölümyiyen bırakır. WorldGenLevel, ServerLevelAccessor
     * olduğu için finalizeSpawn'a doğrudan verilebilir; getLevel() ise
     * EntityType.create için gereken ServerLevel'ı döndürür.
     */
    private void spawnDeathEaters(WorldGenLevel level, RandomSource random,
                                  BlockPos origin, int originY) {
        ServerLevel serverLevel = level.getLevel();
        int count = 2 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            DeathEaterEntity deathEater = ModEntities.DEATH_EATER.get().create(serverLevel, EntitySpawnReason.STRUCTURE);
            if (deathEater == null) {
                continue;
            }
            // Ateşin 2-3 blok çevresinde rastgele nokta; engebeli hücreyi atla
            int sx = origin.getX() + random.nextInt(7) - 3;
            int sz = origin.getZ() + random.nextInt(7) - 3;
            int sy = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, sx, sz);
            if (Math.abs(sy - originY) > 2) {
                sx = origin.getX() + 1;
                sz = origin.getZ() + 1;
                sy = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, sx, sz);
            }
            deathEater.snapTo(sx + 0.5, sy, sz + 0.5, random.nextFloat() * 360.0F, 0.0F);
            deathEater.setPersistenceRequired(); // kamp bekçileri despawn olmasın
            deathEater.finalizeSpawn(level, level.getCurrentDifficultyAt(deathEater.blockPosition()),
                    EntitySpawnReason.STRUCTURE, null);
            level.addFreshEntity(deathEater);
        }
    }
}
