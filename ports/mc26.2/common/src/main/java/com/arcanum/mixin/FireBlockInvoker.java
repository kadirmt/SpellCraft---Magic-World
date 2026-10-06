package com.arcanum.mixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Ortak invoker: {@code private void FireBlock#setFlammable(Block, int igniteOdds, int burnOdds)} [javap 26.1.2].
 * Loader API'si (Fabric FlammableBlockRegistry / Forge) yerine TEK mekanizma (ARCHITECTURE §3).
 * Kullanım: {@code ((FireBlockInvoker) (FireBlock) Blocks.FIRE).arcanum$setFlammable(block, 5, 20);}
 */
@Mixin(FireBlock.class)
public interface FireBlockInvoker {
    @Invoker("setFlammable")
    void arcanum$setFlammable(Block block, int igniteOdds, int burnOdds);
}
