package com.arcanum.util;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Renkli vanilla blokları (yün, halı, boyalı pişmiş kil, mum) ve zincir için
 * TEK erişim noktası (26.2 ileri-uyum kuralı §1-8).
 *
 * <p>26.1.2'de {@code Blocks.BLACK_WOOL} gibi renk-başına alanlar duruyor; 26.2'de
 * bunlar {@code ColorCollection<Block>}'a taşındı ({@code Blocks.WOOL.pick(color)},
 * {@code Blocks.CARPET}, {@code Blocks.DYED_TERRACOTTA}, {@code Blocks.DYED_CANDLE}).
 * Yapı kodu ({@code HogsmeadePiece}, {@code DeathEaterCampFeature} vb.) renkli bloğu
 * YALNIZ bu sınıftan alır → 26.2 portunda yalnız bu dosya değişir.</p>
 *
 * <p>26.2 durumu (vanilla kaynağıyla doğrulandı): {@code ColorCollection<T>} bir record;
 * alanları {@code white..black} sırasıyla {@code ColorCollection.VALUES}'daki {@link DyeColor}
 * sırasıyla {@code zipMap} üzerinden eşlenir, {@code BlockItemIds.WOOL/CARPET/DYED_TERRACOTTA/
 * DYED_CANDLE} = {@code createSimpleColored("wool"|"carpet"|"terracotta"|"candle")} →
 * {@code prefixWithColor} ile {@code <DyeColor#getName()>_<taban>} kimlikleri. {@code pick(color)}
 * DyeColor → aynı adlı alan switch'i olduğundan her renk 26.1.2'deki {@code Blocks.<RENK>_X}
 * bloğuyla (aynı kayıt kimliği) birebir eşleşir.</p>
 *
 * <p>{@code Blocks.CHAIN} 26.x'te {@code Blocks.IRON_CHAIN} oldu (bakır zincir
 * varyantları eklendi); {@link #ironChain()} bunu sarar.</p>
 */
public final class VanillaBlocks {

    private VanillaBlocks() {
    }

    /** Renkli yün ({@code <renk>_wool}). */
    public static Block wool(DyeColor color) {
        return Blocks.WOOL.pick(color);
    }

    /** Renkli halı ({@code <renk>_carpet}). */
    public static Block carpet(DyeColor color) {
        return Blocks.CARPET.pick(color);
    }

    /** Boyalı pişmiş kil ({@code <renk>_terracotta}; düz {@code Blocks.TERRACOTTA} DEĞİL). */
    public static Block terracotta(DyeColor color) {
        return Blocks.DYED_TERRACOTTA.pick(color);
    }

    /** Renkli mum ({@code <renk>_candle}; düz {@code Blocks.CANDLE} DEĞİL). */
    public static Block candle(DyeColor color) {
        return Blocks.DYED_CANDLE.pick(color);
    }

    /** Demir zincir (1.21.1'deki {@code Blocks.CHAIN}). */
    public static Block ironChain() {
        return Blocks.IRON_CHAIN;
    }
}
