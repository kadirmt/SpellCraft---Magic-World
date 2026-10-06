package com.arcanum.block;

import com.arcanum.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Arcanewood yaprağı — altından süzülen sihirli yaprak zerresi (LEAF_MOTE) ve
 * ara sıra büyü kıvılcımı (SPELL_SPARK) döker. Kiraz yaprağının partikül
 * davranışını taklit eder; vanilla yağmur damlası efekti korunur.
 */
public class ArcanewoodLeavesBlock extends LeavesBlock {

    public ArcanewoodLeavesBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random); // yağmur damlası efekti korunur

        // Altı kapalıysa partikül dökme (vanilla cherry_leaves davranışı)
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (Block.isFaceFull(belowState.getCollisionShape(level, below), Direction.UP)) {
            return;
        }
        // %2: süzülen yaprak zerresi
        if (random.nextFloat() < 0.02F) {
            ParticleUtils.spawnParticleBelow(level, pos, random, ModParticles.LEAF_MOTE.get());
        }
        // %0.5: büyü kıvılcımı
        if (random.nextFloat() < 0.005F) {
            ParticleUtils.spawnParticleBelow(level, pos, random, ModParticles.SPELL_SPARK.get());
        }
    }
}
