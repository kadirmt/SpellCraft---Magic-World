package com.arcanum.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;

/**
 * Arcanewood fidanı — davranış vanilla {@link SaplingBlock} ile AYNI (TreeGrower → arcanewood_tree).
 *
 * <p>26.x: {@code SaplingBlock(TreeGrower, Properties)} kurucusu HAM vanilla'da protected; Fabric
 * (transitive access widener) ve Forge (AT) bugün public'e açıyor ama ortak kod ham vanilla sözleşmesine
 * göre yazılır → ince alt sınıf. Codec vanilla'nınkiyle aynı biçimde ("tree" + blok özellikleri).
 */
public class ArcanewoodSaplingBlock extends SaplingBlock {
    public static final MapCodec<ArcanewoodSaplingBlock> CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(TreeGrower.CODEC.fieldOf("tree").forGetter(b -> b.treeGrower), propertiesCodec())
                    .apply(i, ArcanewoodSaplingBlock::new));

    public ArcanewoodSaplingBlock(TreeGrower treeGrower, Properties properties) {
        super(treeGrower, properties);
    }

    @Override
    public MapCodec<ArcanewoodSaplingBlock> codec() {
        return CODEC;
    }
}
